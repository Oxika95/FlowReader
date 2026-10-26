package com.personal.flowreader.tts

/**
 * Infer `"male"` / `"female"` from voice id/label when engines embed gender in the name
 * (e.g. Neko `expr-voice-2-f`, Korokoro `f1`…`m5`). Prefer an explicit API `gender` when present.
 */
object VoiceGender {
    private val TRAILING_FM = Regex("""(?i)[-_]([fm])(?=\s*(?:\(|$|\.))""")
    private val TOKEN_FM_DIGIT = Regex("""(?i)(?:^|[^a-z0-9])([fm])([1-9])(?:[^a-z0-9]|$)""")
    private val WORD_FEMALE = Regex("""(?i)(?:^|[^a-z])(female|woman)(?:[^a-z]|$)""")
    private val WORD_MALE = Regex("""(?i)(?:^|[^a-z])(male|man)(?:[^a-z]|$)""")

    /** Normalize an explicit gender string; blank if not male/female. */
    fun normalize(raw: String?): String = when (raw?.trim()?.lowercase()) {
        "male" -> "male"
        "female" -> "female"
        else -> ""
    }

    /**
     * Prefer [explicit] when it is male/female; otherwise scan [id] and [label].
     * Returns `"male"`, `"female"`, or `""`.
     */
    fun resolve(explicit: String?, id: String, label: String = ""): String {
        val fromApi = normalize(explicit)
        if (fromApi.isNotEmpty()) return fromApi
        return infer(id, label)
    }

    fun infer(id: String, label: String = ""): String {
        val hay = listOf(id, label)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()
        if (hay.isBlank()) return ""

        TRAILING_FM.find(hay)?.groupValues?.getOrNull(1)?.let { letter ->
            return if (letter.equals("f", ignoreCase = true)) "female" else "male"
        }
        TOKEN_FM_DIGIT.find(hay)?.groupValues?.getOrNull(1)?.let { letter ->
            return if (letter.equals("f", ignoreCase = true)) "female" else "male"
        }
        if (WORD_FEMALE.containsMatchIn(hay)) return "female"
        if (WORD_MALE.containsMatchIn(hay)) return "male"
        return ""
    }
}
