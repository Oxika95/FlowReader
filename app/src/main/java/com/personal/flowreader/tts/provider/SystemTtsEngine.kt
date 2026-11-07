package com.personal.flowreader.tts.provider

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The one Android [TextToSpeech] engine of the app. Live speech ([com.personal.flowreader.tts.TtsController])
 * and clip synthesis ([SystemTtsProvider]) both go through [use], so there is one lock, one
 * listener and one utterance at a time, and the engine is never shut down or rebound while an
 * utterance runs.
 *
 * [onBound] runs once per newly bound engine, before the first [use] block.
 */
class SystemTtsEngine(
    private val context: Context,
    private val onBound: (TextToSpeech) -> Unit = {},
) {
    private val slot = EngineSlot<TextToSpeech>(bind = this::create, release = { it.shutdown() })

    /** Runs [block] with the engine for [enginePackage] (null = system default) under the engine lock. */
    suspend fun <T> use(enginePackage: String?, rebind: Boolean = false, block: suspend (TextToSpeech) -> T): T =
        slot.use(enginePackage, rebind, block)

    /** Binds the engine without doing anything with it. */
    suspend fun ensure(enginePackage: String?, rebind: Boolean = false) {
        use(enginePackage, rebind) { }
    }

    /** The bound engine for [enginePackage], or null. Never blocks. */
    fun peek(enginePackage: String?): TextToSpeech? = slot.peek(enginePackage)

    /** Stops the running utterance, if any. Never blocks; safe from any thread. */
    fun stop() {
        slot.current()?.stop()
    }

    /** Stops speech and releases the engine, after the current [use] ends when one is running. */
    fun shutdown() {
        stop()
        slot.shutdown()
    }

    /**
     * Runs one utterance and suspends until it ends. [start] must queue it under the given id
     * (`speak` or `synthesizeToFile`). Call only inside [use]. Cancelling the caller, or an
     * outside [stop], ends the utterance and cancels the caller. [onRange] gets the spoken
     * character range of the text, when the engine reports it.
     */
    suspend fun utterance(
        tts: TextToSpeech,
        onRange: ((start: Int, end: Int) -> Unit)? = null,
        start: (utteranceId: String) -> Int,
    ): Unit = suspendCancellableCoroutine { cont ->
        val id = UUID.randomUUID().toString()
        fun fail(message: String) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(message))
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == id && cont.isActive) cont.resume(Unit)
            }

            @Deprecated("deprecated")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id) fail("System TTS error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id) fail("System TTS error code=$errorCode")
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == id && cont.isActive) cont.cancel()
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                if (utteranceId == id) onRange?.invoke(start, end)
            }
        })
        if (start(id) != TextToSpeech.SUCCESS) fail("System TTS unavailable")
        cont.invokeOnCancellation { tts.stop() }
    }

    private suspend fun create(enginePackage: String?): TextToSpeech {
        val engine = suspendCancellableCoroutine { cont ->
            var created: TextToSpeech? = null
            val listener = TextToSpeech.OnInitListener { status ->
                val ready = created
                if (status != TextToSpeech.SUCCESS || ready == null) {
                    ready?.shutdown()
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("System TTS init failed"))
                } else if (cont.isActive) {
                    cont.resume(ready)
                } else {
                    ready.shutdown()
                }
            }
            created = if (enginePackage.isNullOrBlank()) {
                TextToSpeech(context, listener)
            } else {
                TextToSpeech(context, listener, enginePackage)
            }
            cont.invokeOnCancellation { created?.shutdown() }
        }
        engine.language = Locale.getDefault()
        onBound(engine)
        return engine
    }
}
