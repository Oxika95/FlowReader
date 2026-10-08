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

/** Shared host/path match helpers for [RouterRule] and [ParseRule]. */
object ShareUrlMatch {
    fun matchUrlRule(url: String, rules: List<RouterRule>): RouterRule? {
        val host = UrlDetector.hostOf(url) ?: return null
        val path = UrlDetector.pathOf(url) ?: "/"
        return rules.firstOrNull {
            it.enabled && it.kind == RouterContentKind.Url && matches(host, path, it)
        }
    }

    fun matchParse(url: String, rules: List<ParseRule>): ParseRule? {
        val host = UrlDetector.hostOf(url) ?: return null
        val path = UrlDetector.pathOf(url) ?: "/"
        return rules.firstOrNull { it.enabled && matches(host, path, it) }
    }

    fun matchParseHost(host: String, path: String, rules: List<ParseRule>): ParseRule? {
        val h = host.lowercase(Locale.US).trim('.')
        val p = UrlDetector.normalizePath(path)
        return rules.firstOrNull { it.enabled && matches(h, p, it) }
    }

    fun firstOfKind(kind: RouterContentKind, rules: List<RouterRule>): RouterRule? =
        rules.firstOrNull { it.enabled && it.kind == kind }

    fun matches(host: String, path: String, rule: RouterRule): Boolean =
        matches(
            host = host,
            path = path,
            hostPattern = rule.hostPattern,
            pathPattern = rule.pathPattern,
            pathIsRegex = rule.pathIsRegex,
            matchSubdomains = rule.matchSubdomains,
        )

    fun matches(host: String, path: String, rule: ParseRule): Boolean =
        matches(
            host = host,
            path = path,
            hostPattern = rule.hostPattern,
            pathPattern = rule.pathPattern,
            pathIsRegex = rule.pathIsRegex,
            matchSubdomains = rule.matchSubdomains,
        )

    fun matchesHost(host: String, hostPattern: String, matchSubdomains: Boolean): Boolean {
        var pattern = hostPattern.lowercase(Locale.US).trim().trim('.').removePrefix("www.")
        val h = host.lowercase(Locale.US).trim('.').removePrefix("www.")
        if (pattern.isEmpty() || pattern == "*") return true
        if (pattern.startsWith("*.")) {
            pattern = pattern.removePrefix("*.").trim('.')
            if (pattern.isEmpty()) return true
            if (h == pattern) return true
            return matchSubdomains && h.endsWith(".$pattern")
        }
        if (matchSubdomains && '*' in pattern) return globMatches(h, pattern)
        if (h == pattern) return true
        return matchSubdomains && h.endsWith(".$pattern")
    }

    fun matches(
        host: String,
        path: String,
        hostPattern: String,
        pathPattern: String?,
        pathIsRegex: Boolean,
        matchSubdomains: Boolean,
    ): Boolean {
        if (pathIsRegex) {
            return matchesRegex(host, path, pathPattern ?: return false)
        }
        return matchesHost(host, hostPattern, matchSubdomains) &&
            matchesPath(path, pathPattern, wildcards = matchSubdomains)
    }

    // With wildcards, `*` matches any run of characters (including `/`); a trailing "/*" also matches the bare parent.
    fun matchesPath(path: String, pathPattern: String?, wildcards: Boolean = true): Boolean {
        if (pathPattern.isNullOrBlank()) return true
        val p = UrlDetector.normalizePath(path)
        val pattern = UrlDetector.normalizePath(pathPattern)
        if (wildcards && '*' in pattern) {
            if (pattern.endsWith("/*") && p == pattern.dropLast(2).ifEmpty { "/" }) return true
            return globMatches(p, pattern)
        }
        return p == pattern || p.startsWith("$pattern/")
    }

    private fun globMatches(value: String, glob: String): Boolean {
        val regex = glob.split('*').joinToString(".*") { Pattern.quote(it) }
        return Pattern.compile(regex).matcher(value).matches()
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

    fun parseMatchInput(raw: String, isRegex: Boolean): ParsedMatch {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return ParsedMatch(host = "*", path = null, isRegex = false)
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
            host = host.ifBlank { "*" },
            path = path,
            isRegex = false,
        )
    }

