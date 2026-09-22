package com.personal.flowreader.data

import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

enum class FilterMatchType {
    CaseSensitive,
    CaseInsensitive,
    RegEx,
    ;

    val label: String
        get() = when (this) {
            CaseSensitive -> "Case Sensitive"
            CaseInsensitive -> "Case Insensitive"
            RegEx -> "RegEx"
        }
}

enum class FilterScope {
    Global,
    Groups,
    Local,
    ;

    val label: String
        get() = when (this) {
            Global -> "Global"
            Groups -> "Groups"
            Local -> "Local"
        }
}

data class FilterRule(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val enabled: Boolean = true,
    val matchType: FilterMatchType = FilterMatchType.CaseInsensitive,
    val wholeWords: Boolean = true,
    /** When true, apply only when synthesizing speech — leave on-screen text unchanged. */
    val ttsOnly: Boolean = false,
    val pattern: String = "",
    val replacement: String = "",
    val order: Int = 0,
)

data class FilterApplyResult(
    val text: String,
    val replacedRanges: List<IntRange> = emptyList(),
)

data class FilteredBookDoc(
    val doc: BookDoc,
    /** Block id → ranges in filtered text that came from non-blank replacements. */
    val replacedRangesByBlockId: Map<String, List<IntRange>> = emptyMap(),
)

object TextFilters {
    fun apply(text: String, rules: List<FilterRule>): FilterApplyResult {
        val enabled = rules
            .filter { it.enabled && it.pattern.isNotEmpty() }
            .sortedBy { it.order }
        if (enabled.isEmpty()) return FilterApplyResult(text)

        var current = text
        var flags = BooleanArray(text.length)
        for (rule in enabled) {
            val next = applyRule(current, flags, rule) ?: continue
            current = next.first
            flags = next.second
        }
        return FilterApplyResult(current, flagsToRanges(flags))
    }

    /** On-screen book text — skips [FilterRule.ttsOnly] rules. */
    fun applyVisual(doc: BookDoc, rules: List<FilterRule>): FilteredBookDoc =
        apply(doc, rules.filter { !it.ttsOnly })

    /** Speech-only transforms for a sentence (or any spoken snippet). */
    fun applySpeech(text: String, rules: List<FilterRule>): String {
        val speech = rules.filter { it.ttsOnly && it.enabled && it.pattern.isNotEmpty() }
        if (speech.isEmpty()) return text
        return apply(text, speech).text
    }

    fun apply(doc: BookDoc, rules: List<FilterRule>): FilteredBookDoc {
        if (rules.none { it.enabled && it.pattern.isNotEmpty() }) {
            return FilteredBookDoc(doc)
        }
        val ranges = LinkedHashMap<String, List<IntRange>>()
        val chapters = doc.chapters.map { chapter ->
            chapter.copy(
                blocks = chapter.blocks.map { block ->
                    val result = apply(block.text, rules)
                    if (result.replacedRanges.isNotEmpty()) {
                        ranges[block.id] = result.replacedRanges
                    }
                    if (result.text == block.text) block else block.copy(text = result.text)
                },
            )
        }
        return FilteredBookDoc(BookDoc(doc.title, chapters), ranges)
    }

    /** Global, then Groups, then Local (each sorted by [FilterRule.order]). */
    fun merge(
        global: List<FilterRule>,
        groups: List<FilterRule>,
        local: List<FilterRule>,
    ): List<FilterRule> =
        global.sortedBy { it.order } + groups.sortedBy { it.order } + local.sortedBy { it.order }

    fun validatePattern(rule: FilterRule): String? {
        if (rule.pattern.isEmpty()) return null
        if (rule.matchType != FilterMatchType.RegEx) return null
        return try {
            Pattern.compile(rule.pattern)
            null
        } catch (e: PatternSyntaxException) {
            e.description ?: "Invalid regular expression"
        }
    }

    fun encodeRules(rules: List<FilterRule>): String {
        val body = rules.sortedBy { it.order }.joinToString(",") { rule ->
            buildString {
                append('{')
                appendJsonField("id", rule.id); append(',')
                appendJsonField("title", rule.title); append(',')
                append("\"enabled\":").append(rule.enabled).append(',')
                appendJsonField("matchType", rule.matchType.name); append(',')
                append("\"wholeWords\":").append(rule.wholeWords).append(',')
                append("\"ttsOnly\":").append(rule.ttsOnly).append(',')
                appendJsonField("pattern", rule.pattern); append(',')
                appendJsonField("replacement", rule.replacement); append(',')
                append("\"order\":").append(rule.order)
                append('}')
            }
        }
        return "[$body]"
    }

