package com.personal.flowreader.tts

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.Sentence
import com.personal.flowreader.data.SentenceSplitter
import com.personal.flowreader.data.SettingsStore
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsEngines
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
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
    val engines: List<TtsEngineOption> = listOf(
        TtsEngineOption(TtsEngines.EDGE, "Edge TTS"),
        TtsEngineOption(TtsEngines.SYSTEM_DEFAULT, "System Default"),
    ),
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

    private var doc: BookDoc? = null
    private var sentences: List<Sentence> = emptyList()
    private var index = 0
    private var playJob: Job? = null
    private var player: MediaPlayer? = null
    private val playbackMutex = Mutex()
    /** Serializes play / pause / restart so MediaSession echoes can't fork loops. */
    private val playGate = Mutex()
    /** Bumped on every stop/restart; playFile ignores stale generations. */
    private var playGeneration = 0
    /** In-flight Edge synthesize/prefetch jobs keyed by sentence index. */
    private val edgeJobs = mutableMapOf<Int, Job>()
    private var systemTts: TextToSpeech? = null
    private var systemReady = false
    private var boundEnginePackage: String? = null
    private var session: MediaSession? = null
    /** Suppress MediaSession onPlay while we push PLAYING ourselves. */
    private var suppressSessionPlay = false

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state

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
                    voices = voices,
                )
            }
            refreshCatalog()
        }
    }

    fun attach(book: BookDoc, start: Locus) {
        // Sync teardown so a new book never shares a live player/loop (QuickNovel stop-before-play).
        playGeneration++
        playJob?.cancel()
        playJob = null
        releasePlayer()
        systemTts?.stop()
        doc = book
        sentences = SentenceSplitter.split(book)
        index = SentenceSplitter.indexAt(sentences, start)
        val s = sentences.getOrNull(index)
        val ready = scanReadySentenceIndices()
        _state.update {
            it.copy(
                playing = false,
                following = true,
                sentence = s,
                sentenceIndex = index,
                snippet = s?.text.orEmpty(),
                error = null,
                readySentenceIndices = ready,
                generatingSentenceIndices = emptySet(),
            )
        }
        pruneCacheToWindow(index)
        scope.launch { refreshCatalog() }
    }

    fun play() {
        if (sentences.isEmpty()) return
        scope.launch { startPlayback() }
    }

    fun pause() {
        scope.launch { pausePlayback(PlaybackState.STATE_PAUSED) }
    }

    fun stop() {
        scope.launch { pausePlayback(PlaybackState.STATE_STOPPED) }
    }

    fun skipNext() {
        if (index < sentences.lastIndex) {
            index++
            publishSentence()
            if (_state.value.playing) restartLoop()
        }
    }

    fun skipPrev() {
        if (index > 0) {
            index--
            publishSentence()
            if (_state.value.playing) restartLoop()
        }
    }

    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.5f, 2.5f)
        _state.update { it.copy(speed = value) }
        scope.launch { settings.setSpeed(value) }
        if (_state.value.playing) restartLoop()
    }

    fun setPitch(pitch: Float) {
        val value = pitch.coerceIn(0.5f, 2f)
        _state.update { it.copy(pitch = value) }
        scope.launch { settings.setPitch(value) }
        clearEdgeCache()
        if (_state.value.playing) restartLoop()
    }

    fun setPrefetchCount(count: Int) {
        val value = count.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        if (value == _state.value.prefetchCount) return
        _state.update { it.copy(prefetchCount = value) }
        scope.launch { settings.setPrefetchCount(value) }
        pruneCacheToWindow(index)
        if (_state.value.playing) restartLoop()
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
        }
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

    fun setVoice(voiceId: String) {
        if (voiceId == _state.value.voiceId) return
        _state.update { it.copy(voiceId = voiceId) }
        scope.launch { settings.setVoice(voiceId) }
        clearEdgeCache()
        if (_state.value.playing) restartLoop()
    }

    fun userScrolledAway() {
        if (_state.value.playing && _state.value.following) {
            _state.update { it.copy(following = false) }
        }
    }

    fun followAgain() {
        _state.update { it.copy(following = true) }
    }

    fun jumpTo(locus: Locus) {
        index = SentenceSplitter.indexAt(sentences, locus)
        _state.update { it.copy(following = true) }
        publishSentence()
        if (_state.value.playing) restartLoop()
    }

    fun currentLocus(): Locus {
        val s = sentences.getOrNull(index) ?: return Locus()
        return Locus(s.chapterIndex, s.blockIndex, s.start)
    }

    fun release() {
        playGeneration++
        playJob?.cancel()
        playJob = null
        releasePlayer()
        systemTts?.stop()
        systemTts?.shutdown()
        systemTts = null
        _state.update { it.copy(playing = false) }
        session?.release()
        session = null
    }

    /**
     * Readest-style: await previous speak teardown, then start one loop under [playGate].
     * QuickNovel-style: bump generation + stop player before any new MediaPlayer.
     */
    private suspend fun startPlayback() = playGate.withLock {
        if (_state.value.playing && playJob?.isActive == true) return@withLock
        ensureSession()
        playGeneration++
        val generation = playGeneration
        val previous = playJob
        playJob = null
        previous?.cancelAndJoin()
        releasePlayer()
        systemTts?.stop()
        _state.update { it.copy(playing = true, following = true, error = null) }
        playJob = scope.launch {
            loop(generation)
        }
        suppressSessionPlay = true
        try {
            setSessionState(PlaybackState.STATE_PLAYING)
        } finally {
            suppressSessionPlay = false
        }
    }

    private suspend fun pausePlayback(sessionState: Int) = playGate.withLock {
        playGeneration++
        val previous = playJob
        playJob = null
        previous?.cancelAndJoin()
        releasePlayer()
        systemTts?.stop()
        _state.update { it.copy(playing = false) }
        setSessionState(sessionState)
    }

    private suspend fun refreshCatalog() {
        val engines = mutableListOf(
            TtsEngineOption(TtsEngines.EDGE, "Edge TTS"),
            TtsEngineOption(TtsEngines.SYSTEM_DEFAULT, "System Default"),
        )
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
        val voices = voicesFor(key, forceRefresh = true)
        val voiceId = when {
            voices.any { it.id == _state.value.voiceId } -> _state.value.voiceId
            key == TtsEngines.EDGE -> TtsPrefs.DEFAULT_EDGE_VOICE
            else -> voices.firstOrNull()?.id.orEmpty()
        }
        _state.update {
            it.copy(engines = engines, voices = voices, voiceId = voiceId)
        }
    }

    private fun voicesFor(engineKey: String, forceRefresh: Boolean = false): List<TtsVoiceOption> {
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
            })
            isActive = true
        }
    }

    private fun setSessionState(state: Int) {
        val s = session ?: return
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, _state.value.speed)
                .build(),
        )
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
                releasePlayer()
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
            for (offset in 1..n) {
                val target = index + offset
                startEdgeJob(target) { prefetch(target) }
            }
            try {
                speak(s, generation)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update { it.copy(playing = false, error = t.message ?: "TTS failed") }
                setSessionState(PlaybackState.STATE_STOPPED)
                return
            }
            if (generation != playGeneration) return
            if (index >= sentences.lastIndex) {
                _state.update { it.copy(playing = false) }
                setSessionState(PlaybackState.STATE_STOPPED)
                return
            }
            index++
        }
    }

    private suspend fun speak(s: Sentence, generation: Int) {
        when (_state.value.engineKey) {
            TtsEngines.EDGE -> speakEdge(index, generation)
            else -> speakSystem(s.text)
        }
    }

    private fun startEdgeJob(i: Int, block: suspend () -> Unit) {
        edgeJobs[i]?.cancel()
        lateinit var job: Job
        job = scope.launch(Dispatchers.IO) {
            try {
                block()
            } finally {
                if (edgeJobs[i] === job) edgeJobs.remove(i)
            }
        }
        edgeJobs[i] = job
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
        playFile(f, generation)
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
                text = s.text,
                voice = edgeVoiceId(),
                ratePercent = ratePercent(),
                pitchPercent = pitchPercent(),
            )
            f.writeBytes(audio.mp3)
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
        suspendCancellableCoroutine { cont ->
            // QuickNovel EdgeTtsPlayer: always tear down previous player before start.
            releasePlayer()
            if (generation != playGeneration || !cont.isActive) {
                if (cont.isActive) cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            val mp = MediaPlayer()
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
                mp.setDataSource(file.absolutePath)
                mp.prepare()
                // Cancel during prepare must not start (stale start caused doubled audio).
                if (generation != playGeneration || !cont.isActive) {
                    releasePlayer()
                    if (cont.isActive) cont.resume(Unit)
                    return@suspendCancellableCoroutine
                }
                mp.playbackParams = mp.playbackParams.setSpeed(_state.value.speed)
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
        if (systemTts != null && systemReady && boundEnginePackage == enginePackage) {
            return systemTts!!
        }
        systemTts?.shutdown()
        systemTts = null
        systemReady = false
        return suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    systemTts = tts
                    systemReady = true
                    boundEnginePackage = enginePackage
                    if (_state.value.engineKey != TtsEngines.EDGE) {
                        val voices = systemVoices(tts!!)
                        val voiceId = when {
                            voices.any { it.id == _state.value.voiceId } -> _state.value.voiceId
                            else -> voices.firstOrNull()?.id.orEmpty()
                        }
                        _state.update { it.copy(voices = voices, voiceId = voiceId) }
                    }
                    if (cont.isActive) cont.resume(tts!!)
                } else if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("System TTS init failed"))
                }
            }
            tts = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
        }
    }

    private fun edgeVoiceId(): String {
        val id = _state.value.voiceId
        return if (id.isNotBlank() && EdgeVoices.any { it.id == id }) id else EdgeVoices.first().id
    }

    private fun cacheFile(i: Int): File {
        val key = "${_state.value.engineKey}_${edgeVoiceId()}_${ratePercent()}_${pitchPercent()}"
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(cacheDir, "s${i}_$key.mp3")
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
        cacheDir.listFiles()?.forEach { file ->
            val match = SENTENCE_CACHE_NAME.matchEntire(file.name) ?: return@forEach
            val i = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (i !in window) {
                edgeJobs[i]?.cancel()
                edgeJobs.remove(i)
                file.delete()
            }
        }
        _state.update {
            it.copy(
                readySentenceIndices = it.readySentenceIndices.filterTo(linkedSetOf()) { i -> i in window },
                generatingSentenceIndices = it.generatingSentenceIndices.filterTo(linkedSetOf()) { i ->
                    i in window
                },
            )
        }
    }

    private fun clearEdgeCache() {
        edgeJobs.values.forEach { it.cancel() }
        edgeJobs.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
        clearAudioStatus()
    }

    private fun ratePercent(): Int = ((_state.value.speed - 1f) * 100f).toInt()

    private fun pitchPercent(): Int = ((_state.value.pitch - 1f) * 50f).toInt()

    companion object {
        private val SENTENCE_CACHE_NAME = Regex("""^s(\d+)_.*\.mp3$""")

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