    fun formatMatch(hostPattern: String, pathPattern: String?, pathIsRegex: Boolean): String {
        if (pathIsRegex) {
            return pathPattern?.takeIf { it.isNotBlank() } ?: hostPattern
        }
        return hostPattern + (pathPattern?.takeIf { it.isNotBlank() }.orEmpty())
    }

    fun formatMatch(rule: RouterRule): String =
        formatMatch(rule.hostPattern, rule.pathPattern, rule.pathIsRegex)

    fun formatMatch(rule: ParseRule): String =
        formatMatch(rule.hostPattern, rule.pathPattern, rule.pathIsRegex)

    data class ParsedMatch(
        val host: String,
        val path: String?,
        val isRegex: Boolean,
    )
}

object RouterRules {
    const val URL_ANY_ID = "seed-url-any"

    /** Flow-tree defaults (list order wins). Plugin URL rules come from [withPluginHosts]. */
    fun seed(): List<RouterRule> = listOf(
        RouterRule(
            id = "seed-book-files",
            kind = RouterContentKind.BookFile,
            destination = RouterLanding.Files,
            order = 0,
        ),
        RouterRule(
            id = "seed-raw-text",
            kind = RouterContentKind.RawText,
            destination = RouterLanding.Queue,
            order = 1,
        ),
        RouterRule(
            id = URL_ANY_ID,
            kind = RouterContentKind.Url,
            hostPattern = "*",
            parseUrl = true,
            destination = RouterLanding.Queue,
            order = 2,
        ),
    )

    fun pluginRuleId(pluginId: String, host: String): String =
        "plugin-$pluginId-${host.lowercase(Locale.US).trim('.')}"

    /**
     * Adds one Plugin URL rule per manifest `shareHosts` entry, inserted before the catch-all
     * URL rule. Rules in [alreadySeeded] are skipped (the user may have deleted them), as are
     * hosts that already route to the same plugin. Returns the new rules and seeded ids.
     */
    fun withPluginHosts(
        current: List<RouterRule>,
        manifests: List<com.personal.flowreader.plugin.api.PluginManifest>,
        alreadySeeded: Set<String>,
    ): Pair<List<RouterRule>, Set<String>> {
        val seeded = alreadySeeded.toMutableSet()
        val additions = ArrayList<RouterRule>()
        manifests.forEach { manifest ->
            manifest.shareHosts.forEach { rawHost ->
                val host = rawHost.lowercase(Locale.US).trim().trim('.').removePrefix("www.")
                if (host.isEmpty()) return@forEach
                val id = pluginRuleId(manifest.id, host)
                if (id in seeded) return@forEach
                seeded += id
                val exists = current.any {
                    it.destination.isPlugin &&
                        it.pluginId == manifest.id &&
                        it.hostPattern.lowercase(Locale.US).trim('.').removePrefix("www.") == host
                }
                if (!exists) {
                    additions += RouterRule(
                        id = id,
                        kind = RouterContentKind.Url,
                        hostPattern = host,
                        matchSubdomains = true,
                        parseUrl = false,
                        destination = RouterLanding.Plugin,
                        pluginId = manifest.id,
                    )
                }
            }
        }
        if (additions.isEmpty()) return current to seeded
        val sorted = current.sortedBy { it.order }
        val anyIdx = sorted.indexOfFirst { it.id == URL_ANY_ID }.let { if (it < 0) sorted.size else it }
        val merged = sorted.take(anyIdx) + additions + sorted.drop(anyIdx)
        return merged.mapIndexed { i, r -> r.copy(order = i) } to seeded
    }