    fun decodeRules(json: String?): List<FilterRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val objects = splitJsonObjects(json.trim())
            objects.mapIndexed { index, obj ->
                FilterRule(
                    id = readJsonString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    title = readJsonString(obj, "title"),
                    enabled = readJsonBoolean(obj, "enabled", true),
                    matchType = runCatching {
                        FilterMatchType.valueOf(
                            readJsonString(obj, "matchType").ifBlank {
                                FilterMatchType.CaseInsensitive.name
                            },
                        )
                    }.getOrDefault(FilterMatchType.CaseInsensitive),
                    wholeWords = readJsonBoolean(obj, "wholeWords", true),
                    ttsOnly = readJsonBoolean(obj, "ttsOnly", false),
                    pattern = readJsonString(obj, "pattern"),
                    replacement = readJsonString(obj, "replacement"),
                    order = readJsonInt(obj, "order", index),
                )
            }.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun StringBuilder.appendJsonField(key: String, value: String) {
        append('"').append(key).append('"').append(':')
        append('"').append(escapeJson(value)).append('"')
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun splitJsonObjects(json: String): List<String> {
        if (!json.startsWith('[') || !json.endsWith(']')) return emptyList()
        val inner = json.substring(1, json.length - 1).trim()
        if (inner.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        var depth = 0
        var inString = false
        var escape = false
        var start = -1
        for (i in inner.indices) {
            val ch = inner[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out.add(inner.substring(start, i + 1))
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun readJsonString(obj: String, key: String): String {
        val keyToken = "\"$key\""
        val keyIndex = obj.indexOf(keyToken)
        if (keyIndex < 0) return ""
        var i = keyIndex + keyToken.length
        while (i < obj.length && (obj[i] == ' ' || obj[i] == ':' || obj[i] == '\n' || obj[i] == '\r' || obj[i] == '\t')) {
            i++
        }
        if (i >= obj.length || obj[i] != '"') return ""
        i++
        val sb = StringBuilder()
        var escape = false
        while (i < obj.length) {
            val ch = obj[i]
            when {
                escape -> {
                    when (ch) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        else -> sb.append(ch)
                    }
                    escape = false
                }
                ch == '\\' -> escape = true
                ch == '"' -> return sb.toString()
                else -> sb.append(ch)
            }
            i++
        }
        return sb.toString()
    }

    private fun readJsonBoolean(obj: String, key: String, default: Boolean): Boolean {
        val keyToken = "\"$key\""
        val keyIndex = obj.indexOf(keyToken)
        if (keyIndex < 0) return default
        var i = keyIndex + keyToken.length
        while (i < obj.length && (obj[i] == ' ' || obj[i] == ':' || obj[i] == '\n' || obj[i] == '\r' || obj[i] == '\t')) {
            i++
        }
        return when {
            obj.startsWith("true", i) -> true
            obj.startsWith("false", i) -> false
            else -> default
        }
    }

    private fun readJsonInt(obj: String, key: String, default: Int): Int {
        val keyToken = "\"$key\""
        val keyIndex = obj.indexOf(keyToken)
        if (keyIndex < 0) return default
        var i = keyIndex + keyToken.length
        while (i < obj.length && (obj[i] == ' ' || obj[i] == ':' || obj[i] == '\n' || obj[i] == '\r' || obj[i] == '\t')) {
            i++
        }
        val start = i
        if (i < obj.length && obj[i] == '-') i++
        while (i < obj.length && obj[i].isDigit()) i++
        if (start == i || (i == start + 1 && obj[start] == '-')) return default
        return obj.substring(start, i).toIntOrNull() ?: default
    }

    private fun applyRule(
        text: String,
        flags: BooleanArray,
        rule: FilterRule,
    ): Pair<String, BooleanArray>? {
        val pattern = compile(rule) ?: return null
        val matcher = pattern.matcher(text)
        if (!matcher.find()) return text to flags
        matcher.reset()

        val sb = StringBuilder(text.length)
        val newFlags = ArrayList<Boolean>(text.length)
        var last = 0
        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()
            if (start < last) continue
            for (i in last until start) {
                sb.append(text[i])
                newFlags.add(flags.getOrElse(i) { false })
            }
            val replacement = rule.replacement
            if (replacement.isNotEmpty()) {
                sb.append(replacement)
                repeat(replacement.length) { newFlags.add(true) }
            }
            last = end
        }
        for (i in last until text.length) {
            sb.append(text[i])
            newFlags.add(flags.getOrElse(i) { false })
        }
        return sb.toString() to newFlags.toBooleanArray()
    }

    private fun compile(rule: FilterRule): Pattern? {
        if (rule.pattern.isEmpty()) return null
        return try {
            when (rule.matchType) {
                FilterMatchType.RegEx -> Pattern.compile(rule.pattern)
                FilterMatchType.CaseSensitive -> {
                    val body = Pattern.quote(rule.pattern)
                    val src = if (rule.wholeWords) "\\b$body\\b" else body
                    Pattern.compile(src)
                }
                FilterMatchType.CaseInsensitive -> {
                    val body = Pattern.quote(rule.pattern)
                    val src = if (rule.wholeWords) "\\b$body\\b" else body
                    Pattern.compile(src, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
                }
            }
        } catch (_: PatternSyntaxException) {
            null
        }
    }

    private fun flagsToRanges(flags: BooleanArray): List<IntRange> {
        if (flags.isEmpty()) return emptyList()
        val ranges = ArrayList<IntRange>()
        var start = -1
        for (i in flags.indices) {
            if (flags[i]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                ranges.add(start until i)
                start = -1
            }
        }
        if (start >= 0) ranges.add(start until flags.size)
        return ranges
    }
}
