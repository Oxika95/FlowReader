package com.personal.flowreader.tts

import com.personal.flowreader.data.TtsVoiceOption
import org.json.JSONArray

/** One Edge voice from the `voices/list` endpoint (or the bundled snapshot of it). */
data class EdgeVoice(
    /** ShortName, e.g. `en-US-AndrewMultilingualNeural`; what SSML `voice name` takes. */
    val id: String,
    val locale: String,
    /** "English" from LocaleName "English (United States)". */
    val language: String,
    /** "United States"; blank when LocaleName has no qualifier. */
    val region: String,
    val gender: String,
) {
    /** "Andrew Multilingual" from `en-US-AndrewMultilingualNeural`. */
    val name: String
        get() = id.removePrefix("$locale-")
            .removeSuffix("Neural")
            .replace("Multilingual", " Multilingual")
            .trim()
            .ifBlank { id }

    val label: String
        get() = listOf(name, region, gender).filter { it.isNotBlank() }.joinToString(" · ")
}

object EdgeVoiceCatalog {
    private const val BUNDLED = "/tts/edge_voices.json"

    /** Region sort order within a language; unlisted locales follow alphabetically. */
    private val PINNED_LOCALES = listOf("en-US", "en-GB")

    const val DEFAULT_LANGUAGE = "English"

    /** Parses the Edge `voices/list` JSON array; malformed entries are skipped. */
    fun parse(json: String): List<EdgeVoice> {
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val id = o.optString("ShortName").trim()
                val locale = o.optString("Locale").trim()
                if (id.isEmpty() || locale.isEmpty()) continue
                val localeName = o.optString("LocaleName").trim()
                    .ifEmpty { o.optString("FriendlyName").substringAfter(" - ", "").trim() }
                    .ifEmpty { locale }
                val open = localeName.indexOf(" (")
                val language = if (open > 0) localeName.substring(0, open) else localeName
                val region = if (open > 0) localeName.substring(open + 2).removeSuffix(")") else ""
                add(EdgeVoice(id, locale, language, region, o.optString("Gender").trim()))
            }
        }.distinctBy { it.id }
    }

    fun bundled(): List<EdgeVoice> {
        val text = EdgeVoiceCatalog::class.java.getResourceAsStream(BUNDLED)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: return emptyList()
        return parse(text)
    }

    /** Picker rows: languages A–Z, pinned regions first, then region and name. */
    fun options(voices: List<EdgeVoice>): List<TtsVoiceOption> =
        voices
            .sortedWith(
                compareBy<EdgeVoice> { it.language }
                    .thenBy { PINNED_LOCALES.indexOf(it.locale).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                    .thenBy { it.region }
                    .thenBy { it.name },
            )
            .map { TtsVoiceOption(it.id, it.label, it.language) }

    /** SSML `xml:lang` for [voiceId]: its locale's language-region part. */
    fun langOf(voiceId: String, voices: List<EdgeVoice>): String {
        val locale = voices.firstOrNull { it.id == voiceId }?.locale
            ?: voiceId.split('-').take(2).joinToString("-")
        return locale.split('-').take(2).joinToString("-").ifBlank { "en-US" }
    }
}
