package com.personal.flowreader.tts.provider

import android.speech.tts.TextToSpeech
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.tts.VoiceGender
import java.io.File
import java.util.Locale
import java.util.UUID

/**
 * Android [TextToSpeech] engine that writes clips to a temp WAV for the host cache /
 * [com.personal.flowreader.tts.TtsAudioEngine] path (same rail / prefetch as plugins).
 *
 * The engine is exclusive and shared with live speech through [SystemTtsEngine]. Prefetch may
 * queue multiple [synthesize] callers; they run one after another, never overlapping.
 */
class SystemTtsProvider(
    override val id: String,
    override val label: String,
    private val tempDir: () -> File,
    private val engine: SystemTtsEngine,
) : TtsProvider {
    override val capabilities: Set<TtsCapability> = setOf(
        TtsCapability.ClipAudio,
        TtsCapability.Prefetch,
    )

    private val enginePackage: String?
        get() = if (id == TtsEngineIds.SYSTEM_DEFAULT) null else id

    override suspend fun listVoices(): List<TtsVoiceOption> = engine.use(enginePackage) { tts ->
        val voices = tts.voices?.toList().orEmpty()
        if (voices.isEmpty()) return@use listOf(TtsVoiceOption("default", "Default"))
        voices
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

    override suspend fun synthesize(request: TtsSynthRequest): TtsClipAudio =
        engine.use(enginePackage) { tts ->
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
            try {
                engine.utterance(tts) { utteranceId -> tts.synthesizeToFile(request.text, null, out, utteranceId) }
                val bytes = out.readBytes()
                require(bytes.isNotEmpty()) { "System TTS returned empty audio" }
                TtsClipAudio(bytes, emptyList())
            } finally {
                runCatching { out.delete() }
            }
        }
}