    fun encode(rules: List<RouterRule>): String {
        val body = rules.sortedBy { it.order }.joinToString(",") { rule ->
            buildString {
                append('{')
                ShareJson.appendJson(this, "id", rule.id); append(',')
                ShareJson.appendJson(this, "kind", rule.kind.name); append(',')
                append("\"enabled\":").append(rule.enabled).append(',')
                ShareJson.appendJson(this, "hostPattern", rule.hostPattern); append(',')
                ShareJson.appendJson(this, "pathPattern", rule.pathPattern.orEmpty()); append(',')
                append("\"pathIsRegex\":").append(rule.pathIsRegex).append(',')
                append("\"matchSubdomains\":").append(rule.matchSubdomains).append(',')
                append("\"parseUrl\":").append(rule.parseUrl).append(',')
                ShareJson.appendJson(this, "destination", rule.destination.id); append(',')
                ShareJson.appendJson(this, "pluginId", rule.pluginId.orEmpty()); append(',')
                append("\"order\":").append(rule.order)
                append('}')
            }
        }
        return "[$body]"
    }

    fun decode(json: String?): List<RouterRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            ShareJson.splitObjects(json.trim()).mapIndexed { index, obj ->
                val kind = runCatching {
                    RouterContentKind.valueOf(ShareJson.readString(obj, "kind"))
                }.getOrDefault(RouterContentKind.Url)
                RouterRule(
                    id = ShareJson.readString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    kind = kind,
                    enabled = ShareJson.readBool(obj, "enabled", true),
                    hostPattern = ShareJson.readString(obj, "hostPattern").ifBlank { "*" },
                    pathPattern = ShareJson.readString(obj, "pathPattern").ifBlank { null },
                    pathIsRegex = ShareJson.readBool(obj, "pathIsRegex", false),
                    matchSubdomains = ShareJson.readBool(obj, "matchSubdomains", true),
                    parseUrl = ShareJson.readBool(obj, "parseUrl", kind == RouterContentKind.Url),
                    destination = RouterLanding.parse(
                        ShareJson.readString(obj, "destination"),
                        RouterLanding.Queue,
                    ),
                    pluginId = ShareJson.readString(obj, "pluginId").ifBlank { null },
                    order = ShareJson.readInt(obj, "order", index),
                )
            }.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

object ParseRules {
    const val DEFAULT_ID = "seed-parse-default"

    fun isProtected(rule: ParseRule): Boolean = rule.id == DEFAULT_ID

    /**
     * [rules] in order, then the protected catch-all "Default" rule (matches any URL, always
     * enabled). Its parser mode and CSS are user-editable; its match and place are not.
     */
    fun withDefault(rules: List<ParseRule>): List<ParseRule> {
        val stored = rules.firstOrNull(::isProtected)
        val fallback = (stored ?: ParseRule(id = DEFAULT_ID, hostPattern = "*")).copy(
            hostPattern = "*",
            pathPattern = null,
            pathIsRegex = false,
            matchSubdomains = true,
            enabled = true,
        )
        return (rules.filterNot(::isProtected) + fallback).mapIndexed { i, rule -> rule.copy(order = i) }
    }

    /** Fields used at crawl time — Custom only; null = Default built-in heuristics. */
    fun effectiveSelectors(rule: ParseRule): ParseSelectors? {
        if (rule.parseMode != ShareParseMode.Custom) return null
        return ParseSelectors.of(
            title = rule.titleCss,
            body = rule.contentCss,
            cover = rule.coverCss,
            prev = rule.prevCss,
            next = rule.nextCss,
            remove = rule.removeCss,
        )
    }

    /** Next-link crawling is offered only for Custom rules with a Next selector. */
    fun canCrawl(rule: ParseRule): Boolean = effectiveSelectors(rule)?.next != null

    /** True when shared [url] would be routed to [rule] (ignoring rules ordered above it). */
    fun appliesTo(rule: ParseRule, url: String): Boolean {
        val host = UrlDetector.hostOf(url) ?: return false
        return ShareUrlMatch.matches(host, UrlDetector.pathOf(url) ?: "/", rule)
    }

    fun defaultForUrl(url: String): ParseRule =
        ParseRule(
            hostPattern = UrlDetector.hostOf(url).orEmpty(),
            parseMode = ShareParseMode.Default,
        )

