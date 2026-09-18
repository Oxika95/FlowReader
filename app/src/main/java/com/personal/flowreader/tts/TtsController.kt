package com.personal.flowreader.tts

import android.content.Context
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.Sentence
import com.personal.flowreader.data.SentenceSplitter
import com.personal.flowreader.data.TtsEngineKind
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class TtsUiState(
    val playing: Boolean = false,
    val following: Boolean = true,
    val engine: TtsEngineKind = TtsEngineKind.System,
    val speed: Float = 1.0f,
    val sentence: Sentence? = null,
    val snippet: String = "",
    val error: String? = null,
)

class TtsController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val edge = EdgeTtsClient()
    private val cacheDir = File(context.cacheDir, "tts").apply { mkdirs() }

    private var doc: BookDoc? = null
    private var sentences: List<Sentence> = emptyList()
    private var index = 0
    private var playJob: Job? = null
    private var player: MediaPlayer? = null
    private var systemTts: TextToSpeech? = null
    private var systemReady = false
    private var session: MediaSession? = null

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state

    fun attach(book: BookDoc, start: Locus) {
        stop()
        doc = book
        sentences = SentenceSplitter.split(book)
        index = SentenceSplitter.indexAt(sentences, start)
        val s = sentences.getOrNull(index)
        _state.update {
            it.copy(
                playing = false,
                following = true,
                sentence = s,
                snippet = s?.text.orEmpty(),
                error = null,
            )
        }
    }

    fun play() {
        if (sentences.isEmpty()) return
        ensureSession()
        _state.update { it.copy(playing = true, following = true, error = null) }
        setSessionState(PlaybackState.STATE_PLAYING)
        playJob?.cancel()
        playJob = scope.launch { loop() }
    }

    fun pause() {
        playJob?.cancel()
        player?.runCatching { if (isPlaying) pause() }
        systemTts?.stop()
        _state.update { it.copy(playing = false) }
        setSessionState(PlaybackState.STATE_PAUSED)
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        player?.runCatching { reset(); release() }
        player = null
        systemTts?.stop()
        _state.update { it.copy(playing = false) }
        setSessionState(PlaybackState.STATE_STOPPED)
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
        _state.update { it.copy(speed = speed) }
        if (_state.value.playing) restartLoop()
    }

    fun setEngine(kind: TtsEngineKind) {
        _state.update { it.copy(engine = kind) }
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
        stop()
        systemTts?.shutdown()
        systemTts = null
        session?.release()
        session = null
    }

    private fun ensureSession() {
        if (session != null) return
        session = MediaSession(context, "flow-tts").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
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
        playJob?.cancel()
        playJob = scope.launch { loop() }
    }

    private fun publishSentence() {
        val s = sentences.getOrNull(index)
        _state.update { it.copy(sentence = s, snippet = s?.text.orEmpty()) }
    }

    private suspend fun loop() {
        while (scope.isActive && _state.value.playing) {
            val s = sentences.getOrNull(index) ?: break
            publishSentence()
            val lookahead = scope.launch(Dispatchers.IO) {
                prefetch(index + 1)
                prefetch(index + 2)
            }
            try {
                speak(s)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                lookahead.cancel()
                _state.update { it.copy(playing = false, error = t.message ?: "TTS failed") }
                setSessionState(PlaybackState.STATE_STOPPED)
                return
            }
            lookahead.cancel()
            if (index >= sentences.lastIndex) {
                _state.update { it.copy(playing = false) }
                setSessionState(PlaybackState.STATE_STOPPED)
                return
            }
            index++
        }
    }

    private suspend fun speak(s: Sentence) {
        when (_state.value.engine) {
            TtsEngineKind.System -> speakSystem(s.text)
            TtsEngineKind.Edge -> speakEdge(s.text, index)
        }
    }

    private suspend fun prefetch(i: Int) {
        if (_state.value.engine != TtsEngineKind.Edge) return
        val s = sentences.getOrNull(i) ?: return
        val f = cacheFile(i)
        if (f.exists() && f.length() > 0) return
        try {
            val audio = edge.synthesize(s.text, ratePercent = ratePercent())
            f.writeBytes(audio.mp3)
        } catch (_: Throwable) {
            // Lookahead is best-effort; the play loop surfaces the real error.
        }
    }

    private suspend fun speakEdge(text: String, i: Int) {
        val f = cacheFile(i)
        if (!f.exists() || f.length() == 0L) {
            val audio = edge.synthesize(text, ratePercent = ratePercent())
            f.writeBytes(audio.mp3)
        }
        playFile(f)
    }

    private suspend fun playFile(file: File) = suspendCancellableCoroutine { cont ->
        player?.runCatching { reset(); release() }
        val mp = MediaPlayer()
        player = mp
        mp.setDataSource(file.absolutePath)
        mp.setOnCompletionListener {
            if (cont.isActive) cont.resume(Unit)
        }
        mp.setOnErrorListener { _, _, _ ->
            if (cont.isActive) cont.resumeWithException(IllegalStateException("Unable to play audio"))
            true
        }
        cont.invokeOnCancellation {
            mp.runCatching { stop(); release() }
        }
        mp.prepare()
        mp.playbackParams = mp.playbackParams.setSpeed(_state.value.speed)
        mp.start()
    }

    private suspend fun speakSystem(text: String) {
        val tts = ensureSystem()
        tts.setSpeechRate(_state.value.speed)
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

    private suspend fun ensureSystem(): TextToSpeech {
        systemTts?.let { if (systemReady) return it }
        return suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    systemTts = tts
                    systemReady = true
                    if (cont.isActive) cont.resume(tts!!)
                } else if (cont.isActive) {
                    cont.resumeWithException(IllegalStateException("System TTS init failed"))
                }
            }
        }
    }

    private fun cacheFile(i: Int) = File(cacheDir, "s$i.mp3")

    private fun ratePercent(): Int = ((_state.value.speed - 1f) * 100f).toInt()
}
