package com.personal.flowreader.tts.provider

import android.util.Base64
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.plugin.PluginManager
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.tts.EdgeWordBoundary
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
                val gender = o.optString("gender").trim().lowercase().let { g ->
                    when (g) {
                        "male", "female" -> g
                        else -> ""
                    }
                }
                add(
                    TtsVoiceOption(
                        id = voiceId,
                        label = o.optString("label").ifBlank { voiceId },
                        language = o.optString("language"),
                        gender = gender,
                    ),
                )
            }
        }
    }

    override suspend fun synthesize(request: TtsSynthRequest): TtsClipAudio {
        val args = JSONArray().put(
            JSONObject()
                .put("text", request.text)
                .put("voice", request.voice)
                .put("lang", request.lang)
                .put("ratePercent", request.ratePercent)
                .put("pitchPercent", request.pitchPercent),
        )
        val raw = manager.runtime(manifest.id).call("synthesize", args) as? JSONObject
            ?: throw IllegalStateException("${manifest.name} returned no audio")
        val b64 = raw.optString("mp3Base64")
        require(b64.isNotBlank()) { "${manifest.name} returned empty audio" }
        val mp3 = Base64.decode(b64, Base64.DEFAULT)
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
        return TtsClipAudio(mp3, boundaries)
    }
}