    fun encode(rules: List<ParseRule>): String {
        val body = rules.sortedBy { it.order }.joinToString(",") { rule ->
            buildString {
                append('{')
                ShareJson.appendJson(this, "id", rule.id); append(',')
                ShareJson.appendJson(this, "hostPattern", rule.hostPattern); append(',')
                ShareJson.appendJson(this, "pathPattern", rule.pathPattern.orEmpty()); append(',')
                append("\"pathIsRegex\":").append(rule.pathIsRegex).append(',')
                append("\"enabled\":").append(rule.enabled).append(',')
                append("\"matchSubdomains\":").append(rule.matchSubdomains).append(',')
                ShareJson.appendJson(this, "parseMode", rule.parseMode.name); append(',')
                ShareJson.appendJson(this, "contentCss", rule.contentCss.orEmpty()); append(',')
                ShareJson.appendJson(this, "titleCss", rule.titleCss.orEmpty()); append(',')
                ShareJson.appendJson(this, "coverCss", rule.coverCss.orEmpty()); append(',')
                ShareJson.appendJson(this, "removeCss", rule.removeCss.orEmpty()); append(',')
                ShareJson.appendJson(this, "prevCss", rule.prevCss.orEmpty()); append(',')
                ShareJson.appendJson(this, "nextCss", rule.nextCss.orEmpty()); append(',')
                append("\"crawlLimit\":").append(rule.crawlLimit).append(',')
                append("\"desktop\":").append(rule.desktop).append(',')
                ShareJson.appendJson(this, "testUrl", rule.testUrl.orEmpty()); append(',')
                append("\"order\":").append(rule.order)
                append('}')
            }
        }
        return "[$body]"
    }

    fun decode(json: String?): List<ParseRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            ShareJson.splitObjects(json.trim()).mapIndexed { index, obj ->
                val parseMode = runCatching { ShareParseMode.valueOf(ShareJson.readString(obj, "parseMode")) }
                    .getOrDefault(ShareParseMode.Default)
                ParseRule(
                    id = ShareJson.readString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    hostPattern = ShareJson.readString(obj, "hostPattern"),
                    pathPattern = ShareJson.readString(obj, "pathPattern").ifBlank { null },
                    pathIsRegex = ShareJson.readBool(obj, "pathIsRegex", false),
                    enabled = ShareJson.readBool(obj, "enabled", true),
                    matchSubdomains = ShareJson.readBool(obj, "matchSubdomains", true),
                    parseMode = parseMode,
                    contentCss = ShareJson.readString(obj, "contentCss").ifBlank { null },
                    titleCss = ShareJson.readString(obj, "titleCss").ifBlank { null },
                    coverCss = ShareJson.readString(obj, "coverCss").ifBlank { null },
                    removeCss = ShareJson.readString(obj, "removeCss").ifBlank { null },
                    prevCss = ShareJson.readString(obj, "prevCss").ifBlank { null },
                    nextCss = ShareJson.readString(obj, "nextCss").ifBlank { null },
                    crawlLimit = ShareJson.readInt(obj, "crawlLimit", ParseRule.DEFAULT_CRAWL_LIMIT)
                        .coerceIn(1, ParseRule.MAX_CRAWL_LIMIT),
                    desktop = ShareJson.readBool(obj, "desktop", false),
                    testUrl = ShareJson.readString(obj, "testUrl").ifBlank { null },
                    order = ShareJson.readInt(obj, "order", index),
                )
            }.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

/** Tiny JSON helpers shared by handoff / parse persistence. */
internal object ShareJson {
    fun appendJson(sb: StringBuilder, key: String, value: String) {
        sb.append('"').append(key).append('"').append(':')
        sb.append('"').append(escape(value)).append('"')
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

    fun splitObjects(json: String): List<String> {
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

    fun readString(obj: String, key: String): String {
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

    fun readBool(obj: String, key: String, default: Boolean): Boolean {
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

    fun readInt(obj: String, key: String, default: Int): Int {
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
