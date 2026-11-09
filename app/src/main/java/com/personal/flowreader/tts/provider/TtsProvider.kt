package com.personal.flowreader.tts.provider

import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.tts.EdgeWordBoundary

/** Feature flags advertised by a TTS engine (system or plugin). */
enum class TtsCapability(val key: String) {
    ClipAudio("clipAudio"),
    WordBoundaries("wordBoundaries"),
    Prefetch("prefetch"),
    NetworkLanes("networkLanes"),
    ;

    companion object {
        fun parse(raw: String): TtsCapability? = entries.firstOrNull { it.key == raw }
    }
}

data class TtsSynthRequest(
    val text: String,
    val voice: String,
    val lang: String,
    val ratePercent: Int = 0,
    val pitchPercent: Int = 0,
    /**
     * App-minted ownership id for this synth attempt (32 lowercase hex). Plugin TTS must echo
     * it; Edge uses it as ConnectionId. Empty for engines that ignore it (system WAV).
     */
    val requestId: String = "",
)

data class TtsClipAudio(
    val mp3: ByteArray,
    val boundaries: List<EdgeWordBoundary> = emptyList(),
    /** Echo of [TtsSynthRequest.requestId] when the plugin returns one; empty otherwise. */
    val requestId: String = "",
)

/**
 * One selectable TTS engine. System engines speak via Android TTS; clip-audio providers
 * return MP3 for the host cache and [com.personal.flowreader.tts.TtsAudioEngine].
 */
interface TtsProvider {
    val id: String
    val label: String
    val capabilities: Set<TtsCapability>

    fun has(cap: TtsCapability): Boolean = cap in capabilities

    suspend fun listVoices(): List<TtsVoiceOption>

    /** Only for [TtsCapability.ClipAudio] providers. */
    suspend fun synthesize(request: TtsSynthRequest): TtsClipAudio {
        throw UnsupportedOperationException("$id does not synthesize clips")
    }
}

object TtsEngineIds {
    const val SYSTEM_DEFAULT = "system"
    const val LEGACY_EDGE = "edge"

    fun plugin(pluginId: String): String = "tts:$pluginId"

    fun isPlugin(engineKey: String): Boolean = engineKey.startsWith("tts:")

    fun pluginIdOf(engineKey: String): String? =
        engineKey.takeIf { isPlugin(it) }?.removePrefix("tts:")
}
