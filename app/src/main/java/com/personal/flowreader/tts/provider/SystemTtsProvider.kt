package com.personal.flowreader.tts.provider

import com.personal.flowreader.data.TtsVoiceOption

/**
 * Placeholder for Android [android.speech.tts.TextToSpeech] engines. Voices and speech are
 * still driven by [com.personal.flowreader.tts.TtsController] session APIs; this type exists so
 * the registry and Settings list are provider-shaped.
 */
class SystemTtsProvider(
    override val id: String,
    override val label: String,
) : TtsProvider {
    override val capabilities: Set<TtsCapability> = emptySet()

    override suspend fun listVoices(): List<TtsVoiceOption> =
        listOf(TtsVoiceOption("default", "Default"))
}
