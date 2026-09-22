package com.personal.flowreader.share

import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

object UrlDetector {
    private val URL = Pattern.compile(
        "(?i)\\b((?:https?://|www\\.)[^\\s<>\"'\\]\\)]+)",
    )

    fun firstUrl(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        if (looksLikeBareUrl(trimmed)) return normalize(trimmed)
        val m = URL.matcher(trimmed)
        if (!m.find()) return null
        return normalize(m.group(1) ?: return null)
    }

    fun hostOf(url: String): String? = runCatching {
        val normalized = normalize(url) ?: return null
        URI(normalized).host?.lowercase(Locale.US)?.trim('.')
    }.getOrNull()

    /** Path with leading slash, no trailing slash (except `/`), query/fragment stripped. */
    fun pathOf(url: String): String? = runCatching {
        val normalized = normalize(url) ?: return null
        val raw = URI(normalized).path.orEmpty()
        normalizePath(raw)
    }.getOrNull()

    fun normalizePath(raw: String?): String {
        if (raw.isNullOrBlank()) return "/"
        var p = raw.trim()
        if (!p.startsWith("/")) p = "/$p"
        while (p.length > 1 && p.endsWith("/")) p = p.dropLast(1)
        return p.lowercase(Locale.US)
    }

    private fun looksLikeBareUrl(s: String): Boolean {
        if (s.contains(Regex("\\s"))) return false
        return s.startsWith("http://", ignoreCase = true) ||
            s.startsWith("https://", ignoreCase = true) ||
            s.startsWith("www.", ignoreCase = true)
    }

    private fun normalize(raw: String): String? {
        var s = raw.trim().trimEnd('.', ',', ';', ')', ']', '"', '\'')
        if (s.isEmpty()) return null
        if (s.startsWith("www.", ignoreCase = true)) s = "https://$s"
        return s
    }
}

object ShareDomainRules {
    fun seed(): List<ShareDomainRule> = listOf(
        ShareDomainRule(
            id = "seed-royalroad",
            hostPattern = "royalroad.com",
            enabled = true,
            matchSubdomains = true,
            parseMode = ShareParseMode.Default,
            destination = ShareDestination.Plugin,
            pluginId = "royalroad",
            order = 0,
        ),
        ShareDomainRule(
            id = "seed-royalroadl",
            hostPattern = "royalroadl.com",
            enabled = true,
            matchSubdomains = true,
            parseMode = ShareParseMode.Default,
            destination = ShareDestination.Plugin,
            pluginId = "royalroad",
            order = 1,
        ),
    )

    fun match(host: String, rules: List<ShareDomainRule>): ShareDomainRule? =
        match(host = host, path = "/", rules = rules)

    fun matchUrl(url: String, rules: List<ShareDomainRule>): ShareDomainRule? {
        val host = UrlDetector.hostOf(url) ?: return null
        val path = UrlDetector.pathOf(url) ?: "/"
        return match(host = host, path = path, rules = rules)
    }

    /** First enabled rule in list order wins (list order is the user's manual sort). */
    fun match(host: String, path: String, rules: List<ShareDomainRule>): ShareDomainRule? {
        val h = host.lowercase(Locale.US).trim('.')
        val p = UrlDetector.normalizePath(path)
        return rules.firstOrNull { it.enabled && matches(h, p, it) }
    }

    fun matches(host: String, path: String, rule: ShareDomainRule): Boolean {
        if (rule.pathIsRegex) {
            return matchesRegex(host, path, rule.pathPattern ?: return false)
        }
        return matchesHost(host, rule) && matchesPath(path, rule)
    }

    fun matchesHost(host: String, rule: ShareDomainRule): Boolean {
        var pattern = rule.hostPattern.lowercase(Locale.US).trim().trim('.')
        if (pattern.isEmpty()) return true
        if (pattern.startsWith("*.")) {
            pattern = pattern.removePrefix("*.").trim('.')
            if (pattern.isEmpty()) return rule.matchSubdomains
            if (host == pattern) return true
            return rule.matchSubdomains && host.endsWith(".$pattern")
        }
        if (pattern == "*") return rule.matchSubdomains
        if (host == pattern) return true
        return rule.matchSubdomains && host.endsWith(".$pattern")
    }

    fun matchesPath(path: String, rule: ShareDomainRule): Boolean {
        if (rule.pathPattern.isNullOrBlank()) return true
        val p = UrlDetector.normalizePath(path)
        val pattern = UrlDetector.normalizePath(rule.pathPattern)
        return p == pattern || p.startsWith("$pattern/")
    }

