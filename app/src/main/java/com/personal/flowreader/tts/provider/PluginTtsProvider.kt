package com.personal.flowreader.tts.provider

import android.net.Network
import android.util.Base64
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.tts.EdgeWordBoundary
import com.personal.flowreader.tts.VoiceGender
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** TTS engine backed by a `kind: "tts"` QuickJS plugin. */
class PluginTtsProvider(
    private val manager: PluginManager,
    private val manifest: PluginManifest,
) : TtsProvider {
    override val id: String = TtsEngineIds.plugin(manifest.id)
    override val label: String = manifest.name
    override val capabilities: Set<TtsCapability> = buildSet {
        if (manifest.has(PluginCapability.ClipAudio)) add(TtsCapability.ClipAudio)
        if (manifest.has(PluginCapability.WordBoundaries)) add(TtsCapability.WordBoundaries)
        if (manifest.has(PluginCapability.Prefetch)) add(TtsCapability.Prefetch)
        if (manifest.has(PluginCapability.NetworkLanes)) add(TtsCapability.NetworkLanes)
    }

    override suspend fun listVoices(): List<TtsVoiceOption> {
        val raw = manager.runtime(manifest.id).call("listVoices")
        val arr = raw as? JSONArray ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val voiceId = o.optString("id").trim()
                if (voiceId.isEmpty()) continue
                val label = o.optString("label").ifBlank { voiceId }
                add(
                    TtsVoiceOption(
                        id = voiceId,
                        label = label,
                        language = o.optString("language"),
                        gender = VoiceGender.resolve(
                            o.optString("gender"),
                            voiceId,
                            label,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * @param raceSlot QuickJS slot for staggered races (0 = primary).
     * @param network optional Android network to bind the WebSocket (lane).
     */
    suspend fun synthesize(
        request: TtsSynthRequest,
        raceSlot: Int = 0,
        network: Network? = null,
    ): TtsClipAudio {
        val runtime = manager.runtimeForRace(manifest.id, raceSlot)
        if (network != null) runtime.ws.bindNetwork(network)
        val args = JSONArray().put(
            JSONObject()
                .put("text", request.text)
                .put("voice", request.voice)
                .put("lang", request.lang)
                .put("ratePercent", request.ratePercent)
                .put("pitchPercent", request.pitchPercent),
        )
        val raw = runtime.call("synthesize", args) as? JSONObject
            ?: throw IllegalStateException("${manifest.name} returned no audio")
        return clipFromResult(raw, manifest.name)
    }

    override suspend fun synthesize(request: TtsSynthRequest): TtsClipAudio =
        synthesize(request, raceSlot = 0, network = null)

    companion object {
        fun clipFromResult(raw: JSONObject, pluginName: String): TtsClipAudio {
            val path = raw.optString("audioPath").trim()
            val mp3 = if (path.isNotEmpty()) {
                val file = File(path)
                try {
                    require(file.isFile && file.length() > 0L) { "$pluginName returned empty audio" }
                    file.readBytes()
                } finally {
                    runCatching { file.delete() }
                }
            } else {
                val b64 = raw.optString("mp3Base64")
                require(b64.isNotBlank()) { "$pluginName returned empty audio" }
                Base64.decode(b64, Base64.DEFAULT)
            }
            val boundaries = ArrayList<EdgeWordBoundary>()
            val meta = raw.optJSONArray("boundaries")
            if (meta != null) {
                for (i in 0 until meta.length()) {
                    val o = meta.optJSONObject(i) ?: continue
                    val text = o.optString("text")
                    if (text.isBlank()) continue
                    boundaries += EdgeWordBoundary(
                        offset = o.optLong("offset"),
                        duration = o.optLong("duration"),
                        text = text,
                    )
                }
            }
            return TtsClipAudio(
                mp3 = mp3,
                boundaries = boundaries,
                connectId = raw.optString("connectId").trim(),
            )
        }
    }
}
