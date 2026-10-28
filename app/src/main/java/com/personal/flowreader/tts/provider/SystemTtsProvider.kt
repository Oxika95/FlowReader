package com.personal.flowreader.tts.provider

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.tts.VoiceGender
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android [TextToSpeech] engine that writes clips to a temp WAV for the host cache /
 * [com.personal.flowreader.tts.TtsAudioEngine] path (same rail / prefetch as plugins).
 */
class SystemTtsProvider(
    override val id: String,
    override val label: String,
    private val tempDir: () -> File,
    private val obtainEngine: suspend (enginePackage: String?) -> TextToSpeech,
) : TtsProvider {
    override val capabilities: Set<TtsCapability> = setOf(
        TtsCapability.ClipAudio,
        TtsCapability.Prefetch,
    )

    private val enginePackage: String?
        get() = if (id == TtsEngineIds.SYSTEM_DEFAULT) null else id

    override suspend fun listVoices(): List<TtsVoiceOption> {
        val tts = obtainEngine(enginePackage)
        val voices = tts.voices?.toList().orEmpty()
        if (voices.isEmpty()) return listOf(TtsVoiceOption("default", "Default"))
        return voices
            .sortedWith(
                compareBy<android.speech.tts.Voice> { it.locale.toLanguageTag() }
                    .thenBy { it.name },
            )
            .map { v ->
                TtsVoiceOption(
                    id = v.name,
                    label = v.name,
                    language = v.locale.displayLanguage.ifBlank { v.locale.toLanguageTag() },
                    gender = VoiceGender.resolve("", v.name, v.name),
                )
            }
    }

    override suspend fun synthesize(request: TtsSynthRequest): TtsClipAudio {
        val tts = obtainEngine(enginePackage)
        val rate = 1f + request.ratePercent / 100f
        val pitch = 1f + request.pitchPercent / 50f
        tts.setSpeechRate(rate.coerceIn(0.5f, 2.5f))
        tts.setPitch(pitch.coerceIn(0.5f, 2f))
        if (request.voice.isNotBlank() && request.voice != "default") {
            tts.voices?.firstOrNull { it.name == request.voice }?.let { tts.voice = it }
        } else if (request.lang.isNotBlank()) {
            runCatching { tts.language = Locale.forLanguageTag(request.lang) }
        }
        val dir = tempDir().apply { mkdirs() }
        val out = File(dir, "sys-${UUID.randomUUID()}.wav")
        return try {
            synthesizeToFile(tts, request.text, out)
            val bytes = out.readBytes()
            require(bytes.isNotEmpty()) { "System TTS returned empty audio" }
            TtsClipAudio(bytes, emptyList())
        } finally {
            runCatching { out.delete() }
        }
    }

    private suspend fun synthesizeToFile(tts: TextToSpeech, text: String, file: File): Unit =
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
                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == id && cont.isActive) {
                        cont.resumeWithException(
                            IllegalStateException("System TTS error code=$errorCode"),
                        )
                    }
                }
            })
            val code = tts.synthesizeToFile(text, null, file, id)
            if (code != TextToSpeech.SUCCESS && cont.isActive) {
                cont.resumeWithException(IllegalStateException("System TTS unavailable"))
            }
            cont.invokeOnCancellation { tts.stop() }
        }
}
