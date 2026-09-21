package com.personal.flowreader.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.ContextCompat
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.Sentence
import com.personal.flowreader.data.SentenceSplitter
import com.personal.flowreader.data.SettingsStore
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsEngines
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsUiState(
    val playing: Boolean = false,
    val following: Boolean = true,
    val engineKey: String = TtsEngines.EDGE,
    val voiceId: String = TtsPrefs.DEFAULT_EDGE_VOICE,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val prefetchCount: Int = TtsPrefs.DEFAULT_PREFETCH,
    val doubleTapPlay: Boolean = true,
    val autoScrollWithTts: Boolean = true,
    val keepAliveUnderlay: Boolean = false,
    val continuousPcmPlayback: Boolean = false,
    val sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    val engines: List<TtsEngineOption> = TtsEngines.BUILT_IN,
    val voices: List<TtsVoiceOption> = emptyList(),
    val sentence: Sentence? = null,
    val sentenceIndex: Int = 0,
    val snippet: String = "",
    val error: String? = null,
    /** Edge MP3 already on disk for these sentence indices. */
    val readySentenceIndices: Set<Int> = emptySet(),
    /** Edge synthesize currently in flight. */
    val generatingSentenceIndices: Set<Int> = emptySet(),
)

class TtsController(
    private val context: Context,
    private val settings: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val edge = EdgeTtsClient()
    private val cacheDir = File(context.cacheDir, "tts").apply { mkdirs() }
    private val keepAlive = AudioKeepAlive()
    private val pcmPlayer = PcmSentencePlayer()

    /** Volatile: written on Main, read by cache sweeps on IO. */
    @Volatile
    private var sentences: List<Sentence> = emptyList()
    /** TTS-only filter rules; applied at speak/synthesize time on sentence text. */
    @Volatile
    private var speechFilters: List<FilterRule> = emptyList()
    /** Sanitized book id; scopes cache file names so books never share clips. */
    @Volatile
    private var bookKey = ""
    @Volatile
    private var attachedBookId = ""
    @Volatile
    private var moreProvider: (suspend () -> Boolean)? = null
    @Volatile
    private var index = 0
    private var playJob: Job? = null
    private var previewJob: Job? = null
    private var player: MediaPlayer? = null
    private var previewPlayer: MediaPlayer? = null
    private val playbackMutex = Mutex()
    /** Serializes play / pause / restart so MediaSession echoes can't fork loops. */
    private val playGate = Mutex()
    /** Bumped on every stop/restart; playFile ignores stale generations. */
    private var playGeneration = 0
    /** In-flight Edge synthesize/prefetch jobs keyed by sentence index; touched from IO too. */
    private val edgeJobs = ConcurrentHashMap<Int, Job>()
    private var systemTts: TextToSpeech? = null
    private var systemReady = false
    private var boundEnginePackage: String? = null
    private var session: MediaSession? = null
    /** Suppress MediaSession onPlay while we push PLAYING ourselves. */
    private var suppressSessionPlay = false
    @Volatile
    private var bookTitle: String = ""
    @Volatile
    private var chapterTitles: List<String> = emptyList()
    @Volatile
    private var coverArt: Bitmap? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var holdsAudioFocus = false
    private var noisyRegistered = false
    /** Activity-registered asker for POST_NOTIFICATIONS (API 33+). */
    @Volatile
    private var notificationPermissionAsker: ((onDone: () -> Unit) -> Unit)? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                if (_state.value.playing) pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit
            AudioManager.AUDIOFOCUS_GAIN -> Unit
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && _state.value.playing) {
                pause()
            }
        }
    }

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state

    private val _bookFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when playback reaches the last sentence and stops (Que auto-advance listens). */
    val bookFinished: SharedFlow<Unit> = _bookFinished

    fun setNotificationPermissionAsker(asker: ((onDone: () -> Unit) -> Unit)?) {
        notificationPermissionAsker = asker
    }

    fun sessionToken(): MediaSession.Token? = session?.sessionToken

    fun mediaTitle(): String = bookTitle.ifBlank { "Flow Reader" }

    fun mediaSubtitle(): String {
        val s = sentences.getOrNull(index) ?: return _state.value.snippet
        val chapter = chapterTitles.getOrNull(s.chapterIndex).orEmpty()
        return chapter.ifBlank { s.text.take(120) }
    }

    fun mediaCover(): Bitmap? = coverArt

    init {
        scope.launch {
            val prefs = settings.ttsOnce()
            val voices = voicesFor(prefs.engineKey)
            val voiceId = when {
                voices.any { it.id == prefs.voiceId } -> prefs.voiceId
                prefs.engineKey == TtsEngines.EDGE -> TtsPrefs.DEFAULT_EDGE_VOICE
                else -> voices.firstOrNull()?.id.orEmpty()
            }
            _state.update {
                it.copy(
                    engineKey = prefs.engineKey,
                    voiceId = voiceId,
                    speed = prefs.speed,
                    pitch = prefs.pitch,
                    prefetchCount = prefs.prefetchCount,
                    doubleTapPlay = prefs.doubleTapPlay,
                    autoScrollWithTts = prefs.autoScrollWithTts,
                    keepAliveUnderlay = prefs.keepAliveUnderlay,
                    continuousPcmPlayback = prefs.continuousPcmPlayback,
                    sentenceGapMs = prefs.sentenceGapMs,
                    voices = voices,
                )
            }
            refreshCatalog()
        }
    }

    fun attach(
        bookId: String,
        book: BookDoc,
        start: Locus,
        speechFilters: List<FilterRule> = emptyList(),
    ) {
        // Sync teardown so a new book never shares a live player/loop (QuickNovel stop-before-play).
        playGeneration++
        playJob?.cancel()
        playJob = null
        cancelPreview()
        stopSessionAudio()
        systemTts?.stop()
        cancelEdgeJobs()
        abandonAudioFocus()
        unregisterNoisyReceiver()
        TtsPlaybackService.stop()
        // Always drop the previous book's streaming provider — setMoreProvider(null)
        // is a no-op once attachedBookId has already moved on.
        moreProvider = null
        attachedBookId = bookId
        bookKey = sanitizeBookKey(bookId)
        bookTitle = book.title
        chapterTitles = book.chapters.map { it.title }
        coverArt?.recycle()
        coverArt = null
        this.speechFilters = speechFilters
        sentences = emptyList()
        index = 0
        _state.update {
            it.copy(
                playing = false,
                following = it.autoScrollWithTts,
                sentence = null,
                sentenceIndex = 0,
                snippet = "",
                error = null,
                readySentenceIndices = emptySet(),
                generatingSentenceIndices = emptySet(),
            )
        }
        ensureSession()
        updateSessionMetadata()
        setSessionState(PlaybackState.STATE_PAUSED)
        scope.launch {
            val split = withContext(Dispatchers.Default) {
                SentenceSplitter.split(book)
            }
            if (attachedBookId != bookId) return@launch
            sentences = split
            index = SentenceSplitter.indexAt(sentences, start)
            val s = sentences.getOrNull(index)
            _state.update {
                it.copy(
                    sentence = s,
                    sentenceIndex = index,
                    snippet = s?.text.orEmpty(),
                )
            }
            updateSessionMetadata()
            val center = index
            // Disk work for the rail (drop other books, trim to window, rescan) stays off main.
            val ready = withContext(Dispatchers.IO) {
                dropForeignBookCache()
                deleteCacheOutsideWindow(cacheWindow(center))
                scanReadySentenceIndices()
            }
            if (attachedBookId != bookId) return@launch
            _state.update { it.copy(readySentenceIndices = ready) }
            refreshCatalog()
            val cover = withContext(Dispatchers.IO) { loadCoverArt(bookId) }
            if (bookKey == sanitizeBookKey(bookId)) {
                coverArt?.recycle()
                coverArt = cover
                updateSessionMetadata()
                TtsPlaybackService.refresh()
            } else {
                cover?.recycle()
            }
        }
    }

    /**
     * Append chapters to the current book without resetting the playhead.
     * Sentence indices continue after the existing list so Edge prefetch stays valid.
     */
    fun extend(addedChapters: List<Chapter>) {
        if (addedChapters.isEmpty()) return
        val offset = chapterTitles.size
        chapterTitles = chapterTitles + addedChapters.map { it.title }
        val extra = SentenceSplitter.split(
            BookDoc(bookTitle, addedChapters),
        ).map { s -> s.copy(chapterIndex = s.chapterIndex + offset) }
        if (extra.isEmpty()) return
        sentences = sentences + extra
        updateSessionMetadata()
        if (_state.value.playing) {
            val n = _state.value.prefetchCount.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
            for (ahead in 1..n) {
                startEdgeJob(index + ahead) { prefetch(index + ahead) }
            }
        }
        TtsPlaybackService.refresh()
    }

    /**
     * Optional next-chapter loader for plugin streaming. Cleared when [bookId] no longer matches.
     */
    fun setMoreProvider(bookId: String, provider: (suspend () -> Boolean)?) {
        if (provider == null) {
            // Clear whenever asked — even if attach() already moved on to another book.
            moreProvider = null
        } else if (attachedBookId == bookId) {
            moreProvider = provider
        }
    }

    private suspend fun pullMore(): Boolean {
        val provider = moreProvider ?: return false
        return runCatching { provider() }.getOrDefault(false)
    }

    /** Drop Edge clips so refiltered text is not spoken from stale audio. */
    fun invalidateEdgeCache() {
        scope.launch { clearEdgeCache() }
    }

    /** One-shot speak for filter preview using the selected engine and voice. */
    fun speakPreview(text: String) {
        val snippet = text.trim()
        if (snippet.isEmpty()) return
        cancelPreview()
        previewJob = scope.launch {
            try {
                if (_state.value.playing) {
                    pausePlayback(PlaybackState.STATE_PAUSED)
                }
                when (_state.value.engineKey) {
                    TtsEngines.EDGE -> previewWithEdge(snippet)
                    else -> speakSystem(snippet)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Preview is best-effort.
            }
        }
    }

    private suspend fun previewWithEdge(text: String) {
        val audio = withContext(Dispatchers.IO) {
            edge.synthesize(
                text = text,
                voice = edgeVoiceId(),
                ratePercent = ratePercent(),
                pitchPercent = pitchPercent(),
            )
        }
        val file = File(cacheDir, "filter_preview.mp3")
        withContext(Dispatchers.IO) { writeAtomically(file, audio.mp3) }
        playPreviewFile(file)
    }

    private suspend fun playPreviewFile(file: File) = suspendCancellableCoroutine { cont ->
        releasePreviewPlayer()
        val mp = MediaPlayer()
        previewPlayer = mp
        mp.setOnCompletionListener {
            if (previewPlayer === mp) previewPlayer = null
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.runCatching { release() }
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setOnErrorListener { _, _, _ ->
            if (previewPlayer === mp) previewPlayer = null
            mp.setOnCompletionListener(null)
            mp.setOnErrorListener(null)
            mp.runCatching { release() }
            if (cont.isActive) {
                cont.resumeWithException(IllegalStateException("Preview playback failed"))
            }
            true
        }
        try {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
        } catch (t: Throwable) {
            releasePreviewPlayer()
            if (cont.isActive) cont.resumeWithException(t)
            return@suspendCancellableCoroutine
        }
        cont.invokeOnCancellation { releasePreviewPlayer() }
    }

    private fun cancelPreview() {
        previewJob?.cancel()
        previewJob = null
        releasePreviewPlayer()
        // Stop leftover system preview utterances without tearing down the engine.
        if (_state.value.engineKey != TtsEngines.EDGE) {
            systemTts?.stop()
        }
    }

    private fun releasePreviewPlayer() {
        val mp = previewPlayer ?: return
        previewPlayer = null
        mp.setOnCompletionListener(null)
        mp.setOnErrorListener(null)
        try {
            if (mp.isPlaying) mp.stop()
        } catch (_: IllegalStateException) {
        }
        try {
            mp.reset()
        } catch (_: IllegalStateException) {
        }
        try {
            mp.release()
        } catch (_: IllegalStateException) {
        }
    }

    fun play(follow: Boolean? = null) {
        if (sentences.isEmpty()) return
        scope.launch { startPlayback(follow) }
    }

    fun pause() {
        scope.launch { pausePlayback(PlaybackState.STATE_PAUSED) }
    }

    /** Tear down playback entirely (notification Stop, book switch). */
    fun stop() {
        scope.launch { pausePlayback(PlaybackState.STATE_STOPPED) }
    }

    fun skipNext() {
        scope.launch {
            playGate.withLock {
                if (index >= sentences.lastIndex) return@withLock
                index++
                advancePlayheadLocked()
            }
        }
    }

    fun skipPrev() {
        scope.launch {
            playGate.withLock {
                if (index <= 0) return@withLock
                index--
                advancePlayheadLocked()
            }
        }
    }

    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 2.5f)
        _state.update { it.copy(speed = value) }
        scope.launch {
            settings.setSpeed(value)
            // Rate is baked into each cached MP3, so old clips no longer match.
            clearEdgeCache()
            if (_state.value.playing) restartLoop()
        }
    }

    fun setPitch(pitch: Float) {
        val value = pitch.coerceIn(0.5f, 2f)
        _state.update { it.copy(pitch = value) }
        scope.launch {
            settings.setPitch(value)
            clearEdgeCache()
            if (_state.value.playing) restartLoop()
        }
    }

    fun setPrefetchCount(count: Int) {
        val value = count.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        if (value == _state.value.prefetchCount) return
        _state.update { it.copy(prefetchCount = value) }
        scope.launch { settings.setPrefetchCount(value) }
        pruneCacheToWindow(index)
        if (_state.value.playing) restartLoop()
    }

    fun setDoubleTapPlay(enabled: Boolean) {
        if (enabled == _state.value.doubleTapPlay) return
        _state.update { it.copy(doubleTapPlay = enabled) }
        scope.launch { settings.setDoubleTapPlay(enabled) }
    }

    fun setAutoScrollWithTts(enabled: Boolean) {
        if (enabled == _state.value.autoScrollWithTts) return
        _state.update {
            it.copy(
                autoScrollWithTts = enabled,
                // Turning the feature on resumes follow; turning it off clears it.
                following = if (enabled) true else false,
            )
        }
        scope.launch { settings.setAutoScrollWithTts(enabled) }
    }

    fun setKeepAliveUnderlay(enabled: Boolean) {
        if (enabled == _state.value.keepAliveUnderlay) return
        _state.update { it.copy(keepAliveUnderlay = enabled) }
        scope.launch { settings.setKeepAliveUnderlay(enabled) }
        syncKeepAlive()
    }

    fun setContinuousPcmPlayback(enabled: Boolean) {
        if (enabled == _state.value.continuousPcmPlayback) return
        _state.update { it.copy(continuousPcmPlayback = enabled) }
        scope.launch { settings.setContinuousPcmPlayback(enabled) }
        if (_state.value.playing) restartLoop()
    }

    fun setSentenceGapMs(ms: Int) {
        val value = TtsPrefs.coerceSentenceGapMs(ms)
        if (value == _state.value.sentenceGapMs) return
        _state.update { it.copy(sentenceGapMs = value) }
        scope.launch { settings.setSentenceGapMs(value) }
    }

    /**
     * Long-press on a cached rail bar: pause, delete that clip, show generating pulse,
     * and fetch a fresh Edge MP3 for the sentence.
     */
    fun forceRegenerateSentence(i: Int) {
        if (_state.value.engineKey != TtsEngines.EDGE) return
        if (sentences.getOrNull(i) == null) return
        scope.launch {
            pausePlayback(PlaybackState.STATE_PAUSED)
            edgeJobs[i]?.cancel()
            cacheFile(i).delete()
            _state.update {
                it.copy(
                    readySentenceIndices = it.readySentenceIndices - i,
                    generatingSentenceIndices = it.generatingSentenceIndices + i,
                )
            }
            startEdgeJob(i) { synthesizeToCache(i, force = true) }
        }
    }

    fun setEngine(key: String) {
        if (key == _state.value.engineKey) return
        val voices = voicesFor(key)
        val preferred = _state.value.voiceId
        val voiceId = when {
            voices.any { it.id == preferred } -> preferred
            key == TtsEngines.EDGE -> TtsPrefs.DEFAULT_EDGE_VOICE
            else -> voices.firstOrNull()?.id.orEmpty()
        }
        _state.update {
            it.copy(engineKey = key, voiceId = voiceId, voices = voices, error = null)
        }
        scope.launch {
            settings.setEngine(key)
            settings.setVoice(voiceId)
            clearEdgeCache()
            // Force re-init when switching system engines.
            if (key != TtsEngines.EDGE) {
                systemTts?.shutdown()
                systemTts = null
                systemReady = false
                boundEnginePackage = null
            }
            if (_state.value.playing) restartLoop()
        }
    }

    fun setVoice(voiceId: String) {
        if (voiceId == _state.value.voiceId) return
        _state.update { it.copy(voiceId = voiceId) }
        scope.launch {
            settings.setVoice(voiceId)
            clearEdgeCache()
            if (_state.value.playing) restartLoop()
        }
    }

    fun userScrolledAway() {
        if (!_state.value.autoScrollWithTts) return
        if (_state.value.playing && _state.value.following) {
            _state.update { it.copy(following = false) }
        }
    }

    fun followAgain() {
        if (!_state.value.autoScrollWithTts) return
        _state.update { it.copy(following = true) }
    }

    fun jumpTo(locus: Locus) {
        index = SentenceSplitter.indexAt(sentences, locus)
        _state.update {
            it.copy(following = it.autoScrollWithTts)
        }
        moveToCurrentSentence()
    }

    /**
     * Retire the old playhead before the new one is announced: [playGeneration] must move
     * synchronously or the in-flight loop can advance once more before [restartLoop] lands.
     */
    private fun moveToCurrentSentence() {
        val playing = _state.value.playing
        if (playing) playGeneration++
        publishSentence()
        if (playing) restartLoop()
    }

    /** Caller must hold [playGate]. */
    private suspend fun advancePlayheadLocked() {
        val playing = _state.value.playing
        if (playing) playGeneration++
        publishSentence()
        if (!playing) return
        val generation = playGeneration
        val previous = playJob
        playJob = null
        previous?.cancelAndJoin()
        interruptClipPlayback()
        playJob = scope.launch {
            if (_state.value.playing) loop(generation)
        }
    }

    /** App-scoped session; call only if the process itself is being torn down. */
    fun release() {
        playGeneration++
        playJob?.cancel()
        playJob = null
        stopSessionAudio()
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
        abandonAudioFocus()
        unregisterNoisyReceiver()
        TtsPlaybackService.stop()
        _state.update { it.copy(playing = false) }
        session?.release()
        session = null
    }

    /**
     * Readest-style: await previous speak teardown, then start one loop under [playGate].
     * QuickNovel-style: bump generation + stop player before any new MediaPlayer.
     */
    private suspend fun startPlayback(follow: Boolean? = null) = playGate.withLock {
        if (_state.value.playing && playJob?.isActive == true) return@withLock
        ensureNotificationPermission()
        ensureSession()
        if (!requestAudioFocus()) {
            _state.update { it.copy(error = "Audio focus unavailable") }
            return@withLock
        }
        registerNoisyReceiver()
        playGeneration++
        val generation = playGeneration
        val previous = playJob
        playJob = null
        previous?.cancelAndJoin()
        interruptClipPlayback()
        systemTts?.stop()
        _state.update {
            it.copy(
                playing = true,
                following = follow ?: it.autoScrollWithTts,
                error = null,
            )
        }
        syncKeepAlive()
        updateSessionMetadata()
        TtsPlaybackService.start(context)
        playJob = scope.launch {
            loop(generation)
        }
        suppressSessionPlay = true
        try {
            setSessionState(PlaybackState.STATE_PLAYING)
            TtsPlaybackService.refresh()
        } finally {
            suppressSessionPlay = false
        }
    }

    private suspend fun pausePlayback(sessionState: Int) = playGate.withLock {
        playGeneration++
        val previous = playJob
        playJob = null
        previous?.cancelAndJoin()
        stopSessionAudio()
        systemTts?.stop()
        _state.update { it.copy(playing = false) }
        setSessionState(sessionState)
        when (sessionState) {
            PlaybackState.STATE_STOPPED -> {
                abandonAudioFocus()
                unregisterNoisyReceiver()
                TtsPlaybackService.stop()
            }
            else -> {
                // Keep FGS so the shade card stays for resume (Readest-style).
                TtsPlaybackService.refresh()
            }
        }
    }

    private suspend fun refreshCatalog() {
        val engines = TtsEngines.BUILT_IN.toMutableList()
        runCatching {
            val probe = ensureSystemForPackage(null)
            probe.engines
                ?.mapNotNull { info ->
                    val pkg = info.name ?: return@mapNotNull null
                    if (pkg.isBlank()) return@mapNotNull null
                    TtsEngineOption(pkg, info.label?.ifBlank { pkg } ?: pkg)
                }
                ?.distinctBy { it.key }
                ?.forEach { engines += it }
        }
        val key = _state.value.engineKey
        val voices = voicesFor(key)
        val voiceId = when {
            voices.any { it.id == _state.value.voiceId } -> _state.value.voiceId
            key == TtsEngines.EDGE -> TtsPrefs.DEFAULT_EDGE_VOICE
            else -> voices.firstOrNull()?.id.orEmpty()
        }
        _state.update {
            it.copy(engines = engines, voices = voices, voiceId = voiceId)
        }
    }

    private fun voicesFor(engineKey: String): List<TtsVoiceOption> {
        return when (engineKey) {
            TtsEngines.EDGE -> EdgeVoices
            else -> {
                val tts = systemTts
                if (tts != null && systemReady) systemVoices(tts)
                else listOf(TtsVoiceOption("default", "Default"))
            }
        }
    }

    private fun systemVoices(tts: TextToSpeech): List<TtsVoiceOption> {
        val voices = tts.voices?.toList().orEmpty()
        if (voices.isEmpty()) {
            return listOf(TtsVoiceOption("default", "Default"))
        }
        val locale = Locale.getDefault()
        return voices
            .filter { it.locale.language == locale.language || it.locale == Locale.US }
            .ifEmpty { voices }
            .sortedWith(compareBy<Voice> { it.locale.toLanguageTag() }.thenBy { it.name })
            .map { v ->
                val label = buildString {
                    append(v.name.substringAfterLast(".").ifBlank { v.name })
                    append(" (")
                    append(v.locale.toLanguageTag())
                    append(")")
                }
                TtsVoiceOption(v.name, label)
            }
            .distinctBy { it.id }
    }

    private fun ensureSession() {
        if (session != null) return
        session = MediaSession(context, "flow-tts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    // Readest media bridge: only start when paused — never re-enter while playing.
                    if (suppressSessionPlay) return
                    if (_state.value.playing) return
                    play()
                }
                override fun onPause() {
                    if (_state.value.playing) pause()
                }
                override fun onSkipToNext() {
                    skipNext()
                }
                override fun onSkipToPrevious() {
                    skipPrev()
                }
                override fun onStop() {
                    scope.launch { pausePlayback(PlaybackState.STATE_STOPPED) }
                }
            })
            isActive = true
        }
        updateSessionMetadata()
    }

    private fun setSessionState(state: Int) {
        val s = session ?: return
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or
                        PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or
                        PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP,
                )
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, _state.value.speed)
                .build(),
        )
    }

    private fun updateSessionMetadata() {
        val s = session ?: return
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, mediaTitle())
            .putString(MediaMetadata.METADATA_KEY_ARTIST, mediaSubtitle())
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, mediaTitle())
            .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, mediaSubtitle())
        coverArt?.let { art ->
            builder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, art)
            builder.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, art)
        }
        s.setMetadata(builder.build())
    }

    private suspend fun loadCoverArt(bookId: String): Bitmap? {
        val app = context.applicationContext as? FlowApp ?: return null
        val row = app.db.progress().get(bookId) ?: return null
        val file = File(row.storedPath)
        return EpubCover.loadBitmap(file)
    }

    private fun restartLoop() {
        if (!_state.value.playing) return
        scope.launch {
            playGate.withLock {
                if (!_state.value.playing) return@withLock
                playGeneration++
                val generation = playGeneration
                val previous = playJob
                playJob = null
                previous?.cancelAndJoin()
                interruptClipPlayback()
                syncKeepAlive()
                playJob = scope.launch {
                    if (_state.value.playing) loop(generation)
                }
            }
        }
    }

    private fun publishSentence() {
        val s = sentences.getOrNull(index)
        _state.update {
            it.copy(sentence = s, sentenceIndex = index, snippet = s?.text.orEmpty())
        }
        updateSessionMetadata()
        TtsPlaybackService.refresh()
        pruneCacheToWindow(index)
    }

    private fun markGenerating(i: Int) {
        _state.update {
            it.copy(generatingSentenceIndices = it.generatingSentenceIndices + i)
        }
    }

    private fun unmarkGenerating(i: Int) {
        _state.update {
            it.copy(generatingSentenceIndices = it.generatingSentenceIndices - i)
        }
    }

    private fun markReady(i: Int) {
        _state.update {
            it.copy(
                readySentenceIndices = it.readySentenceIndices + i,
                generatingSentenceIndices = it.generatingSentenceIndices - i,
            )
        }
    }

    private suspend fun publishGenerating(i: Int) {
        withContext(Dispatchers.Main.immediate) { markGenerating(i) }
        // Let Compose paint a generating frame before the network call.
        yield()
    }

    private suspend fun publishReady(i: Int) {
        withContext(Dispatchers.Main.immediate) { markReady(i) }
    }

    private suspend fun publishUnmarkGenerating(i: Int) {
        withContext(Dispatchers.Main.immediate) { unmarkGenerating(i) }
    }

    private fun clearAudioStatus() {
        _state.update {
            it.copy(
                readySentenceIndices = emptySet(),
                generatingSentenceIndices = emptySet(),
            )
        }
    }

    private suspend fun loop(generation: Int) {
        while (scope.isActive && _state.value.playing && generation == playGeneration) {
            val s = sentences.getOrNull(index) ?: break
            publishSentence()
            val n = _state.value.prefetchCount.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
            if (sentences.lastIndex - index <= n) {
                pullMore()
            }
            for (offset in 1..n) {
                val target = index + offset
                startEdgeJob(target) { prefetch(target) }
            }
            try {
                speak(s, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                stopSessionAudio()
                _state.update { it.copy(playing = false, error = t.message ?: "TTS failed") }
                setSessionState(PlaybackState.STATE_STOPPED)
                abandonAudioFocus()
                unregisterNoisyReceiver()
                TtsPlaybackService.stop()
                return
            }
            if (generation != playGeneration) return
            val gapMs = _state.value.sentenceGapMs
            if (gapMs > 0) {
                if (_state.value.continuousPcmPlayback &&
                    _state.value.engineKey == TtsEngines.EDGE
                ) {
                    pcmPlayer.writeSilence(gapMs) { generation == playGeneration }
                } else {
                    delay(gapMs.toLong())
                }
            }
            if (generation != playGeneration) return
            if (index >= sentences.lastIndex) {
                if (pullMore() && index < sentences.lastIndex) {
                    index++
                    continue
                }
                stopSessionAudio()
                _state.update { it.copy(playing = false) }
                setSessionState(PlaybackState.STATE_STOPPED)
                abandonAudioFocus()
                unregisterNoisyReceiver()
                TtsPlaybackService.stop()
                _bookFinished.tryEmit(Unit)
                return
            }
            index++
        }
    }

    private suspend fun speak(s: Sentence, generation: Int) {
        when (_state.value.engineKey) {
            TtsEngines.EDGE -> speakEdge(index, generation)
            else -> speakSystem(speechText(s.text))
        }
    }

    /** Visible sentence text plus any TTS-only filter transforms. */
    private fun speechText(text: String): String = TextFilters.applySpeech(text, speechFilters)

    private fun startEdgeJob(i: Int, block: suspend () -> Unit) {
        edgeJobs.remove(i)?.cancel()
        lateinit var job: Job
        // Lazy start so the job is registered before its own finally can deregister it.
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                edgeJobs.remove(i, job)
            }
        }
        edgeJobs[i] = job
        job.start()
    }

    private fun cancelEdgeJobs() {
        edgeJobs.values.forEach { it.cancel() }
        edgeJobs.clear()
    }

    private suspend fun prefetch(i: Int) {
        if (_state.value.engineKey != TtsEngines.EDGE) return
        if (!inCacheWindow(i)) return
        try {
            synthesizeToCache(i, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Lookahead is best-effort.
        }
    }

    private suspend fun speakEdge(i: Int, generation: Int) {
        synthesizeToCache(i, force = false)
        if (generation != playGeneration) return
        val f = cacheFile(i)
        if (!f.exists() || f.length() == 0L) {
            throw IllegalStateException("Unable to play audio")
        }
        if (_state.value.continuousPcmPlayback) {
            pcmPlayer.play(f) { generation == playGeneration }
        } else {
            playFile(f, generation)
        }
    }

    /**
     * Ensure sentence [i] has an Edge MP3 on disk. When [force] is true, always re-synthesize.
     * @return true if a usable file is ready afterward.
     */
    private suspend fun synthesizeToCache(i: Int, force: Boolean): Boolean {
        if (_state.value.engineKey != TtsEngines.EDGE) return false
        val s = sentences.getOrNull(i) ?: return false
        val f = cacheFile(i)
        if (!force && f.exists() && f.length() > 0L) {
            publishReady(i)
            return true
        }
        if (force) f.delete()
        publishGenerating(i)
        return try {
            val audio = edge.synthesize(
                text = speechText(s.text),
                voice = edgeVoiceId(),
                ratePercent = ratePercent(),
                pitchPercent = pitchPercent(),
            )
            writeAtomically(f, audio.mp3)
            publishReady(i)
            true
        } catch (e: CancellationException) {
            publishUnmarkGenerating(i)
            throw e
        } catch (t: Throwable) {
            publishUnmarkGenerating(i)
            if (force) false else throw t
        }
    }

    private suspend fun playFile(file: File, generation: Int) = playbackMutex.withLock {
        if (generation != playGeneration) return@withLock
        // QuickNovel EdgeTtsPlayer: always tear down previous player before start.
        releasePlayer()
        if (generation != playGeneration) return@withLock
        val mp = MediaPlayer()
        try {
            mp.setDataSource(file.absolutePath)
            withContext(Dispatchers.IO) { mp.prepare() }
        } catch (t: Throwable) {
            mp.runCatching { release() }
            throw t
        }
        if (generation != playGeneration) {
            mp.runCatching { release() }
            return@withLock
        }
        suspendCancellableCoroutine { cont ->
            player = mp
            mp.setOnCompletionListener {
                if (player === mp) player = null
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                mp.runCatching { release() }
                if (cont.isActive) cont.resume(Unit)
            }
            mp.setOnErrorListener { _, _, _ ->
                if (player === mp) player = null
                mp.setOnCompletionListener(null)
                mp.setOnErrorListener(null)
                mp.runCatching { release() }
                if (cont.isActive) cont.resumeWithException(IllegalStateException("Unable to play audio"))
                true
            }
            cont.invokeOnCancellation {
                releasePlayer()
            }
            try {
                // Edge bakes the rate into the MP3 — resampling here would double it.
                mp.start()
            } catch (t: Throwable) {
                releasePlayer()
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }

    /** QuickNovel-style: clear listeners, stop, reset, release — never leave a live player. */
    private fun releasePlayer() {
        val mp = player ?: return
        player = null
        mp.setOnCompletionListener(null)
        mp.setOnErrorListener(null)
        try {
            if (mp.isPlaying) mp.stop()
        } catch (_: IllegalStateException) {
        }
        try {
            mp.reset()
        } catch (_: IllegalStateException) {
        }
        try {
            mp.release()
        } catch (_: IllegalStateException) {
        }
    }

    private fun interruptClipPlayback() {
        releasePlayer()
        pcmPlayer.cancel()
    }

    private fun stopSessionAudio() {
        releasePlayer()
        pcmPlayer.release()
        keepAlive.stop()
    }

    private fun syncKeepAlive() {
        if (_state.value.playing && _state.value.keepAliveUnderlay) {
            keepAlive.start()
        } else {
            keepAlive.stop()
        }
    }

    private suspend fun speakSystem(text: String) {
        val enginePkg = when (val key = _state.value.engineKey) {
            TtsEngines.SYSTEM_DEFAULT -> null
            else -> key
        }
        val tts = ensureSystemForPackage(enginePkg)
        tts.setSpeechRate(_state.value.speed)
        tts.setPitch(_state.value.pitch)
        val voiceId = _state.value.voiceId
        if (voiceId.isNotBlank() && voiceId != "default") {
            tts.voices?.firstOrNull { it.name == voiceId }?.let { tts.voice = it }
        }
        // Refresh voice list once ready.
        val voices = systemVoices(tts)
        if (voices != _state.value.voices) {
            _state.update { it.copy(voices = voices) }
        }
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) cont.resume(Unit)
                }
                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) {
                        cont.resumeWithException(IllegalStateException("System TTS error"))
                    }
                }
            })
            val code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (code != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("System TTS unavailable"))
            }
            cont.invokeOnCancellation { tts.stop() }
        }
    }

    private suspend fun ensureSystemForPackage(enginePackage: String?): TextToSpeech {
        val bound = systemTts
        if (bound != null && systemReady && boundEnginePackage == enginePackage) {
            return bound
        }
        systemTts?.shutdown()
        systemTts = null
        systemReady = false
        return suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                val engine = created
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    if (cont.isActive) {
                        cont.resumeWithException(IllegalStateException("System TTS init failed"))
                    }
                    return@OnInitListener
                }
                engine.language = Locale.getDefault()
                systemTts = engine
                systemReady = true
                boundEnginePackage = enginePackage
                if (_state.value.engineKey != TtsEngines.EDGE) {
                    val voices = systemVoices(engine)
                    val voiceId = when {
                        voices.any { it.id == _state.value.voiceId } -> _state.value.voiceId
                        else -> voices.firstOrNull()?.id.orEmpty()
                    }
                    _state.update { it.copy(voices = voices, voiceId = voiceId) }
                }
                if (cont.isActive) cont.resume(engine)
            }
            created = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
        }
    }

    private suspend fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (settings.notificationsAskedOnce()) return
        settings.setNotificationsAskedOnce(true)
        val asker = notificationPermissionAsker ?: return
        suspendCancellableCoroutine { cont ->
            asker {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (holdsAudioFocus) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(audioFocusListener)
                .setWillPauseWhenDucked(true)
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        holdsAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return holdsAudioFocus
    }

    private fun abandonAudioFocus() {
        if (!holdsAudioFocus && audioFocusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusListener)
        }
        audioFocusRequest = null
        holdsAudioFocus = false
    }

    private fun registerNoisyReceiver() {
        if (noisyRegistered) return
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        noisyRegistered = true
    }

    private fun unregisterNoisyReceiver() {
        if (!noisyRegistered) return
        runCatching { context.unregisterReceiver(noisyReceiver) }
        noisyRegistered = false
    }

    private fun edgeVoiceId(): String {
        val id = _state.value.voiceId
        return if (id.isNotBlank() && EdgeVoices.any { it.id == id }) id else EdgeVoices.first().id
    }

    private fun cacheFile(i: Int): File = File(cacheDir, "b${bookKey}_s${i}_${voiceCacheKey()}.mp3")

    private fun voiceCacheKey(): String =
        "${_state.value.engineKey}_${edgeVoiceId()}_${ratePercent()}_${pitchPercent()}"
            .replace(VOICE_KEY_UNSAFE, "_")

    private fun sanitizeBookKey(bookId: String): String = bookId.replace(BOOK_KEY_UNSAFE, "-")

    /** Sentence index of a cache file belonging to the attached book, or null for anything else. */
    private fun cachedSentenceIndex(name: String): Int? {
        val match = SENTENCE_CACHE_NAME.matchEntire(name) ?: return null
        if (match.groupValues[1] != bookKey) return null
        return match.groupValues[2].toIntOrNull()
    }

    /** Write via temp + rename so a failed synthesize never leaves a truncated MP3 behind. */
    private fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(cacheDir, "${target.name}.part")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IllegalStateException("Unable to cache audio")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /** Rebuild ready set from on-disk Edge MP3s for the current engine/voice/rate/pitch. */
    private fun scanReadySentenceIndices(): Set<Int> {
        if (_state.value.engineKey != TtsEngines.EDGE || sentences.isEmpty()) return emptySet()
        return sentences.indices.filterTo(linkedSetOf()) { i ->
            val f = cacheFile(i)
            f.exists() && f.length() > 0L
        }
    }

    private fun cacheWindow(center: Int = index): IntRange {
        val n = _state.value.prefetchCount.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        if (sentences.isEmpty()) return IntRange.EMPTY
        val lo = (center - n).coerceAtLeast(0)
        val hi = (center + n).coerceAtMost(sentences.lastIndex)
        return lo..hi
    }

    private fun inCacheWindow(i: Int, center: Int = index): Boolean = i in cacheWindow(center)

    /**
     * Keep at most prefetchCount behind + current + prefetchCount ahead on disk and in UI state.
     * Full-book audio retention is intentionally not supported yet.
     */
    private fun pruneCacheToWindow(center: Int = index) {
        if (_state.value.engineKey != TtsEngines.EDGE || sentences.isEmpty()) return
        val window = cacheWindow(center)
        // Rail state first so bars retire on this frame; the disk sweep follows off main.
        _state.update {
            it.copy(
                readySentenceIndices = it.readySentenceIndices.filterTo(linkedSetOf()) { i -> i in window },
                generatingSentenceIndices = it.generatingSentenceIndices.filterTo(linkedSetOf()) { i ->
                    i in window
                },
            )
        }
        // Re-read the window on the IO thread: by the time this runs the playhead may have
        // moved on, and a stale window would delete the clip we just prefetched.
        scope.launch(Dispatchers.IO) { deleteCacheOutsideWindow(cacheWindow()) }
    }

    /** Disk only: drop this book's clips outside [window] and cancel the jobs that fed them. */
    private fun deleteCacheOutsideWindow(window: IntRange) {
        cacheDir.listFiles()?.forEach { file ->
            val i = cachedSentenceIndex(file.name) ?: return@forEach
            if (i !in window) {
                edgeJobs.remove(i)?.cancel()
                file.delete()
            }
        }
    }

    /** Disk only: clips from other books (and any `.part` leftovers) can never serve this rail. */
    private fun dropForeignBookCache() {
        cacheDir.listFiles()?.forEach { file ->
            if (cachedSentenceIndex(file.name) == null) file.delete()
        }
    }

    /**
     * Voice / rate / pitch are baked into every clip, so a change invalidates the whole cache.
     * Deletes on [Dispatchers.IO]; callers must await before starting new synthesis.
     */
    private suspend fun clearEdgeCache() {
        cancelEdgeJobs()
        clearAudioStatus()
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        clearAudioStatus()
    }

    private fun ratePercent(): Int = ((_state.value.speed - 1f) * 100f).toInt()

    private fun pitchPercent(): Int = ((_state.value.pitch - 1f) * 50f).toInt()

    companion object {
        /** `b<bookKey>_s<sentenceIndex>_<voiceKey>.mp3` */
        private val SENTENCE_CACHE_NAME = Regex("""^b([A-Za-z0-9.-]*)_s(\d+)_.*\.mp3$""")
        private val BOOK_KEY_UNSAFE = Regex("[^A-Za-z0-9.-]")
        private val VOICE_KEY_UNSAFE = Regex("[^A-Za-z0-9._-]")

        val EdgeVoices = listOf(
            TtsVoiceOption("en-US-AndrewNeural", "Andrew"),
            TtsVoiceOption("en-US-AriaNeural", "Aria"),
            TtsVoiceOption("en-US-JennyNeural", "Jenny"),
            TtsVoiceOption("en-US-GuyNeural", "Guy"),
            TtsVoiceOption("en-US-MichelleNeural", "Michelle"),
            TtsVoiceOption("en-GB-SoniaNeural", "Sonia (UK)"),
            TtsVoiceOption("en-GB-RyanNeural", "Ryan (UK)"),
            TtsVoiceOption("en-AU-NatashaNeural", "Natasha (AU)"),
        )
    }
}
