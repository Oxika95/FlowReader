package com.personal.flowreader.tts

import com.personal.flowreader.data.TtsVoiceOption
import java.util.Locale

/**
 * Default voice / language when the saved id is missing from an engine catalog.
 * System TTS lists are ordered by locale tag, so [List.first] would be Arabic on Google —
 * prefer the device language instead.
 */
object TtsVoicePick {
    /** Display language label for [locale], matching how system voices set [TtsVoiceOption.language]. */
    fun displayLanguage(locale: Locale = Locale.getDefault()): String =
        locale.displayLanguage.ifBlank { locale.toLanguageTag() }

    /**
     * Keep [preferred] when it is in [voices]; otherwise a voice for the device language,
     * else the first catalog entry.
     */
    fun coerce(
        preferred: String,
        voices: List<TtsVoiceOption>,
        pluginDefault: String? = null,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (preferred.isNotBlank() && voices.any { it.id == preferred }) return preferred
        if (!pluginDefault.isNullOrBlank() && voices.any { it.id == pluginDefault }) {
            return pluginDefault
        }
        return bestForSystem(voices, locale)?.id.orEmpty()
    }

    /** Language filter default: device language when present in [languages], else first sorted. */
    fun defaultLanguage(
        languages: List<String>,
        locale: Locale = Locale.getDefault(),
    ): String {
        if (languages.isEmpty()) return ""
        val system = displayLanguage(locale)
        if (system in languages) return system
        return languages.first()
    }

    fun bestForSystem(
        voices: List<TtsVoiceOption>,
        locale: Locale = Locale.getDefault(),
    ): TtsVoiceOption? {
        if (voices.isEmpty()) return null
        val display = displayLanguage(locale)
        val tag = locale.toLanguageTag()
        val lang = locale.language
        val pool = voices.filter { it.language.equals(display, ignoreCase = true) }
            .ifEmpty {
                voices.filter { voiceMatchesLang(it.id, lang) }
            }
            .ifEmpty { return voices.firstOrNull() }
        return pool.firstOrNull { voiceMatchesTag(it.id, tag) }
            ?: pool.firstOrNull { voiceMatchesLang(it.id, lang) }
            ?: pool.firstOrNull()
    }

    private fun voiceMatchesTag(id: String, tag: String): Boolean {
        if (tag.isBlank()) return false
        return id.startsWith(tag, ignoreCase = true) ||
            id.startsWith(tag.replace('-', '_'), ignoreCase = true)
    }

    private fun voiceMatchesLang(id: String, lang: String): Boolean {
        if (lang.isBlank()) return false
        val lower = id.lowercase()
        val l = lang.lowercase()
        return lower.startsWith("$l-") ||
            lower.startsWith("${l}_") ||
            lower.startsWith("$l.") ||
            lower == l
    }
}