    private fun matchesRegex(host: String, path: String, rawPattern: String): Boolean {
        val raw = rawPattern.trim()
        if (raw.isEmpty()) return false
        return runCatching {
            val pattern = if (raw.startsWith("^")) raw else "^$raw"
            val compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
            val pathOnly = UrlDetector.normalizePath(path)
            val location = locationKey(host, pathOnly)
            val locationBare = locationKey(host.removePrefix("www."), pathOnly)
            // Path-only patterns (start with /) match the path; otherwise match host+path.
            val pathAnchored = raw.startsWith("/") || raw.startsWith("^/")
            if (pathAnchored) {
                compiled.matcher(pathOnly).find()
            } else {
                compiled.matcher(location).find() || compiled.matcher(locationBare).find()
            }
        }.getOrDefault(false)
    }

    fun locationKey(host: String, path: String): String {
        val h = host.lowercase(Locale.US).trim('.')
        val p = UrlDetector.normalizePath(path)
        return if (p == "/") h else h + p
    }

    /**
     * Parse the single match field into host/path (prefix mode) or a host-agnostic regex.
     * A leading `*.` is kept on the host; whether it is honored is controlled separately.
     */
    fun parseMatchInput(raw: String, isRegex: Boolean): ParsedMatch {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParsedMatch(host = "", path = null, isRegex = false)
        }
        if (isRegex) {
            return ParsedMatch(host = "*", path = trimmed, isRegex = true)
        }
        var s = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .trim()
        val slash = s.indexOf('/')
        val host: String
        val path: String?
        if (slash < 0) {
            host = s.lowercase(Locale.US).trim('.')
            path = null
        } else {
            host = s.substring(0, slash).lowercase(Locale.US).trim('.')
            path = UrlDetector.normalizePath(s.substring(slash)).takeIf { it != "/" }
        }
        return ParsedMatch(
            host = host,
            path = path,
            isRegex = false,
        )
    }

    /** Single-field display: host + path as stored, or the regex string. */
    fun formatMatch(rule: ShareDomainRule): String {
        if (rule.pathIsRegex) {
            return rule.pathPattern?.takeIf { it.isNotBlank() } ?: rule.hostPattern
        }
        val host = rule.hostPattern
        val path = rule.pathPattern?.takeIf { it.isNotBlank() }.orEmpty()
        return host + path
    }

    data class ParsedMatch(
        val host: String,
        val path: String?,
        val isRegex: Boolean,
    )

    /** CSS used at crawl time — Custom only; Default uses built-in heuristics. */
    fun effectiveSelectors(rule: ShareDomainRule): Triple<String?, String?, String?> {
        if (rule.parseMode != ShareParseMode.Custom) return Triple(null, null, null)
        return Triple(rule.contentCss, rule.titleCss, rule.removeCss)
    }

    fun encode(rules: List<ShareDomainRule>): String {
        val body = rules.sortedBy { it.order }.joinToString(",") { rule ->
            buildString {
                append('{')
                appendJson("id", rule.id); append(',')
                appendJson("hostPattern", rule.hostPattern); append(',')
                appendJson("pathPattern", rule.pathPattern.orEmpty()); append(',')
                append("\"pathIsRegex\":").append(rule.pathIsRegex).append(',')
                append("\"enabled\":").append(rule.enabled).append(',')
                append("\"matchSubdomains\":").append(rule.matchSubdomains).append(',')
                appendJson("parseMode", rule.parseMode.name); append(',')
                appendJson("destination", rule.destination.name); append(',')
                appendJson("pluginId", rule.pluginId.orEmpty()); append(',')
                appendJson("contentCss", rule.contentCss.orEmpty()); append(',')
                appendJson("titleCss", rule.titleCss.orEmpty()); append(',')
                appendJson("removeCss", rule.removeCss.orEmpty()); append(',')
                appendJson("testUrl", rule.testUrl.orEmpty()); append(',')
                append("\"order\":").append(rule.order)
                append('}')
            }
        }
        return "[$body]"
    }

    fun decode(json: String?): List<ShareDomainRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            splitObjects(json.trim()).mapIndexed { index, obj ->
                val content = readString(obj, "contentCss")
                    .ifBlank { readString(obj, "cssSelector") }
                    .ifBlank { null }
                val legacy = readString(obj, "action")
                val (parseMode, destination, pluginId) = decodeRouting(obj, legacy, content)
                ShareDomainRule(
                    id = readString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    hostPattern = readString(obj, "hostPattern"),
                    pathPattern = readString(obj, "pathPattern").ifBlank { null },
                    pathIsRegex = readBool(obj, "pathIsRegex", false),
                    enabled = readBool(obj, "enabled", true),
                    matchSubdomains = readBool(obj, "matchSubdomains", true),
                    parseMode = parseMode,
                    destination = destination,
                    pluginId = pluginId,
                    contentCss = content,
                    titleCss = readString(obj, "titleCss").ifBlank { null },
                    removeCss = readString(obj, "removeCss").ifBlank { null },
                    testUrl = readString(obj, "testUrl").ifBlank { null },
                    order = readInt(obj, "order", index),
                )
            }.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun decodeRouting(
        obj: String,
        legacyAction: String,
        contentCss: String?,
    ): Triple<ShareParseMode, ShareDestination, String?> {
        val parseRaw = readString(obj, "parseMode")
        val destRaw = readString(obj, "destination")
        if (parseRaw.isNotBlank() || destRaw.isNotBlank()) {
            val parse = runCatching { ShareParseMode.valueOf(parseRaw) }
                .getOrDefault(ShareParseMode.Default)
            val dest = runCatching { ShareDestination.valueOf(destRaw) }
                .getOrDefault(ShareDestination.Files)
            val plugin = readString(obj, "pluginId").ifBlank { null }
            return Triple(parse, dest, plugin)
        }
        return when (legacyAction) {
            "RoyalRoadPlugin" -> Triple(ShareParseMode.Default, ShareDestination.Plugin, "royalroad")
            "RoyalRoadSimple" -> Triple(ShareParseMode.Default, ShareDestination.Queue, null)
            "Queue" -> Triple(ShareParseMode.Default, ShareDestination.Queue, null)
            "Files" -> Triple(ShareParseMode.Default, ShareDestination.Files, null)
            "CrawlArticle" -> Triple(
                if (contentCss.isNullOrBlank()) ShareParseMode.Default else ShareParseMode.Custom,
                ShareDestination.Files,
                null,
            )
            "Ask" -> Triple(ShareParseMode.Default, ShareDestination.Files, null)
            else -> Triple(ShareParseMode.Default, ShareDestination.Files, null)
        }
    }

    private fun StringBuilder.appendJson(key: String, value: String) {
        append('"').append(key).append('"').append(':')
        append('"').append(escape(value)).append('"')
    }

    private fun escape(value: String): String = buildString(value.length) {
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

    private fun splitObjects(json: String): List<String> {
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
            if (escape) {
                escape = false
                continue
            }
            when {
                inString && ch == '\\' -> escape = true
                ch == '"' -> inString = !inString
                !inString && ch == '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                !inString && ch == '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        out += inner.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
        return out
    }

    private fun readString(obj: String, key: String): String {
        val keyPat = "\"$key\""
        val idx = obj.indexOf(keyPat)
        if (idx < 0) return ""
        val colon = obj.indexOf(':', idx + keyPat.length)
        if (colon < 0) return ""
        var i = colon + 1
        while (i < obj.length && obj[i].isWhitespace()) i++
        if (i >= obj.length || obj[i] != '"') return ""
        i++
        val sb = StringBuilder()
        while (i < obj.length) {
            val ch = obj[i++]
            when {
                ch == '\\' && i < obj.length -> {
                    when (val n = obj[i++]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> sb.append(n)
                    }
                }
                ch == '"' -> break
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun readBool(obj: String, key: String, default: Boolean): Boolean {
        val keyPat = "\"$key\""
        val idx = obj.indexOf(keyPat)
        if (idx < 0) return default
        val colon = obj.indexOf(':', idx + keyPat.length)
        if (colon < 0) return default
        val rest = obj.substring(colon + 1).trimStart()
        return when {
            rest.startsWith("true") -> true
            rest.startsWith("false") -> false
            else -> default
        }
    }

    private fun readInt(obj: String, key: String, default: Int): Int {
        val keyPat = "\"$key\""
        val idx = obj.indexOf(keyPat)
        if (idx < 0) return default
        val colon = obj.indexOf(':', idx + keyPat.length)
        if (colon < 0) return default
        val rest = obj.substring(colon + 1).trimStart()
        val num = rest.takeWhile { it == '-' || it.isDigit() }
        return num.toIntOrNull() ?: default
    }
}
