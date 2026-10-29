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
import android.os.SystemClock
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
import com.personal.flowreader.data.TonalUnderlay
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsEngines
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.data.UnderlayMode
import com.personal.flowreader.debug.DebugLog
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
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
import com.personal.flowreader.tts.provider.PluginTtsProvider
import com.personal.flowreader.tts.provider.SystemTtsProvider
import com.personal.flowreader.tts.provider.TtsCapability
import com.personal.flowreader.tts.provider.TtsSynthRequest

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
    val engineKey: String = TtsEngines.SYSTEM_DEFAULT,
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
    val underlayMode: UnderlayMode = UnderlayMode.Off,
    val minSignal: Float = TonalUnderlay.DEFAULT_LEVEL,
    /** Paired BT MAC that gates [UnderlayMode.Device]. */
    val underlayBtAddress: String = "",
    val underlayBtName: String = "",
    /** True while playing and media audio is routed to [underlayBtAddress]. */
    val underlayBtActive: Boolean = false,
    val sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    /** Added to heard media time when resolving Edge word cues (ms). */
    val highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    /** Ideal characters per TTS clip. */
    val clipTargetChars: Int = TtsPrefs.DEFAULT_CLIP_TARGET_CHARS,
    /** Characters allowed above/below [clipTargetChars]. */
    val clipFlexChars: Int = TtsPrefs.DEFAULT_CLIP_FLEX_CHARS,
    /** Current engine returns clip MP3 (prefetch / clip size UI). */
    val showClipSettings: Boolean = false,
    /** Current engine advertises network-lane / mobile-data policy. */
    val showNetworkLanes: Boolean = false,
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
    private val plugins: com.personal.flowreader.plugin.PluginManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cacheDir = File(context.cacheDir, "tts").apply { mkdirs() }
    private val providers = com.personal.flowreader.tts.provider.TtsProviderRegistry(
        plugins = plugins,
        systemTempDir = { File(cacheDir, "sys-synth") },
        obtainSystemTts = { pkg -> ensureSystemForPackage(pkg) },
    )
    private val edgeNet = EdgeNetwork(context)
    /** Cached voices per clip-audio engine key (`tts:{id}`). */
    private val clipVoicesByEngine = java.util.concurrent.ConcurrentHashMap<String, List<TtsVoiceOption>>()
    /** Consecutive playhead network failures; drives the retry backoff. */
    private var networkFailures = 0
    /** Consecutive playhead handshake rejections (200/401/403); drives [awaitServiceRetry]. */
    private var serviceFailures = 0
    private val keepAlive = AudioKeepAlive()
    private val audioEngine = TtsAudioEngine()
    private val underlayBt = UnderlayBluetoothMonitor(context) {
        audioEngine.routedDevice() ?: keepAlive.routedDevice()
    }.also { bt ->
        audioEngine.onRouteChanged = bt.routingListener
        keepAlive.onRouteChanged = bt.routingListener
    }
    /** Edge word boundaries keyed by sentence index for the attached book/voice. */
    private val wordBoundariesBySentence = ConcurrentHashMap<Int, List<EdgeWordBoundary>>()
    /** Cached TimedCues per sentence (built from Edge boundaries). */
    private val cuesBySentence = ConcurrentHashMap<Int, List<WordHighlight.TimedCue>>()
    /** Polls AudioTrack playback head for word highlight. */
    private var wordTrackJob: Job? = null
    /** Hearable timeline marks: playback head = startWriteFrame ? this sentence/seed. */
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

    private val focusResume = FocusResume()

    /** Delivered on the main thread (no handler on the request). */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val now = SystemClock.elapsedRealtime()
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                SynthDebugLog.append("focus loss permanent")
                focusResume.clear()
                // The system already dropped us; next play must request focus again.
                holdsAudioFocus = false
                audioFocusRequest = null
                if (_state.value.playing) scope.launch { pausePlayback(PlaybackState.STATE_PAUSED) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> {
                val playing = _state.value.playing
                SynthDebugLog.append("focus loss transient playing=$playing")
                focusResume.onTransientLoss(playing, now)
                if (playing) scope.launch { pausePlayback(PlaybackState.STATE_PAUSED) }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                val gain = focusResume.onGain(now) ?: return@OnAudioFocusChangeListener
                val secs = gain.elapsedMs / 1000
                if (gain.resume) {
                    SynthDebugLog.append("focus gain resume after ${secs}s")
                    play()
                } else {
                    SynthDebugLog.append(
                        "focus gain no-resume after ${secs}s (cap ${FOCUS_RESUME_MAX_MS / 1000}s)",
                    )
                }
            }
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

    private val _bookFinished = MutableSharedFlow<SpokenPosition>(extraBufferCapacity = 1)
    /** Emits the last sentence when playback reaches it and stops (finished position, Que auto-advance). */
    val bookFinished: SharedFlow<SpokenPosition> = _bookFinished

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
            val voices = voicesFor(prefs.engineKey)
            val voiceId = coerceVoiceId(prefs.engineKey, prefs.voiceId, voices)
            SynthDebugLog.setEnabled(reader.debugEnabled)
            underlayBt.setTargetAddress(prefs.underlayBtAddress)
            underlayBt.start()
            edgeNet.start()
            withContext(Dispatchers.IO) { edgeNet.setMobileDataAllowed(prefs.mobileDataFallback) }
            val (showClip, showLanes) = capabilityFlags(prefs.engineKey)
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
                    underlayMode = prefs.underlayMode,
                    minSignal = prefs.minSignal,
                    underlayBtAddress = prefs.underlayBtAddress,
                    underlayBtName = prefs.underlayBtName,
                    sentenceGapMs = prefs.sentenceGapMs,
                    highlightSyncMs = prefs.highlightSyncMs,
                    clipTargetChars = prefs.clipTargetChars,
                    clipFlexChars = prefs.clipFlexChars,
                    showClipSettings = showClip,
                    showNetworkLanes = showLanes,
                    debugEnabled = reader.debugEnabled,
                    voices = voices,
                )
            }
            refreshCatalog()
        }
        scope.launch {
            underlayBt.targetActive.collect { syncKeepAlive() }
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
            DebugLog.reader(
                "tts.attach earlyReturn sameSession start=c${start.chapterIndex} " +
                    "si=$index following?${_state.value.autoScrollWithTts}",
            )
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
        focusResume.clear()
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
        DebugLog.reader(
            "tts.attach full book=$bookId sameBook=$sameBook resume=$resume " +
                "start=c${start.chapterIndex} si=$index",
        )
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
                if (usesClipAudio()) previewWithEdge(snippet)
                else speakSystem(snippet, sentenceIndex = null)
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
        if (!usesClipAudio()) {
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
        focusResume.clear()
        if (sentences.isEmpty()) return
        scope.launch { startPlayback(follow) }
    }

    fun pause() {
        focusResume.clear()
        scope.launch { pausePlayback(PlaybackState.STATE_PAUSED) }
    }

    /** Tear down playback entirely and end the session (notification Stop, Library card X). */
    fun stop() {
        focusResume.clear()
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
        val value = TonalUnderlay.coerceLevel(level)
        _state.update { it.copy(minSignal = value) }
        syncKeepAlive()
        if (persist) scope.launch { settings.setMinSignal(value) }
    }

    fun setUnderlayMode(mode: UnderlayMode) {
        if (mode == _state.value.underlayMode) return
        _state.update { it.copy(underlayMode = mode) }
        if (mode == UnderlayMode.Device) underlayBt.refresh()
        syncKeepAlive()
        scope.launch { settings.setUnderlayMode(mode) }
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
            )
        }
        syncKeepAlive()
        scope.launch { settings.setUnderlayBtDevice(addr, label) }
    }

    /** Paired devices for the underlay Bluetooth picker (needs BLUETOOTH_CONNECT on API 31+). */
    fun underlayBondedDevices(): List<PairedBtDevice> = underlayBt.bondedDevices()

    fun refreshUnderlayRoute() {
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
            scope.launch {
                settings.setDebugEnabled(true)
                settings.setDebugTtsLog(true)
            }
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
     * Snapshot the debug ring buffer + live engine counters to a file under
     * app external files (`synth-logs/`).
     */
    fun dumpSynthLog(): File {
        val dir = File(context.getExternalFilesDir(null), "synth-logs").apply { mkdirs() }
        val out = File(dir, "debug-${System.currentTimeMillis()}.txt")
        val s = _state.value
        val header = buildString {
            appendLine("Flow Reader debug dump")
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
        if (!usesClipAudio()) return
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
        val preferred = _state.value.voiceId
        val (showClip, showLanes) = capabilityFlags(key)
        // Clear stale voices immediately so the UI never shows another engine's list.
        _state.update {
            it.copy(
                engineKey = key,
                voiceId = preferred,
                voices = emptyList(),
                showClipSettings = showClip,
                showNetworkLanes = showLanes,
                error = null,
            )
        }
        scope.launch {
            settings.setEngine(key)
            clearEdgeCache()
            if (usesClipAudio(key)) {
                val provider = providers.get(key)
                if (provider != null) {
                    runCatching { clipVoicesByEngine[key] = provider.listVoices() }
                }
            } else {
                systemTts?.shutdown()
                systemTts = null
                systemReady = false
                boundEnginePackage = null
                val pkg = if (key == TtsEngines.SYSTEM_DEFAULT) null else key
                runCatching { ensureSystemForPackage(pkg) }
            }
            val voices = voicesFor(key)
            val voiceId = coerceVoiceId(key, preferred, voices)
            _state.update { it.copy(voices = voices, voiceId = voiceId) }
            settings.setVoice(voiceId)
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
            DebugLog.reader("tts.userScrolledAway si=$index")
            _state.update { it.copy(following = false) }
        }
    }

    fun followAgain() {
        if (!_state.value.autoScrollWithTts) return
        DebugLog.reader("tts.followAgain si=$index")
        _state.update { it.copy(following = true) }
    }

    fun jumpTo(locus: Locus) {
        index = sentences.indexAt(locus)
        DebugLog.reader(
            "tts.jumpTo c=${locus.chapterIndex} b=${locus.blockIndex} o=${locus.charOffset} si=$index",
        )
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

    /** Re-probe system TTS engines and TTS plugins (e.g. after installing Neko Speak). */
    fun refreshEngines() {
        scope.launch { refreshCatalog() }
    }

    private suspend fun refreshCatalog() {
        val systemExtras = ArrayList<TtsEngineOption>()
        runCatching {
            val probe = ensureSystemForPackage(null)
            probe.engines
                ?.mapNotNull { info ->
                    val pkg = info.name ?: return@mapNotNull null
                    if (pkg.isBlank()) return@mapNotNull null
                    // "system" / default engine package is already listed as Default.
                    if (pkg == TtsEngines.SYSTEM_DEFAULT) return@mapNotNull null
                    TtsEngineOption(pkg, info.label?.ifBlank { pkg } ?: pkg)
                }
                ?.distinctBy { it.key }
                ?.forEach { systemExtras += it }
        }
        val providerList = providers.refresh(systemExtras)
        for (p in providerList) {
            if (p.has(TtsCapability.ClipAudio)) {
                runCatching { clipVoicesByEngine[p.id] = p.listVoices() }
            }
        }
        val engines = providers.engineOptions()
        var key = _state.value.engineKey
        if (providers.get(key) == null) {
            key = TtsEngines.SYSTEM_DEFAULT
        }
        // System clip engines still need a bound TextToSpeech for synthesizeToFile / voices.
        if (providers.get(key) is SystemTtsProvider) {
            val pkg = if (key == TtsEngines.SYSTEM_DEFAULT) null else key
            runCatching { ensureSystemForPackage(pkg) }
        }
        val voices = voicesFor(key)
        val voiceId = coerceVoiceId(key, _state.value.voiceId, voices)
        val (showClip, showLanes) = capabilityFlags(key)
        _state.update {
            it.copy(
                engineKey = key,
                engines = engines,
                voices = voices,
                voiceId = voiceId,
                showClipSettings = showClip,
                showNetworkLanes = showLanes,
            )
        }
    }

    private fun usesClipAudio(engineKey: String = _state.value.engineKey): Boolean =
        providers.get(engineKey)?.has(com.personal.flowreader.tts.provider.TtsCapability.ClipAudio) == true

    private fun usesPrefetch(engineKey: String = _state.value.engineKey): Boolean =
        providers.get(engineKey)?.has(com.personal.flowreader.tts.provider.TtsCapability.Prefetch) == true

    private fun usesNetworkLanes(engineKey: String = _state.value.engineKey): Boolean =
        providers.get(engineKey)?.has(com.personal.flowreader.tts.provider.TtsCapability.NetworkLanes) == true

    private fun capabilityFlags(engineKey: String): Pair<Boolean, Boolean> {
        val clip = usesClipAudio(engineKey) || usesPrefetch(engineKey)
        return clip to usesNetworkLanes(engineKey)
    }

    private fun coerceVoiceId(
        engineKey: String,
        preferred: String,
        voices: List<TtsVoiceOption>,
    ): String = TtsVoicePick.coerce(
        preferred = preferred,
        voices = voices,
        pluginDefault = if (providers.get(engineKey) is PluginTtsProvider) {
            TtsPrefs.DEFAULT_EDGE_VOICE
        } else {
            null
        },
    )

    private fun systemBoundMatches(engineKey: String): Boolean {
        if (!systemReady || systemTts == null) return false
        val expected = if (engineKey == TtsEngines.SYSTEM_DEFAULT) null else engineKey
        return boundEnginePackage == expected
    }

    private fun voicesFor(engineKey: String): List<TtsVoiceOption> {
        val provider = providers.get(engineKey)
        if (provider != null && provider.has(TtsCapability.ClipAudio)) {
            val cached = clipVoicesByEngine[engineKey].orEmpty()
            if (cached.isNotEmpty()) return cached
            return if (provider is SystemTtsProvider) {
                listOf(TtsVoiceOption("default", "Default"))
            } else {
                FALLBACK_CLIP_VOICES
            }
        }
        val tts = systemTts
        return if (tts != null && systemBoundMatches(engineKey)) systemVoices(tts)
        else listOf(TtsVoiceOption("default", "Default"))
    }

    private fun systemVoices(tts: TextToSpeech): List<TtsVoiceOption> {
        val voices = tts.voices?.toList().orEmpty()
        if (voices.isEmpty()) {
            return listOf(TtsVoiceOption("default", "Default"))
        }
        return voices
            .sortedWith(compareBy<Voice> { it.locale.toLanguageTag() }.thenBy { it.name })
            .map { v ->
                val langTag = v.locale.toLanguageTag()
                val language = v.locale.getDisplayLanguage(Locale.ENGLISH).ifBlank { langTag }
                val label = buildString {
                    append(v.name.substringAfterLast(".").ifBlank { v.name })
                    append(" (")
                    append(langTag)
                    append(")")
                }
                TtsVoiceOption(
                    v.name,
                    label,
                    language = language,
                    gender = VoiceGender.infer(v.name, label),
                )
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
                    focusResume.clear()
                    if (_state.value.playing) pause()
                }
                override fun onSkipToNext() {
                    skipNext()
                }
                override fun onSkipToPrevious() {
                    skipPrev()
                }
                override fun onStop() {
                    stop()
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
            if (usesClipAudio()) {
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
                serviceFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: EdgeContentException) {
                reportSkippedSentence(index, s.text, e.message ?: "Edge rejected text")
            } catch (e: EdgeServiceException) {
                awaitServiceRetry(e)
                if (generation != playGeneration) return
                continue
            } catch (e: EdgeNetworkException) {
                awaitNetworkRetry(e)
                if (generation != playGeneration) return
                continue
            } catch (t: Throwable) {
                // Hard fail (incl. exhausted race / OOM): pause on this sentence, open the log.
                // Do not skip; do not tear down the session ? Play retries the same clip.
                reportMediaError(t.message ?: "TTS failed")
                SynthDebugLog.requestOpen()
                playGeneration++
                stopSessionAudio()
                systemTts?.stop()
                _state.update { it.copy(playing = false) }
                setSessionState(PlaybackState.STATE_PAUSED)
                TtsPlaybackService.refresh()
                return
            }
            if (generation != playGeneration) return
            val gapMs = _state.value.sentenceGapMs
            if (gapMs > 0) {
                if (usesClipAudio()) {
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
                if (usesClipAudio()) {
                    awaitHeardCatchUp(generation)
                }
                if (generation != playGeneration) return
                stopSessionAudio()
                _state.update { it.copy(playing = false, sessionActive = false) }
                setSessionState(PlaybackState.STATE_STOPPED)
                abandonAudioFocus()
                unregisterNoisyReceiver()
                TtsPlaybackService.stop()
                reading?.let { _bookFinished.tryEmit(SpokenPosition(it, s)) }
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

    /**
     * Edge refused the handshake on both hosts (service change or clock skew). The network is fine,
     * so back off on a fixed ladder instead of waiting for connectivity; the log opens on the first.
     */
    private suspend fun awaitServiceRetry(e: EdgeServiceException) {
        serviceFailures++
        val backoffMs = SERVICE_RETRY_MS[(serviceFailures - 1).coerceAtMost(SERVICE_RETRY_MS.lastIndex)]
        reportMediaError(
            "edge rejected i=$index status=${e.status} failures=$serviceFailures backoffMs=$backoffMs: ${e.message}",
        )
        if (serviceFailures == 1) SynthDebugLog.requestOpen()
        delay(backoffMs)
        prefetchFailed.clear()
    }

    private suspend fun speak(s: Sentence, generation: Int) {
        if (usesClipAudio()) speakEdge(index, generation)
        else speakSystem(speechText(s.text), sentenceIndex = index)
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
        if (!usesClipAudio()) return
        cancelStaleEdgeJobs(center)
        pumpPrefetch(center)
    }

    private fun pumpPrefetch(center: Int = index) {
        if (!usesClipAudio()) return
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
        if (!usesClipAudio()) return
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
        val playText = speechText(sentences.getOrNull(i)?.text.orEmpty())
        SynthDebugLog.append(
            "play i=$i overlapMs=$armOverlap nextKnown=${nextReadyNow != null} " +
                "resolve=${resolveNext != null} slack=${slackMs()} " +
                "pending=${audioEngine.hasPendingRemainder()}",
        )
        SynthDebugLog.append(
            "tts assign play i=$i textHash=${assignTextHash(playText)} " +
                "file=${f.name} fileHash=${assignFileHash(f)} bytes=${f.length()} " +
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

    /** Clip-audio provider synthesize. Network plugins use staggered race; others are single-shot. */
    private suspend fun synthesizeEdge(text: String, tag: String): EdgeAudio {
        val provider = providers.get(_state.value.engineKey)
            ?: throw IllegalStateException("No TTS provider for ${_state.value.engineKey}")
        val voice = edgeVoiceId()
        val langTag = voice.split('-').take(2).joinToString("-").ifBlank { "en-US" }
        val request = TtsSynthRequest(
            text = text,
            voice = voice,
            lang = langTag,
            ratePercent = ratePercent(),
            pitchPercent = pitchPercent(),
        )
        SynthDebugLog.append("tts synth[$tag] provider=${provider.id} voice=$voice")
        return if (provider is PluginTtsProvider && provider.has(TtsCapability.NetworkLanes)) {
            racePluginSynth(provider, request, tag)
        } else {
            synthOnce(provider, request)
        }
    }

    private suspend fun synthOnce(
        provider: com.personal.flowreader.tts.provider.TtsProvider,
        request: TtsSynthRequest,
        raceSlot: Int = 0,
        network: android.net.Network? = null,
    ): EdgeAudio {
        try {
            val clip = if (provider is PluginTtsProvider) {
                provider.synthesize(request, raceSlot = raceSlot, network = network)
            } else {
                provider.synthesize(request)
            }
            return EdgeAudio(clip.mp3, clip.boundaries, clip.connectId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: com.personal.flowreader.plugin.api.PluginException) {
            throw mapPluginSynthError(e)
        } catch (e: EdgeContentException) {
            throw e
        } catch (e: EdgeServiceException) {
            throw e
        } catch (e: EdgeNetworkException) {
            throw e
        } catch (t: Throwable) {
            throw mapSynthThrowable(t)
        }
    }

    /**
     * Staggered race over plugin synthesize: attempt 0 immediately, backups after hedge delay
     * while no attempt has audio yet; first success wins and cancels the rest.
     */
    private suspend fun racePluginSynth(
        provider: PluginTtsProvider,
        request: TtsSynthRequest,
        tag: String,
    ): EdgeAudio = coroutineScope {
        val lanes = withContext(Dispatchers.IO) { edgeNet.lanes() }
        if (lanes.isEmpty()) throw EdgeNetworkException("No network for TTS")
        val offsets = lanes.map { it.startMs }
        val raceStart = SystemClock.elapsedRealtime()
        val winner = CompletableDeferred<Pair<Int, EdgeAudio>>()
        val failedLanes = java.util.Collections.synchronizedSet(linkedSetOf<NetLane>())
        val inFlight = AtomicInteger(0)
        val hasAudio = AtomicBoolean(false)
        val hardFail = java.util.concurrent.atomic.AtomicReference<Throwable?>(null)
        var started = 0
        val attempts = ArrayList<Job>(lanes.size)

        fun launchAttempt(idx: Int) {
            val lane = lanes[idx]
            SynthDebugLog.append(
                "edge race[$tag #${idx}] start lane=${lane.lane.name.lowercase()} @${lane.startMs}",
            )
            inFlight.incrementAndGet()
            attempts += launch(Dispatchers.IO) {
                try {
                    val audio = synthOnce(provider, request, raceSlot = idx, network = lane.network)
                    hasAudio.set(true)
                    winner.complete(idx to audio)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: EdgeContentException) {
                    // Text refused ? no point hedging.
                    winner.completeExceptionally(e)
                } catch (e: EdgeServiceException) {
                    failedLanes.add(lane.lane)
                    hardFail.compareAndSet(null, e)
                    SynthDebugLog.append(
                        "edge race[$tag #${idx}] fail lane=${lane.lane.name.lowercase()}: ${e.message}",
                    )
                } catch (e: EdgeNetworkException) {
                    failedLanes.add(lane.lane)
                    SynthDebugLog.append(
                        "edge race[$tag #${idx}] fail lane=${lane.lane.name.lowercase()}: ${e.message}",
                    )
                } catch (t: Throwable) {
                    failedLanes.add(lane.lane)
                    hardFail.compareAndSet(null, t)
                    SynthDebugLog.append(
                        "edge race[$tag #${idx}] fail lane=${lane.lane.name.lowercase()}: " +
                            (t.message ?: t.javaClass.simpleName),
                    )
                } finally {
                    inFlight.decrementAndGet()
                }
            }
        }

        val scheduler = launch {
            while (isActive && !winner.isCompleted && started < lanes.size) {
                val elapsed = SystemClock.elapsedRealtime() - raceStart
                val due = RaceSchedule.due(
                    startMs = offsets,
                    elapsedMs = elapsed,
                    started = started,
                    inFlight = inFlight.get(),
                    audioInFlight = hasAudio.get(),
                )
                for (idx in due) {
                    launchAttempt(idx)
                    started++
                }
                if (winner.isCompleted) break
                val next = RaceSchedule.nextStartMs(offsets, started) ?: break
                val wait = next - (SystemClock.elapsedRealtime() - raceStart)
                if (wait > 0L) delay(wait) else yield()
            }
            attempts.forEach { it.join() }
            if (!winner.isCompleted) {
                winner.completeExceptionally(
                    hardFail.get() ?: EdgeNetworkException("All TTS race attempts failed"),
                )
            }
        }

        try {
            val (winIdx, audio) = winner.await()
            attempts.forEach { it.cancel() }
            val totalMs = SystemClock.elapsedRealtime() - raceStart
            val winLane = lanes[winIdx]
            edgeNet.report(
                RaceReport(
                    winner = winLane.lane,
                    firstAudioMs = totalMs,
                    winnerStartMs = winLane.startMs,
                    winnerLatencyMs = (totalMs - winLane.startMs).coerceAtLeast(0L),
                    totalMs = totalMs,
                    started = started,
                    failed = failedLanes.toList(),
                ),
            )
            SynthDebugLog.append(
                "edge race[$tag] win lane=${winLane.lane.name.lowercase()} " +
                    "firstAudioMs=$totalMs started=$started",
            )
            audio
        } finally {
            scheduler.cancel()
            attempts.forEach { it.cancel() }
        }
    }

    private fun mapPluginSynthError(e: com.personal.flowreader.plugin.api.PluginException): Throwable {
        val msg = e.message.orEmpty()
        // QuickJS OOM / script InternalError: hard-fail at playhead after race, not content-skip.
        if (msg.contains("out of memory", ignoreCase = true) ||
            msg.contains("InternalError", ignoreCase = true)
        ) {
            return IllegalStateException(msg.ifBlank { "Plugin out of memory" }, e)
        }
        return when (e.code) {
            com.personal.flowreader.plugin.api.PluginErrorCode.Parse ->
                EdgeContentException(msg.ifBlank { "Nothing to synthesize." })
            com.personal.flowreader.plugin.api.PluginErrorCode.AuthRequired -> {
                val status = Regex("""status=(\d+)""").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 403
                EdgeServiceException(msg.ifBlank { "TTS rejected handshake" }, status)
            }
            com.personal.flowreader.plugin.api.PluginErrorCode.Network,
            com.personal.flowreader.plugin.api.PluginErrorCode.Timeout,
            -> EdgeNetworkException(msg.ifBlank { "TTS network failure" }, e)
            else -> mapSynthThrowable(e)
        }
    }

    private fun mapSynthThrowable(t: Throwable): Throwable {
        val msg = t.message.orEmpty()
        val status = Regex("""status=(\d+)""").find(msg)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (status == 200 || status == 401 || status == 403) {
            return EdgeServiceException(msg.ifBlank { "TTS rejected handshake" }, status)
        }
        if (t is java.io.IOException || msg.contains("WebSocket", ignoreCase = true)) {
            return EdgeNetworkException(msg.ifBlank { "TTS network failure" }, t)
        }
        return t
    }

    /**
     * Ensure sentence [i] has an Edge MP3 on disk. When [force] is true, always re-synthesize.
     * [source] labels the synth log (`pre`, `play`, `next`, `regen`).
     * @return true if a usable file is ready afterward.
     */
    private suspend fun synthesizeToCache(i: Int, force: Boolean, source: String): Boolean {
        if (!usesClipAudio()) return false
        val s = sentences.getOrNull(i) ?: return false
        val f = cacheFile(i)
        val wordsFile = wordsCacheFile(i)
        val text = speechText(s.text)
        val textHash = assignTextHash(text)
        if (!force && f.exists() && f.length() > 0L) {
            SynthDebugLog.append(
                "tts assign hit i=$i textHash=$textHash file=${f.name} " +
                    "fileHash=${assignFileHash(f)} bytes=${f.length()}",
            )
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
        if (!SpeechText.isSpeakable(text)) {
            prefetchFailed.add(i)
            return false
        }
        publishGenerating(i)
        return try {
            val audio = synthesizeEdge(text, tag = "$source i=$i")
            val fileHash = assignFileHash(audio.mp3)
            SynthDebugLog.append(
                "tts assign synth i=$i source=$source connectId=${audio.connectId.ifBlank { "-" }} " +
                    "textHash=$textHash fileHash=$fileHash bytes=${audio.mp3.size}",
            )
            writeAtomically(f, audio.mp3)
            SynthDebugLog.append(
                "tts assign write i=$i connectId=${audio.connectId.ifBlank { "-" }} " +
                    "textHash=$textHash file=${f.name} fileHash=$fileHash bytes=${audio.mp3.size}",
            )
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
        val playing = _state.value.playing
        if (playing && _state.value.underlayMode == UnderlayMode.Device) underlayBt.refresh()
        val active = playing && underlayBt.targetActive.value
        if (active != _state.value.underlayBtActive) _state.update { it.copy(underlayBtActive = active) }
        val st = _state.value
        val effective = TonalUnderlay.effectiveLevel(
            mode = st.underlayMode,
            level = st.minSignal,
            btAddress = st.underlayBtAddress,
            targetActive = active,
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
                if (!usesClipAudio()) {
                    val voices = systemVoices(engine)
                    val voiceId = TtsVoicePick.coerce(_state.value.voiceId, voices)
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
        val key = _state.value.engineKey
        val known = clipVoicesByEngine[key].orEmpty()
        if (id.isNotBlank() && (known.isEmpty() || known.any { it.id == id })) return id
        if (providers.get(key) is PluginTtsProvider) return TtsPrefs.DEFAULT_EDGE_VOICE
        return known.firstOrNull()?.id.orEmpty().ifBlank { "default" }
    }

    private fun cacheExtension(): String =
        if (providers.get(_state.value.engineKey) is PluginTtsProvider) "mp3" else "wav"

    private fun cacheFile(i: Int): File {
        val base = "b${bookKey}_s${i}_${voiceCacheKey()}"
        val preferred = File(cacheDir, "$base.${cacheExtension()}")
        if (preferred.exists()) return preferred
        val mp3 = File(cacheDir, "$base.mp3")
        if (mp3.exists()) return mp3
        val wav = File(cacheDir, "$base.wav")
        if (wav.exists()) return wav
        return preferred
    }

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
        if (!usesClipAudio() || sentences.isEmpty()) return emptySet()
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
        if (!usesPrefetch()) return 0
        val n = _state.value.prefetchCount.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        return if (_state.value.sentenceGapMs < 0) maxOf(n, 2) else n
    }

    private fun inCacheWindow(i: Int, center: Int = index): Boolean = i in cacheWindow(center)

    /**
     * Keep at most prefetchCount behind + current + prefetchCount ahead on disk and in UI state.
     * Full-book audio retention is intentionally not supported yet.
     */
    private fun pruneCacheToWindow(center: Int = index) {
        if (!usesClipAudio() || sentences.isEmpty()) return
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

    /** Short hex fingerprint of speakable sentence text for assign logs. */
    private fun assignTextHash(text: String): String =
        Integer.toUnsignedString(text.hashCode(), 16)

    /** Short CRC hex of clip bytes for assign logs. */
    private fun assignFileHash(bytes: ByteArray): String {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return java.lang.Long.toUnsignedString(crc.value, 16)
    }

    /** Stream CRC of an on-disk clip (cache hit / play). */
    private fun assignFileHash(file: File): String {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                crc.update(buf, 0, n)
            }
        }
        return java.lang.Long.toUnsignedString(crc.value, 16)
    }

    companion object {
        /** Max concurrent Edge lookahead synths; nearest gaps fill first. */
        private const val PREFETCH_PARALLELISM = 2

        /** Chapters without text skipped in a row before playback treats it as the book's end. */
        private const val MAX_EMPTY_PULL = 8

        /** Playhead network retry backoff: 1 s, 2 s, 4 s, then 8 s per attempt. */
        private const val NETWORK_RETRY_BASE_MS = 1_000L
        private const val NETWORK_RETRY_MAX_MS = 8_000L

        /** Playhead retry after Edge rejected the handshake; the last value repeats. */
        private val SERVICE_RETRY_MS = longArrayOf(2_000L, 5_000L, 15_000L, 30_000L, 60_000L)

        /** `b<bookKey>_s<sentenceIndex>_<voiceKey>.(mp3|wav)` */
        private val SENTENCE_CACHE_NAME = Regex("""^b([A-Za-z0-9.-]*)_s(\d+)_.*\.(mp3|wav)$""")
        private val BOOK_KEY_UNSAFE = Regex("[^A-Za-z0-9.-]")
        private val VOICE_KEY_UNSAFE = Regex("[^A-Za-z0-9._-]")

        /** Used only while a clip-audio plugin voice list is still loading. */
        private val FALLBACK_CLIP_VOICES = listOf(
            TtsVoiceOption("en-US-AndrewNeural", "en-US-AndrewNeural", "English", "male"),
            TtsVoiceOption("en-US-AriaNeural", "en-US-AriaNeural", "English", "female"),
            TtsVoiceOption("en-US-JennyNeural", "en-US-JennyNeural", "English", "female"),
            TtsVoiceOption("en-US-GuyNeural", "en-US-GuyNeural", "English", "male"),
        )
    }
}
