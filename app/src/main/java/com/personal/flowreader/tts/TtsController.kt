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
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.Sentence
import com.personal.flowreader.data.SentenceTable
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
import kotlinx.coroutines.channels.BufferOverflow
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

/** A sentence playback reached, with the session it was read from. */
class SpokenPosition(val session: ReadingSession, val sentence: Sentence)

data class TtsUiState(
    val playing: Boolean = false,
    /**
     * True from the first play until playback is stopped (Library card X, notification Stop,
     * end of book). Pausing keeps the session, so the Library now-playing card stays.
     */
    val sessionActive: Boolean = false,
    /** Book the controller is attached to (Library now-playing card opens it). */
    val bookId: String = "",
    val bookTitle: String = "",
    val following: Boolean = true,
    val engineKey: String = TtsEngines.EDGE,
    val voiceId: String = TtsPrefs.DEFAULT_EDGE_VOICE,
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val prefetchCount: Int = TtsPrefs.DEFAULT_PREFETCH,
    val doubleTapPlay: Boolean = true,
    /** Edge may send over mobile data while Wi-Fi is weak or failing. */
    val mobileDataFallback: Boolean = true,
    val autoPlayOnShare: Boolean = false,
    val shareInterruptsPlayback: Boolean = false,
    val autoScrollWithTts: Boolean = true,
    val minSignal: Float = TtsPrefs.DEFAULT_MIN_SIGNAL,
    /** Paired BT MAC for underlay standby; empty = always on when armed. */
    val underlayBtAddress: String = "",
    val underlayBtName: String = "",
    /** True when [underlayBtAddress] is currently connected (audio/ACL). */
    val underlayBtConnected: Boolean = false,
    val sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    /** Added to heard media time when resolving Edge word cues (ms). */
    val highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    /** Ideal characters per TTS clip. */
    val clipTargetChars: Int = TtsPrefs.DEFAULT_CLIP_TARGET_CHARS,
    /** Characters allowed above/below [clipTargetChars]. */
    val clipFlexChars: Int = TtsPrefs.DEFAULT_CLIP_FLEX_CHARS,
    /** When true, synth ring-log is active and the floating dump FAB is shown. */
    val debugEnabled: Boolean = false,
    val engines: List<TtsEngineOption> = TtsEngines.BUILT_IN,
    val voices: List<TtsVoiceOption> = emptyList(),
    val sentence: Sentence? = null,
    val sentenceIndex: Int = 0,
    val snippet: String = "",
    val error: String? = null,
    /**
     * Absolute char range in the current sentence's block for word-level Edge highlight.
     * Drawn as a full-accent layer on top of the desaturated sentence highlight.
     */
    val wordHighlight: IntRange? = null,
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
    private val edgeNet = EdgeNetwork(context, edge.http)
    private val edgeVoiceStore = EdgeVoiceStore(File(context.filesDir, "edge_voices.json"), edge.http)
    /** Full Edge voice list; empty until loaded, then [EdgeVoices] stands in. */
    @Volatile
    private var edgeVoices: List<EdgeVoice> = emptyList()
    @Volatile
    private var edgeVoiceOptions: List<TtsVoiceOption> = EdgeVoices
    /** Consecutive playhead network failures; drives the retry backoff. */
    private var networkFailures = 0
    private val cacheDir = File(context.cacheDir, "tts").apply { mkdirs() }
    private val keepAlive = AudioKeepAlive()
    private val underlayBt = UnderlayBluetoothMonitor(context)
    private val audioEngine = TtsAudioEngine()
    /** Edge word boundaries keyed by sentence index for the attached book/voice. */
    private val wordBoundariesBySentence = ConcurrentHashMap<Int, List<EdgeWordBoundary>>()
    /** Cached TimedCues per sentence (built from Edge boundaries). */
    private val cuesBySentence = ConcurrentHashMap<Int, List<WordHighlight.TimedCue>>()
    /** Polls AudioTrack playback head for word highlight. */
    private var wordTrackJob: Job? = null
    /** Hearable timeline marks: playback head ≥ startWriteFrame → this sentence/seed. */
    private val wordSegments = ArrayList<WordSegment>()
    private val wordSegmentsLock = Any()

    private data class WordSegment(
        val sentenceIndex: Int,
        val startWriteFrame: Long,
        val seedSec: Double,
    )

    /** Open book shared with the reader; grows chapter by chapter as playback advances. */
    @Volatile
    private var reading: ReadingSession? = null
    private var pullJob: Job? = null
    /** Stable indices: the window growing or trimming never renumbers the playhead. */
    private val sentences: SentenceTable
        get() = reading?.window?.value?.table ?: SentenceTable.EMPTY
    /** TTS-only filter rules; applied at speak/synthesize time on sentence text. */
    @Volatile
    private var speechFilters: List<FilterRule> = emptyList()
    /** Part of every clip name so audio made with other TTS-only rules is never replayed. */
    @Volatile
    private var speechFilterKey = ""
    private var speechGlobal: List<FilterRule> = emptyList()
    private var speechGroups: List<FilterRule> = emptyList()
    private var speechLocal: List<FilterRule> = emptyList()
    /** Lookahead skips these until the playhead reaches them (no instant retry loop). */
    private val prefetchFailed: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    /** Book id + sentence numbering origin; scopes cache file names so clips never mismatch. */
    @Volatile
    private var bookKey = ""
    @Volatile
    private var attachedBookId = ""
    private var focusedChapter: Int? = null
    @Volatile
    private var index = 0
    private var playJob: Job? = null
    private var previewJob: Job? = null
    private var previewPlayer: MediaPlayer? = null
    /** Serializes play / pause / restart so MediaSession echoes can't fork loops. */
    private val playGate = Mutex()
    /** Bumped on every stop/restart; speak loop ignores stale generations. */
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
        // Prefer the heard sentence (UI index), not the write-ahead playhead.
        val heard = _state.value.sentenceIndex
        val s = sentences.getOrNull(heard) ?: sentences.getOrNull(index) ?: return _state.value.snippet
        val chapter = reading?.window?.value?.chapterTitle(s.chapterIndex).orEmpty()
        return chapter.ifBlank { s.text.take(120) }
    }

    fun mediaCover(): Bitmap? = coverArt

    init {
        scope.launch {
            val prefs = settings.ttsOnce()
            val reader = settings.readerOnce()
            setEdgeVoices(withContext(Dispatchers.IO) { edgeVoiceStore.load() })
            val voices = voicesFor(prefs.engineKey)
            val voiceId = when {
                voices.any { it.id == prefs.voiceId } -> prefs.voiceId
                prefs.engineKey == TtsEngines.EDGE -> TtsPrefs.DEFAULT_EDGE_VOICE
                else -> voices.firstOrNull()?.id.orEmpty()
            }
            SynthDebugLog.setEnabled(reader.debugEnabled)
            underlayBt.setTargetAddress(prefs.underlayBtAddress)
            underlayBt.start()
            edgeNet.start()
            withContext(Dispatchers.IO) { edgeNet.setMobileDataAllowed(prefs.mobileDataFallback) }
            _state.update {
                it.copy(
                    engineKey = prefs.engineKey,
                    voiceId = voiceId,
                    speed = prefs.speed,
                    pitch = prefs.pitch,
                    prefetchCount = prefs.prefetchCount,
                    doubleTapPlay = prefs.doubleTapPlay,
                    mobileDataFallback = prefs.mobileDataFallback,
                    autoPlayOnShare = prefs.autoPlayOnShare,
                    shareInterruptsPlayback = prefs.shareInterruptsPlayback,
                    autoScrollWithTts = prefs.autoScrollWithTts,
                    minSignal = prefs.minSignal,
                    underlayBtAddress = prefs.underlayBtAddress,
                    underlayBtName = prefs.underlayBtName,
                    underlayBtConnected = underlayBt.targetConnected.value,
                    sentenceGapMs = prefs.sentenceGapMs,
                    highlightSyncMs = prefs.highlightSyncMs,
                    clipTargetChars = prefs.clipTargetChars,
                    clipFlexChars = prefs.clipFlexChars,
                    debugEnabled = reader.debugEnabled,
                    voices = voices,
                )
            }
            refreshCatalog()
            withContext(Dispatchers.IO) { edgeVoiceStore.refreshIfStale() }?.let { fresh ->
                setEdgeVoices(fresh)
                if (_state.value.engineKey == TtsEngines.EDGE) {
                    _state.update { it.copy(voices = edgeVoiceOptions) }
                }
            }
        }
        scope.launch {
            underlayBt.targetConnected.collect { connected ->
                _state.update { it.copy(underlayBtConnected = connected) }
                syncKeepAlive()
            }
        }
    }

    /** Session playback is attached to; a reader reopening the same content reuses it. */
    fun attachedSession(): ReadingSession? = reading

    private val _spoken = MutableSharedFlow<SpokenPosition>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Each sentence playback moves onto, paired with the session whose table it came from. */
    val spoken: SharedFlow<SpokenPosition> = _spoken

    private fun emitSpoken(s: Sentence?) {
        val session = reading ?: return
        if (s == null || !_state.value.playing) return
        _spoken.tryEmit(SpokenPosition(session, s))
    }

    /**
     * Filter rules from the reader (all scopes) or Library settings (Global/Groups, keeping the
     * attached book's Local rules). Only TTS-only rules matter here; a change re-keys the clip
     * cache so the next clips are synthesized from the new text.
     */
    fun setSpeechFilters(
        global: List<FilterRule>,
        groups: List<FilterRule>,
        local: List<FilterRule> = speechLocal,
    ) {
        speechGlobal = global
        speechGroups = groups
        speechLocal = local
        val next = TextFilters.merge(global, groups, local)
            .filter { it.ttsOnly && it.enabled && it.pattern.isNotEmpty() }
        if (next == speechFilters) return
        speechFilters = next
        speechFilterKey = speechFilterKeyOf(next)
        SynthDebugLog.append("speechFilters n=${next.size} key=$speechFilterKey")
        if (attachedBookId.isEmpty()) return
        cancelEdgeJobs()
        prefetchFailed.clear()
        wordBoundariesBySentence.clear()
        cuesBySentence.clear()
        val bookId = attachedBookId
        scope.launch {
            val ready = withContext(Dispatchers.IO) { scanReadySentenceIndices() }
            if (attachedBookId != bookId) return@launch
            _state.update { it.copy(readySentenceIndices = ready, generatingSentenceIndices = emptySet()) }
            scheduleAheadPrefetch()
        }
    }

    private fun speechFilterKeyOf(rules: List<FilterRule>): String {
        if (rules.isEmpty()) return ""
        val signature = rules.joinToString("\u0001") {
            "${it.matchType.name}\u0002${it.wholeWords}\u0002${it.pattern}\u0002${it.replacement}"
        }
        return Integer.toHexString(signature.hashCode())
    }

    fun attach(
        session: ReadingSession,
        start: Locus,
    ) {
        // Reopening the reader on the book that is already playing must not interrupt it.
        if (session === reading) {
            _state.update { it.copy(following = it.autoScrollWithTts) }
            return
        }
        val bookId = session.bookId
        val sameBook = bookId == attachedBookId
        val resume = sameBook && _state.value.playing
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
        reading?.let { previous -> scope.launch { previous.setFocus(ReadingSession.FOCUS_TTS, null) } }
        pullJob?.cancel()
        pullJob = null
        reading = session
        focusedChapter = null
        attachedBookId = bookId
        bookKey = cacheKeyFor(session)
        val title = session.window.value.title
        bookTitle = title
        coverArt?.recycle()
        coverArt = null
        prefetchFailed.clear()
        index = sentences.indexAt(start)
        wordBoundariesBySentence.clear()
        cuesBySentence.clear()
        val first = sentences.getOrNull(index)
        // One update: bookId and sentence must never be observed from different books.
        _state.update {
            it.copy(
                bookId = bookId,
                bookTitle = title,
                playing = false,
                sessionActive = sameBook && it.sessionActive,
                following = it.autoScrollWithTts,
                sentence = first,
                sentenceIndex = index,
                snippet = first?.text.orEmpty(),
                error = null,
                wordHighlight = null,
                readySentenceIndices = emptySet(),
                generatingSentenceIndices = emptySet(),
            )
        }
        focusChapter(first)
        ensureSession()
        updateSessionMetadata()
        setSessionState(PlaybackState.STATE_PAUSED)
        scope.launch {
            if (resume) startPlayback()
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
            if (attachedBookId == bookId) {
                coverArt?.recycle()
                coverArt = cover
                updateSessionMetadata()
                TtsPlaybackService.refresh()
            } else {
                cover?.recycle()
            }
        }
    }

    private fun cacheKeyFor(session: ReadingSession): String =
        sanitizeBookKey(session.bookId) + ".o" + session.window.value.origin

    /**
     * Load chapters after the window until one adds sentences, so playback continues past empty
     * chapters (also with the reader closed). False at the end of the book or on a load error.
     */
    private suspend fun pullMore(): Boolean {
        val s = reading ?: return false
        val before = sentences.lastIndex
        repeat(MAX_EMPTY_PULL) {
            val st = _state.value
            val loaded = try {
                s.loadNext(st.clipTargetChars, st.clipFlexChars)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SynthDebugLog.append("pullMore failed: ${t.message ?: t.javaClass.simpleName}")
                false
            }
            if (!loaded || reading !== s) return false
            if (sentences.lastIndex > before) {
                updateSessionMetadata()
                return true
            }
        }
        return false
    }

    /** Background [pullMore]; one at a time so the loop keeps speaking while a chapter loads. */
    private fun pullMoreAsync() {
        if (pullJob?.isActive == true) return
        pullJob = scope.launch { pullMore() }
    }

    /**
     * Keep the window around the playhead: chapters far behind it are dropped, and the chapters
     * either side are loaded now so crossing into them never waits on a load.
     */
    private fun focusChapter(sentence: Sentence?) {
        val chapter = sentence?.chapterIndex ?: return
        if (chapter == focusedChapter) return
        focusedChapter = chapter
        val s = reading ?: return
        scope.launch {
            s.setFocus(ReadingSession.FOCUS_TTS, chapter)
            val st = _state.value
            runCatching { s.loadAdjacent(chapter + 1, st.clipTargetChars, st.clipFlexChars) }
            runCatching { s.loadAdjacent(chapter - 1, st.clipTargetChars, st.clipFlexChars) }
        }
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
                    else -> speakSystem(snippet, sentenceIndex = null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Preview is best-effort.
            }
        }
    }

    private suspend fun previewWithEdge(text: String) {
        val audio = withContext(Dispatchers.IO) { synthesizeEdge(text, tag = "preview") }
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

    /** Tear down playback entirely and end the session (notification Stop, Library card X). */
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
                if (index <= sentences.firstIndex) return@withLock
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

    fun setAutoPlayOnShare(enabled: Boolean) {
        if (enabled == _state.value.autoPlayOnShare) return
        _state.update { it.copy(autoPlayOnShare = enabled) }
        scope.launch { settings.setAutoPlayOnShare(enabled) }
    }

    fun setShareInterruptsPlayback(enabled: Boolean) {
        if (enabled == _state.value.shareInterruptsPlayback) return
        _state.update { it.copy(shareInterruptsPlayback = enabled) }
        scope.launch { settings.setShareInterruptsPlayback(enabled) }
    }

    fun setMobileDataFallback(enabled: Boolean) {
        if (enabled == _state.value.mobileDataFallback) return
        _state.update { it.copy(mobileDataFallback = enabled) }
        scope.launch {
            withContext(Dispatchers.IO) { edgeNet.setMobileDataAllowed(enabled) }
            settings.setMobileDataFallback(enabled)
        }
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

    fun setMinSignal(level: Float, persist: Boolean = true) {
        val value = TtsPrefs.coerceMinSignal(level)
        _state.update { it.copy(minSignal = value) }
        syncKeepAlive()
        if (persist) scope.launch { settings.setMinSignal(value) }
    }

    fun setUnderlayBtDevice(address: String, name: String) {
        val addr = address.trim()
        val label = name.trim()
        if (addr == _state.value.underlayBtAddress && label == _state.value.underlayBtName) {
            return
        }
        underlayBt.setTargetAddress(addr)
        _state.update {
            it.copy(
                underlayBtAddress = addr,
                underlayBtName = label,
                underlayBtConnected = if (addr.isEmpty()) false else underlayBt.targetConnected.value,
            )
        }
        syncKeepAlive()
        scope.launch { settings.setUnderlayBtDevice(addr, label) }
    }

    /** Paired devices for the underlay Bluetooth picker (needs BLUETOOTH_CONNECT on API 31+). */
    fun underlayBondedDevices(): List<PairedBtDevice> = underlayBt.bondedDevices()

    fun refreshUnderlayBtConnection() {
        underlayBt.refresh()
    }

    fun setSentenceGapMs(ms: Int) {
        val value = TtsPrefs.coerceSentenceGapMs(ms)
        if (value == _state.value.sentenceGapMs) return
        _state.update { it.copy(sentenceGapMs = value) }
        scope.launch { settings.setSentenceGapMs(value) }
        if (_state.value.playing) restartLoop()
    }

    fun setHighlightSyncMs(ms: Int) {
        val value = TtsPrefs.coerceHighlightSyncMs(ms)
        if (value == _state.value.highlightSyncMs) return
        _state.update { it.copy(highlightSyncMs = value) }
        scope.launch { settings.setHighlightSyncMs(value) }
    }

    fun setClipTargetChars(chars: Int) {
        val value = TtsPrefs.coerceClipTargetChars(chars)
        if (value == _state.value.clipTargetChars) return
        _state.update { it.copy(clipTargetChars = value) }
        scope.launch {
            settings.setClipTargetChars(value)
            resplitAttachedForClipBand()
        }
    }

    fun setClipFlexChars(chars: Int) {
        val value = TtsPrefs.coerceClipFlexChars(chars)
        if (value == _state.value.clipFlexChars) return
        _state.update { it.copy(clipFlexChars = value) }
        scope.launch {
            settings.setClipFlexChars(value)
            resplitAttachedForClipBand()
        }
    }

    /** Re-chunk sentences after clip-size prefs change; clears Edge cache. */
    private suspend fun resplitAttachedForClipBand() {
        val s0 = reading ?: return
        val bookId = attachedBookId
        val locus = sentences.getOrNull(index)?.let {
            Locus(it.chapterIndex, it.blockIndex, it.start)
        } ?: Locus(0, 0, 0)
        val target = _state.value.clipTargetChars
        val flex = _state.value.clipFlexChars
        withContext(Dispatchers.Default) { s0.resplit(target, flex) }
        if (attachedBookId != bookId || reading !== s0) return
        bookKey = cacheKeyFor(s0)
        index = sentences.indexAt(locus)
        val s = sentences.getOrNull(index)
        _state.update {
            it.copy(
                sentence = s,
                sentenceIndex = index,
                snippet = s?.text.orEmpty(),
                wordHighlight = null,
                readySentenceIndices = emptySet(),
                generatingSentenceIndices = emptySet(),
            )
        }
        clearEdgeCache()
        if (_state.value.playing) restartLoop()
        else {
            pruneCacheToWindow(index)
            updateSessionMetadata()
            TtsPlaybackService.refresh()
        }
    }

    fun setDebugEnabled(enabled: Boolean) {
        if (enabled == _state.value.debugEnabled) return
        SynthDebugLog.setEnabled(enabled)
        _state.update { it.copy(debugEnabled = enabled) }
        SynthDebugLog.append("debugEnabled=$enabled")
    }

    /**
     * Surfaces a playback error: force-enables the synth debugger (persisted)
     * and logs the message there (no longer shown on the media card).
     */
    private fun reportMediaError(message: String) {
        val text = message.ifBlank { "TTS failed" }
        val enable = !_state.value.debugEnabled
        if (enable) {
            scope.launch { settings.setDebugEnabled(true) }
        }
        _state.update { it.copy(debugEnabled = true, error = null) }
        SynthDebugLog.appendError(text)
    }

    /** Playback continues past [i]; the log opens with the text Edge refused. */
    private fun reportSkippedSentence(i: Int, text: String, reason: String) {
        val spoken = speechText(text)
        val detail = if (spoken == text) "" else " spoken=\"$spoken\""
        reportMediaError("skipped i=$i: $reason text=\"$text\"$detail")
        SynthDebugLog.requestOpen()
    }

    /**
     * Snapshot the synth ring buffer + live engine counters to a file under
     * app external files (`synth-logs/`).
     */
    fun dumpSynthLog(): File {
        val dir = File(context.getExternalFilesDir(null), "synth-logs").apply { mkdirs() }
        val out = File(dir, "synth-${System.currentTimeMillis()}.txt")
        val s = _state.value
        val header = buildString {
            appendLine("Flow Reader synth dump")
            appendLine("time=${System.currentTimeMillis()}")
            appendLine("engine=${s.engineKey} voice=${s.voiceId}")
            appendLine("playing=${s.playing} speed=${s.speed} pitch=${s.pitch}")
            appendLine("gapMs=${s.sentenceGapMs} syncMs=${s.highlightSyncMs} prefetch=${s.prefetchCount}")
            appendLine("writeIndex=$index heardIndex=${s.sentenceIndex}")
            appendLine("writtenFrames=${audioEngine.writtenFrames()}")
            appendLine("playbackHead=${audioEngine.playbackHeadFrames()}")
            appendLine("sampleRate=${audioEngine.sampleRateHz()}")
            appendLine("pendingSeedSec=${audioEngine.peekPendingMediaSeedSec()}")
            appendLine("pendingRemainder=${audioEngine.hasPendingRemainder()}")
            appendLine("underruns=${audioEngine.underrunCount()}")
            appendLine("ready=${s.readySentenceIndices.sorted()}")
            appendLine("generating=${s.generatingSentenceIndices.sorted()}")
            appendLine("--- events ---")
        }
        out.writeText(header + SynthDebugLog.snapshot().joinToString("\n") + "\n")
        (context.applicationContext as? FlowApp)?.positionLog?.files?.forEach { log ->
            out.appendText("--- ${log.name} ---\n")
            runCatching { out.appendText(log.readText()) }
        }
        SynthDebugLog.append("dump->${out.absolutePath}")
        return out
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
            wordsCacheFile(i).delete()
            wordBoundariesBySentence.remove(i)
            cuesBySentence.remove(i)
            _state.update {
                it.copy(
                    readySentenceIndices = it.readySentenceIndices - i,
                    generatingSentenceIndices = it.generatingSentenceIndices + i,
                )
            }
            startEdgeJob(i) {
                try {
                    synthesizeToCache(i, force = true, source = "regen")
                } finally {
                    scope.launch(Dispatchers.Main.immediate) { pumpPrefetch() }
                }
            }
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
        index = sentences.indexAt(locus)
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
        underlayBt.stop()
        edgeNet.stop()
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
     * Bump generation and interrupt clip playback before starting a new loop.
     */
    private suspend fun startPlayback(follow: Boolean? = null) = playGate.withLock {
        if (_state.value.playing && playJob?.isActive == true) return@withLock
        ensureNotificationPermission()
        ensureSession()
        if (!requestAudioFocus()) {
            reportMediaError("Audio focus unavailable")
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
                sessionActive = true,
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
        _state.update {
            it.copy(
                playing = false,
                sessionActive = it.sessionActive && sessionState != PlaybackState.STATE_STOPPED,
            )
        }
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

    private fun setEdgeVoices(voices: List<EdgeVoice>) {
        if (voices.isEmpty()) return
        edgeVoices = voices
        edgeVoiceOptions = EdgeVoiceCatalog.options(voices)
    }

    private fun voicesFor(engineKey: String): List<TtsVoiceOption> {
        return when (engineKey) {
            TtsEngines.EDGE -> edgeVoiceOptions
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
        val storedPath = app.catalog.libraryBook(bookId)?.storedPath
            ?: app.pluginCatalog.get(bookId)?.storedPath
            ?: return null
        return EpubCover.loadBitmap(File(storedPath))
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
        emitSpoken(s)
        focusChapter(s)
        updateSessionMetadata()
        TtsPlaybackService.refresh()
        pruneCacheToWindow(index)
    }

    /**
     * Drive sentence wash / pin / locus from the same heard-clock segments as word highlight.
     * Write-ahead [index] may already be further along in the AudioTrack buffer.
     */
    private fun publishHeardSentence(heardIndex: Int) {
        val s = sentences.getOrNull(heardIndex) ?: return
        if (_state.value.sentenceIndex == heardIndex && _state.value.sentence == s) return
        _state.update {
            it.copy(sentence = s, sentenceIndex = heardIndex, snippet = s.text)
        }
        emitSpoken(s)
        focusChapter(s)
        updateSessionMetadata()
        TtsPlaybackService.refresh()
    }

    /** Block until playback head catches write head (end-of-book drain). */
    private suspend fun awaitHeardCatchUp(generation: Int) {
        val rate = audioEngine.sampleRateHz()
        if (rate <= 0) return
        val slackFrames = (rate / 20).coerceAtLeast(1) // ~50ms
        while (generation == playGeneration && _state.value.playing) {
            val written = audioEngine.writtenFrames()
            val head = audioEngine.playbackHeadFrames()
            if (written - head <= slackFrames) break
            delay(16)
        }
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
            // Edge UI follows heard clock; System has no PCM buffer lag — publish write index.
            if (_state.value.engineKey == TtsEngines.EDGE) {
                pruneCacheToWindow(index)
            } else {
                publishSentence()
            }
            val n = effectivePrefetchCount()
            if (sentences.lastIndex - index <= n) {
                pullMoreAsync()
            }
            scheduleAheadPrefetch()
            try {
                if (SpeechText.isSpeakable(speechText(s.text))) {
                    speak(s, generation)
                } else {
                    SynthDebugLog.append("skip i=$index nothing to say text=\"${s.text}\"")
                }
                networkFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: EdgeContentException) {
                reportSkippedSentence(index, s.text, e.message ?: "Edge rejected text")
            } catch (e: EdgeNetworkException) {
                awaitNetworkRetry(e)
                if (generation != playGeneration) return
                continue
            } catch (t: Throwable) {
                stopSessionAudio()
                _state.update { it.copy(playing = false, sessionActive = false) }
                reportMediaError(t.message ?: "TTS failed")
                setSessionState(PlaybackState.STATE_STOPPED)
                abandonAudioFocus()
                unregisterNoisyReceiver()
                TtsPlaybackService.stop()
                return
            }
            if (generation != playGeneration) return
            val gapMs = _state.value.sentenceGapMs
            if (gapMs > 0) {
                if (_state.value.engineKey == TtsEngines.EDGE) {
                    audioEngine.writeSilence(gapMs) { generation == playGeneration }
                } else {
                    delay(gapMs.toLong())
                }
            }
            // Negative gap (crossfade) is applied inside speakEdge / TtsAudioEngine.
            if (generation != playGeneration) return
            if (index >= sentences.lastIndex) {
                pullJob?.join()
                if (index >= sentences.lastIndex) pullMore()
                if (index < sentences.lastIndex) {
                    index++
                    continue
                }
                if (_state.value.engineKey == TtsEngines.EDGE) {
                    awaitHeardCatchUp(generation)
                }
                if (generation != playGeneration) return
                stopSessionAudio()
                _state.update { it.copy(playing = false, sessionActive = false) }
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

    /**
     * Playhead clip failed on the network: wait (no limit) until a network can carry Edge traffic,
     * back off, and let lookahead retry clips that failed meanwhile. Session and media card stay up.
     */
    private suspend fun awaitNetworkRetry(e: EdgeNetworkException) {
        networkFailures++
        val backoffMs = (NETWORK_RETRY_BASE_MS shl (networkFailures - 1).coerceAtMost(3))
            .coerceAtMost(NETWORK_RETRY_MAX_MS)
        SynthDebugLog.appendError(
            "network i=$index failures=$networkFailures backoffMs=$backoffMs: ${e.message}",
        )
        withContext(Dispatchers.IO) {
            if (edgeNet.isOffline()) {
                SynthDebugLog.append("net waiting for network i=$index")
                edgeNet.awaitUsable()
                SynthDebugLog.append("net usable again i=$index")
            }
        }
        delay(backoffMs)
        prefetchFailed.clear()
    }

    private suspend fun speak(s: Sentence, generation: Int) {
        when (_state.value.engineKey) {
            TtsEngines.EDGE -> speakEdge(index, generation)
            else -> speakSystem(speechText(s.text), sentenceIndex = index)
        }
    }

    /** Visible sentence text plus any TTS-only filter transforms. */
    private fun speechText(text: String): String = TextFilters.applySpeech(text, speechFilters)

    /**
     * Force-start (or restart) an Edge job for [i]. Prefer [ensurePrefetchJob] /
     * [scheduleAheadPrefetch] for lookahead so healthy in-flight work is kept.
     */
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

    /**
     * Cancel Edge jobs that are behind the playhead or outside the cache window.
     * Leaves in-flight ahead work alone.
     */
    private fun cancelStaleEdgeJobs(center: Int = index) {
        val window = cacheWindow(center)
        val stale = edgeJobs.keys.filter { it < center || it !in window }
        for (i in stale) {
            edgeJobs.remove(i)?.cancel()
        }
    }

    /**
     * Fill missing ahead clips nearest-first with bounded parallelism. Does not
     * cancel healthy in-flight jobs for sentences still ahead of [index].
     */
    private fun scheduleAheadPrefetch(center: Int = index) {
        if (_state.value.engineKey != TtsEngines.EDGE) return
        cancelStaleEdgeJobs(center)
        pumpPrefetch(center)
    }

    private fun pumpPrefetch(center: Int = index) {
        if (_state.value.engineKey != TtsEngines.EDGE) return
        if (!_state.value.playing) return
        val n = effectivePrefetchCount()
        val hi = (center + n).coerceAtMost(sentences.lastIndex)
        if (hi < center + 1) return

        val activeAhead = edgeJobs.count { (i, job) ->
            job.isActive && i in (center + 1)..hi
        }
        var slots = (PREFETCH_PARALLELISM - activeAhead).coerceAtLeast(0)
        if (slots <= 0) return

        for (target in (center + 1)..hi) {
            if (slots <= 0) break
            if (peekReadyCacheFile(target) != null) continue
            if (edgeJobs[target]?.isActive == true) continue
            if (target in prefetchFailed) continue
            launchPrefetchJob(target)
            slots--
        }
    }

    /** Start prefetch for [i] without canceling any other sentence's job. */
    private fun launchPrefetchJob(i: Int) {
        if (i < sentences.firstIndex || i > sentences.lastIndex) return
        if (edgeJobs[i]?.isActive == true) return
        lateinit var job: Job
        job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                prefetch(i)
            } finally {
                edgeJobs.remove(i, job)
                // Free a slot: fill the next nearest gap.
                scope.launch(Dispatchers.Main.immediate) { pumpPrefetch() }
            }
        }
        edgeJobs[i] = job
        job.start()
    }

    /** Request lookahead fill without canceling in-flight work for the same sentence. */
    private fun ensurePrefetchJob(i: Int) {
        if (i < sentences.firstIndex || i > sentences.lastIndex) return
        if (peekReadyCacheFile(i) != null) return
        scheduleAheadPrefetch()
    }

    private fun cancelEdgeJobs() {
        edgeJobs.values.forEach { it.cancel() }
        edgeJobs.clear()
    }

    private suspend fun prefetch(i: Int) {
        if (_state.value.engineKey != TtsEngines.EDGE) return
        // Drop work that fell behind the playhead while waiting for a slot.
        if (i < index || !inCacheWindow(i)) return
        try {
            synthesizeToCache(i, force = false, source = "pre")
            val f = cacheFile(i)
            if (f.exists() && f.length() > 0L) {
                audioEngine.prefetchDecode(f)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            // Lookahead is best-effort; the playhead retries (or skips) this sentence itself.
            prefetchFailed.add(i)
            SynthDebugLog.append("prefetch fail i=$i: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    private suspend fun speakEdge(i: Int, generation: Int) {
        SynthDebugLog.append(
            "speakEdge i=$i gen=$generation slack=${slackMs()} " +
                "pending=${audioEngine.hasPendingRemainder()} underruns=${audioEngine.underrunCount()}",
        )
        // Current sentence must be on disk before we can write — this is the only
        // ensure allowed to gate play (cold start / first clip).
        ensureSentenceCached(i, generation)
        if (generation != playGeneration) return
        val f = cacheFile(i)
        if (!f.exists() || f.length() == 0L) {
            throw IllegalStateException("Unable to play audio")
        }
        ensureWordBoundariesLoaded(i)
        ensurePcmWordTracking()
        val overlapMs = (-_state.value.sentenceGapMs).coerceAtLeast(0)

        // Nearest-first lookahead; keep in-flight ahead jobs.
        scheduleAheadPrefetch(i)

        // Instant peek only — never join Edge synth before play while the track is live.
        val nextReadyNow = peekReadyCacheFile(i + 1)
        if (nextReadyNow != null) {
            audioEngine.prefetchDecode(nextReadyNow)
            if (overlapMs > 0) ensureWordBoundariesLoaded(i + 1)
        }

        val wantFade = overlapMs > 0 && i < sentences.lastIndex
        // Resolve during body write inside the engine — never before play (FOSS sink rule).
        val resolveNext: (suspend () -> File?)? = if (wantFade && nextReadyNow == null) {
            resolve@{
                val waitStart = System.currentTimeMillis()
                SynthDebugLog.append(
                    "ensureNextDeferred start i=${i + 1} slack=${slackMs()}",
                )
                try {
                    ensureSentenceCached(i + 1, generation, source = "next")
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // The loop retries (or skips) i + 1 on its own turn; this clip still ends cleanly.
                    prefetchFailed.add(i + 1)
                    SynthDebugLog.append("ensureNextDeferred fail i=${i + 1}: ${t.message}")
                    return@resolve null
                }
                SynthDebugLog.append(
                    "ensureNextDeferred done i=${i + 1} " +
                        "waitMs=${System.currentTimeMillis() - waitStart} slack=${slackMs()}",
                )
                if (generation != playGeneration) return@resolve null
                ensureWordBoundariesLoaded(i + 1)
                val n = cacheFile(i + 1)
                if (n.exists() && n.length() > 0L) {
                    audioEngine.prefetchDecode(n)
                    n
                } else {
                    null
                }
            }
        } else {
            null
        }

        if (!wantFade && i < sentences.lastIndex) {
            val n = peekReadyCacheFile(i + 1)
            if (n != null) audioEngine.prefetchDecode(n)
        }

        val armOverlap = if (wantFade) overlapMs else 0
        SynthDebugLog.append(
            "play i=$i overlapMs=$armOverlap nextKnown=${nextReadyNow != null} " +
                "resolve=${resolveNext != null} slack=${slackMs()} " +
                "pending=${audioEngine.hasPendingRemainder()}",
        )
        audioEngine.play(
            file = f,
            generationActive = { generation == playGeneration },
            nextFile = nextReadyNow,
            resolveNext = resolveNext,
            overlapMs = armOverlap,
            onHearableStart = { seed ->
                pushWordSegment(i, audioEngine.writtenFrames(), seed)
            },
            onOverlapNextStart = {
                pushWordSegment(i + 1, audioEngine.writtenFrames(), seedSec = 0.0)
            },
        )
        SynthDebugLog.append(
            "playDone i=$i slack=${slackMs()} underruns=${audioEngine.underrunCount()}",
        )
    }

    /** Non-blocking: file present on disk with bytes. Does not start or join synth. */
    private fun peekReadyCacheFile(i: Int): File? {
        if (i < sentences.firstIndex || i > sentences.lastIndex) return null
        val f = cacheFile(i)
        return if (f.exists() && f.length() > 0L) f else null
    }

    private fun slackMs(): Long {
        val rate = audioEngine.sampleRateHz()
        if (rate <= 0) return -1L
        val slackFrames = audioEngine.writtenFrames() - audioEngine.playbackHeadFrames()
        return slackFrames * 1000L / rate
    }

    /**
     * Wait for an on-disk Edge MP3 for sentence [i], joining any in-flight prefetch
     * instead of starting a duplicate synthesize when possible.
     */
    private suspend fun ensureSentenceCached(i: Int, generation: Int, source: String = "play"): Boolean {
        if (generation != playGeneration) return false
        val f = cacheFile(i)
        if (f.exists() && f.length() > 0L) {
            ensureWordBoundariesLoaded(i)
            publishReady(i)
            return true
        }
        val inflight = edgeJobs[i]
        if (inflight != null) {
            inflight.join()
            if (generation != playGeneration) return false
            if (f.exists() && f.length() > 0L) {
                ensureWordBoundariesLoaded(i)
                publishReady(i)
                return true
            }
        }
        return synthesizeToCache(i, force = false, source = source)
    }

    /** Edge race over the lanes [edgeNet] picks for the current network; the outcome feeds back into it. */
    private suspend fun synthesizeEdge(text: String, tag: String): EdgeAudio {
        val lanes = withContext(Dispatchers.IO) { edgeNet.lanes() }
        val voice = edgeVoiceId()
        return edge.synthesize(
            text = text,
            voice = voice,
            lang = EdgeVoiceCatalog.langOf(voice, edgeVoices),
            ratePercent = ratePercent(),
            pitchPercent = pitchPercent(),
            lanes = lanes,
            onReport = edgeNet::report,
            tag = tag,
        )
    }

    /**
     * Ensure sentence [i] has an Edge MP3 on disk. When [force] is true, always re-synthesize.
     * [source] labels the synth log (`pre`, `play`, `next`, `regen`).
     * @return true if a usable file is ready afterward.
     */
    private suspend fun synthesizeToCache(i: Int, force: Boolean, source: String): Boolean {
        if (_state.value.engineKey != TtsEngines.EDGE) return false
        val s = sentences.getOrNull(i) ?: return false
        val f = cacheFile(i)
        val wordsFile = wordsCacheFile(i)
        if (!force && f.exists() && f.length() > 0L) {
            ensureWordBoundariesLoaded(i)
            publishReady(i)
            return true
        }
        if (force) {
            f.delete()
            wordsFile.delete()
            wordBoundariesBySentence.remove(i)
            cuesBySentence.remove(i)
        }
        val text = speechText(s.text)
        if (!SpeechText.isSpeakable(text)) {
            prefetchFailed.add(i)
            return false
        }
        publishGenerating(i)
        return try {
            val audio = synthesizeEdge(text, tag = "$source i=$i")
            writeAtomically(f, audio.mp3)
            writeWordBoundaries(wordsFile, audio.boundaries)
            wordBoundariesBySentence[i] = audio.boundaries
            val sentence = sentences.getOrNull(i)
            if (sentence != null && audio.boundaries.isNotEmpty()) {
                cuesBySentence[i] = WordHighlight.cuesFromBoundaries(sentence, audio.boundaries)
            } else {
                cuesBySentence.remove(i)
            }
            prefetchFailed.remove(i)
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

    private fun interruptClipPlayback() {
        audioEngine.cancel()
        stopPcmWordTracking()
        clearWordHighlight()
        systemTts?.stop()
    }

    private fun stopSessionAudio() {
        audioEngine.release()
        edgeNet.releaseCellular()
        keepAlive.stop()
        stopPcmWordTracking()
        clearWordHighlight()
    }

    private fun syncKeepAlive() {
        val st = _state.value
        val effective = TtsPrefs.effectiveMinSignal(
            minSignal = st.minSignal,
            underlayBtAddress = st.underlayBtAddress,
            targetConnected = st.underlayBtConnected,
        )
        if (st.playing && TtsPrefs.isUnderlayEnabled(effective)) {
            keepAlive.setLevel(effective)
            keepAlive.start()
        } else {
            keepAlive.stop()
        }
    }

    private fun pushWordSegment(sentenceIndex: Int, startWriteFrame: Long, seedSec: Double) {
        synchronized(wordSegmentsLock) {
            wordSegments.add(WordSegment(sentenceIndex, startWriteFrame, seedSec))
            if (wordSegments.size > 32) {
                wordSegments.subList(0, wordSegments.size - 24).clear()
            }
        }
    }

    private fun ensurePcmWordTracking() {
        if (wordTrackJob?.isActive == true) return
        wordTrackJob = scope.launch {
            var lastSi = -1
            var lastIdx = -1
            while (isActive && _state.value.playing) {
                val rate = audioEngine.sampleRateHz()
                if (rate > 0) {
                    val head = audioEngine.playbackHeadFrames()
                    val seg = synchronized(wordSegmentsLock) {
                        var best: WordSegment? = null
                        for (s in wordSegments) {
                            if (s.startWriteFrame <= head) best = s else break
                        }
                        best ?: wordSegments.firstOrNull()
                    }
                    if (seg != null) {
                        val played = (head - seg.startWriteFrame).coerceAtLeast(0L)
                        val syncSec = _state.value.highlightSyncMs / 1000.0
                        val sec = seg.seedSec + played.toDouble() / rate + syncSec
                        if (seg.sentenceIndex != lastSi) {
                            lastSi = seg.sentenceIndex
                            lastIdx = -1
                            publishHeardSentence(seg.sentenceIndex)
                        }
                        val cues = cuesBySentence[seg.sentenceIndex].orEmpty()
                        val idx = if (cues.isEmpty()) {
                            -1
                        } else {
                            cues.indexOf(WordHighlight.cueAtTime(cues, sec))
                        }
                        if (idx != lastIdx) {
                            lastIdx = idx
                            updateWordHighlight(seg.sentenceIndex, sec)
                        }
                    }
                }
                delay(16)
            }
        }
    }

    private fun stopPcmWordTracking() {
        wordTrackJob?.cancel()
        wordTrackJob = null
        synchronized(wordSegmentsLock) { wordSegments.clear() }
    }

    private fun updateWordHighlight(sentenceIndex: Int, mediaTimeSec: Double) {
        val sentence = sentences.getOrNull(sentenceIndex) ?: run {
            clearWordHighlight()
            return
        }
        val cues = cuesBySentence[sentenceIndex]
            ?: WordHighlight.cuesFromBoundaries(
                sentence,
                wordBoundariesBySentence[sentenceIndex].orEmpty(),
            ).also { if (it.isNotEmpty()) cuesBySentence[sentenceIndex] = it }
        if (cues.isEmpty()) {
            clearWordHighlight()
            return
        }
        val range = WordHighlight.cueAtTime(cues, mediaTimeSec)?.charRange
        val current = _state.value.wordHighlight
        if (range == current) return
        _state.update { it.copy(wordHighlight = range) }
    }

    private fun clearWordHighlight() {
        if (_state.value.wordHighlight == null) return
        _state.update { it.copy(wordHighlight = null) }
    }

    private fun ensureWordBoundariesLoaded(i: Int) {
        if (wordBoundariesBySentence.containsKey(i)) {
            if (!cuesBySentence.containsKey(i)) {
                val sentence = sentences.getOrNull(i)
                val bounds = wordBoundariesBySentence[i].orEmpty()
                if (sentence != null && bounds.isNotEmpty()) {
                    cuesBySentence[i] = WordHighlight.cuesFromBoundaries(sentence, bounds)
                }
            }
            return
        }
        val file = wordsCacheFile(i)
        if (!file.exists()) {
            wordBoundariesBySentence[i] = emptyList()
            return
        }
        val bounds = readWordBoundaries(file)
        wordBoundariesBySentence[i] = bounds
        val sentence = sentences.getOrNull(i)
        if (sentence != null && bounds.isNotEmpty()) {
            cuesBySentence[i] = WordHighlight.cuesFromBoundaries(sentence, bounds)
        }
    }

    private fun wordsCacheFile(i: Int): File =
        File(cacheDir, "b${bookKey}_s${i}_${voiceCacheKey()}.words.json")

    private fun writeWordBoundaries(file: File, boundaries: List<EdgeWordBoundary>) {
        val json = buildString {
            append('[')
            boundaries.forEachIndexed { idx, b ->
                if (idx > 0) append(',')
                append('{')
                append("\"offset\":").append(b.offset).append(',')
                append("\"duration\":").append(b.duration).append(',')
                append("\"text\":")
                appendJsonString(b.text)
                append('}')
            }
            append(']')
        }
        writeAtomically(file, json.toByteArray(Charsets.UTF_8))
    }

    private fun readWordBoundaries(file: File): List<EdgeWordBoundary> {
        return try {
            val raw = file.readText(Charsets.UTF_8)
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val text = obj.optString("text")
                    if (text.isEmpty()) continue
                    add(
                        EdgeWordBoundary(
                            offset = obj.optLong("offset", 0L),
                            duration = obj.optLong("duration", 0L),
                            text = text,
                        ),
                    )
                }
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u%04x".format(ch.code))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
        return this
    }

    private suspend fun speakSystem(text: String, sentenceIndex: Int?) {
        // Keep sentence highlight via publishSentence; only clear word until ranges arrive.
        clearWordHighlight()
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
        val voices = systemVoices(tts)
        if (voices != _state.value.voices) {
            _state.update { it.copy(voices = voices) }
        }
        val spokenSentence = sentenceIndex?.let { sentences.getOrNull(it) }
        suspendCancellableCoroutine { cont ->
            val id = UUID.randomUUID().toString()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        clearWordHighlight()
                        if (cont.isActive) cont.resume(Unit)
                    }
                }
                @Deprecated("deprecated")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == id && cont.isActive) {
                        cont.resumeWithException(IllegalStateException("System TTS error"))
                    }
                }
                override fun onRangeStart(
                    utteranceId: String?,
                    start: Int,
                    end: Int,
                    frame: Int,
                ) {
                    if (utteranceId != id || spokenSentence == null) return
                    val range = WordHighlight.rangeFromUtteranceChars(spokenSentence, start, end)
                        ?: return
                    val current = _state.value.wordHighlight
                    if (range != current) {
                        _state.update { it.copy(wordHighlight = range) }
                    }
                }
            })
            val code = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (code != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("System TTS unavailable"))
            }
            cont.invokeOnCancellation {
                tts.stop()
                clearWordHighlight()
            }
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
        return if (id.isNotBlank() && edgeVoiceOptions.any { it.id == id }) id else TtsPrefs.DEFAULT_EDGE_VOICE
    }

    private fun cacheFile(i: Int): File = File(cacheDir, "b${bookKey}_s${i}_${voiceCacheKey()}.mp3")

    private fun voiceCacheKey(): String =
        "${_state.value.engineKey}_${edgeVoiceId()}_${ratePercent()}_${pitchPercent()}"
            .plus(if (speechFilterKey.isEmpty()) "" else "_f$speechFilterKey")
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
        val n = effectivePrefetchCount()
        if (sentences.isEmpty()) return IntRange.EMPTY
        val lo = (center - n).coerceAtLeast(sentences.firstIndex)
        val hi = (center + n).coerceAtMost(sentences.lastIndex)
        return lo..hi
    }

    /**
     * Prefetch depth used for Edge lookahead. Overlap needs at least two sentences ahead
     * so the PCM writer is not blocked on a cold Edge synthesize between clips.
     */
    private fun effectivePrefetchCount(): Int {
        val n = _state.value.prefetchCount.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        return if (_state.value.sentenceGapMs < 0) maxOf(n, 2) else n
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
        prefetchFailed.clear()
        audioEngine.clearDecodeCache()
        clearAudioStatus()
        wordBoundariesBySentence.clear()
        cuesBySentence.clear()
        withContext(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { it.delete() }
        }
        clearAudioStatus()
    }

    private fun ratePercent(): Int = ((_state.value.speed - 1f) * 100f).toInt()

    private fun pitchPercent(): Int = ((_state.value.pitch - 1f) * 50f).toInt()

    companion object {
        /** Max concurrent Edge lookahead synths; nearest gaps fill first. */
        private const val PREFETCH_PARALLELISM = 2

        /** Chapters without text skipped in a row before playback treats it as the book's end. */
        private const val MAX_EMPTY_PULL = 8

        /** Playhead network retry backoff: 1 s, 2 s, 4 s, then 8 s per attempt. */
        private const val NETWORK_RETRY_BASE_MS = 1_000L
        private const val NETWORK_RETRY_MAX_MS = 8_000L

        /** `b<bookKey>_s<sentenceIndex>_<voiceKey>.mp3` */
        private val SENTENCE_CACHE_NAME = Regex("""^b([A-Za-z0-9.-]*)_s(\d+)_.*\.mp3$""")
        private val BOOK_KEY_UNSAFE = Regex("[^A-Za-z0-9.-]")
        private val VOICE_KEY_UNSAFE = Regex("[^A-Za-z0-9._-]")

        /** Used only if both the downloaded list and the bundled snapshot are unreadable. */
        val EdgeVoices = listOf(
            TtsVoiceOption("en-US-AndrewNeural", "Andrew · United States · Male", "English"),
            TtsVoiceOption("en-US-AriaNeural", "Aria · United States · Female", "English"),
            TtsVoiceOption("en-US-JennyNeural", "Jenny · United States · Female", "English"),
            TtsVoiceOption("en-US-GuyNeural", "Guy · United States · Male", "English"),
            TtsVoiceOption("en-US-MichelleNeural", "Michelle · United States · Female", "English"),
            TtsVoiceOption("en-GB-SoniaNeural", "Sonia · United Kingdom · Female", "English"),
            TtsVoiceOption("en-GB-RyanNeural", "Ryan · United Kingdom · Male", "English"),
            TtsVoiceOption("en-AU-NatashaNeural", "Natasha · Australia · Female", "English"),
        )
    }
}
