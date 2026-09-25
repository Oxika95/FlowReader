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
        var pattern = hostPattern.lowercase(Locale.US).trim().trim('.')
        if (pattern.isEmpty() || pattern == "*") return true
        if (pattern.startsWith("*.")) {
            pattern = pattern.removePrefix("*.").trim('.')
            if (pattern.isEmpty()) return true
            if (host == pattern) return true
            return matchSubdomains && host.endsWith(".$pattern")
        }
        if (host == pattern) return true
        return matchSubdomains && host.endsWith(".$pattern")
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
            matchesPath(path, pathPattern)
    }

    fun matchesPath(path: String, pathPattern: String?): Boolean {
        if (pathPattern.isNullOrBlank()) return true
        val p = UrlDetector.normalizePath(path)
        val pattern = UrlDetector.normalizePath(pathPattern)
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
    /** Flow-tree defaults + Royal Road plugin URLs (list order wins). */
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
            id = "seed-royalroad",
            kind = RouterContentKind.Url,
            hostPattern = "royalroad.com",
            matchSubdomains = true,
            parseUrl = false,
            destination = RouterLanding.Plugin,
            pluginId = "royalroad",
            order = 2,
        ),
        RouterRule(
            id = "seed-royalroadl",
            kind = RouterContentKind.Url,
            hostPattern = "royalroadl.com",
            matchSubdomains = true,
            parseUrl = false,
            destination = RouterLanding.Plugin,
            pluginId = "royalroad",
            order = 3,
        ),
        RouterRule(
            id = "seed-url-any",
            kind = RouterContentKind.Url,
            hostPattern = "*",
            parseUrl = true,
            destination = RouterLanding.Queue,
            order = 4,
        ),
    )

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

    /** Build router rules from legacy plugin handoffs + route default keys. */
    fun migrateFromLegacy(
        handoffJson: String?,
        bookDest: String?,
        textDest: String?,
        urlDest: String?,
        legacyDomainJson: String?,
    ): List<RouterRule> {
        val out = ArrayList<RouterRule>()
        var order = 0
        out += RouterRule(
            id = "seed-book-files",
            kind = RouterContentKind.BookFile,
            destination = RouterLanding.parse(bookDest, RouterLanding.Files).let {
                if (it.id == RouterLanding.PLUGIN) RouterLanding.Files else it
            },
            order = order++,
        )
        out += RouterRule(
            id = "seed-raw-text",
            kind = RouterContentKind.RawText,
            destination = RouterLanding.parse(textDest, RouterLanding.Queue).let {
                if (it.id == RouterLanding.PLUGIN) RouterLanding.Queue else it
            },
            order = order++,
        )

        val fromHandoffs = decodeLegacyHandoffs(handoffJson)
        if (fromHandoffs.isNotEmpty()) {
            fromHandoffs.forEach { h ->
                out += h.copy(order = order++)
            }
        } else if (!legacyDomainJson.isNullOrBlank()) {
            val (pluginRules, _) = ParseRules.migrateLegacy(legacyDomainJson)
            pluginRules.forEach { h ->
                out += h.copy(order = order++)
            }
        } else {
            out += RouterRule(
                id = "seed-royalroad",
                kind = RouterContentKind.Url,
                hostPattern = "royalroad.com",
                matchSubdomains = true,
                parseUrl = false,
                destination = RouterLanding.Plugin,
                pluginId = "royalroad",
                order = order++,
            )
            out += RouterRule(
                id = "seed-royalroadl",
                kind = RouterContentKind.Url,
                hostPattern = "royalroadl.com",
                matchSubdomains = true,
                parseUrl = false,
                destination = RouterLanding.Plugin,
                pluginId = "royalroad",
                order = order++,
            )
        }

        val urlLanding = when (urlDest) {
            RouterLanding.FILES -> RouterLanding.Files
            "parse_queue", RouterLanding.QUEUE, null, "" -> RouterLanding.Queue
            else -> RouterLanding.Queue
        }
        val parseDefault = urlDest == null ||
            urlDest == "parse_queue" ||
            urlLanding.id == RouterLanding.FILES
        out += RouterRule(
            id = "seed-url-any",
            kind = RouterContentKind.Url,
            hostPattern = "*",
            parseUrl = parseDefault || urlLanding.id == RouterLanding.FILES,
            destination = urlLanding,
            order = order,
        )
        return out
    }

    private fun decodeLegacyHandoffs(json: String?): List<RouterRule> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            ShareJson.splitObjects(json.trim()).mapIndexed { index, obj ->
                RouterRule(
                    id = ShareJson.readString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    kind = RouterContentKind.Url,
                    enabled = ShareJson.readBool(obj, "enabled", true),
                    hostPattern = ShareJson.readString(obj, "hostPattern").ifBlank { "*" },
                    pathPattern = ShareJson.readString(obj, "pathPattern").ifBlank { null },
                    pathIsRegex = ShareJson.readBool(obj, "pathIsRegex", false),
                    matchSubdomains = ShareJson.readBool(obj, "matchSubdomains", true),
                    parseUrl = false,
                    destination = RouterLanding.Plugin,
                    pluginId = ShareJson.readString(obj, "pluginId").ifBlank { "royalroad" },
                    order = ShareJson.readInt(obj, "order", index),
                )
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}

object ParseRules {
    /** CSS used at crawl time — Custom only; Default uses built-in heuristics. */
    fun effectiveSelectors(rule: ParseRule): Triple<String?, String?, String?> {
        if (rule.parseMode != ShareParseMode.Custom) return Triple(null, null, null)
        return Triple(rule.contentCss, rule.titleCss, rule.removeCss)
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
                ShareJson.appendJson(this, "removeCss", rule.removeCss.orEmpty()); append(',')
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
                val content = ShareJson.readString(obj, "contentCss")
                    .ifBlank { ShareJson.readString(obj, "cssSelector") }
                    .ifBlank { null }
                val parseRaw = ShareJson.readString(obj, "parseMode")
                val parseMode = runCatching { ShareParseMode.valueOf(parseRaw) }
                    .getOrElse {
                        if (content.isNullOrBlank()) ShareParseMode.Default else ShareParseMode.Custom
                    }
                ParseRule(
                    id = ShareJson.readString(obj, "id").ifBlank { UUID.randomUUID().toString() },
                    hostPattern = ShareJson.readString(obj, "hostPattern"),
                    pathPattern = ShareJson.readString(obj, "pathPattern").ifBlank { null },
                    pathIsRegex = ShareJson.readBool(obj, "pathIsRegex", false),
                    enabled = ShareJson.readBool(obj, "enabled", true),
                    matchSubdomains = ShareJson.readBool(obj, "matchSubdomains", true),
                    parseMode = parseMode,
                    contentCss = content,
                    titleCss = ShareJson.readString(obj, "titleCss").ifBlank { null },
                    removeCss = ShareJson.readString(obj, "removeCss").ifBlank { null },
                    testUrl = ShareJson.readString(obj, "testUrl").ifBlank { null },
                    order = ShareJson.readInt(obj, "order", index),
                )
            }.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    /**
     * Split a legacy unified domain-rules JSON into plugin URL rules + parse rules.
     */
    fun migrateLegacy(json: String?): Pair<List<RouterRule>, List<ParseRule>> {
        if (json.isNullOrBlank()) return emptyList<RouterRule>() to emptyList()
        return try {
            val plugins = ArrayList<RouterRule>()
            val parses = ArrayList<ParseRule>()
            ShareJson.splitObjects(json.trim()).forEachIndexed { index, obj ->
                val content = ShareJson.readString(obj, "contentCss")
                    .ifBlank { ShareJson.readString(obj, "cssSelector") }
                    .ifBlank { null }
                val legacy = ShareJson.readString(obj, "action")
                val destRaw = ShareJson.readString(obj, "destination")
                val id = ShareJson.readString(obj, "id").ifBlank { UUID.randomUUID().toString() }
                val host = ShareJson.readString(obj, "hostPattern")
                val path = ShareJson.readString(obj, "pathPattern").ifBlank { null }
                val pathIsRegex = ShareJson.readBool(obj, "pathIsRegex", false)
                val enabled = ShareJson.readBool(obj, "enabled", true)
                val matchSub = ShareJson.readBool(obj, "matchSubdomains", true)
                val order = ShareJson.readInt(obj, "order", index)

                val asPlugin = destRaw == "Plugin" || legacy == "RoyalRoadPlugin"
                if (asPlugin) {
                    plugins += RouterRule(
                        id = id,
                        kind = RouterContentKind.Url,
                        enabled = enabled,
                        hostPattern = host.ifBlank { "*" },
                        pathPattern = path,
                        pathIsRegex = pathIsRegex,
                        matchSubdomains = matchSub,
                        parseUrl = false,
                        destination = RouterLanding.Plugin,
                        pluginId = ShareJson.readString(obj, "pluginId").ifBlank { "royalroad" },
                        order = order,
                    )
                } else if (legacy != "Ask") {
                    val parseRaw = ShareJson.readString(obj, "parseMode")
                    val parseMode = runCatching { ShareParseMode.valueOf(parseRaw) }
                        .getOrElse {
                            if (content.isNullOrBlank()) ShareParseMode.Default else ShareParseMode.Custom
                        }
                    parses += ParseRule(
                        id = id,
                        hostPattern = host,
                        pathPattern = path,
                        pathIsRegex = pathIsRegex,
                        enabled = enabled,
                        matchSubdomains = matchSub,
                        parseMode = parseMode,
                        contentCss = content,
                        titleCss = ShareJson.readString(obj, "titleCss").ifBlank { null },
                        removeCss = ShareJson.readString(obj, "removeCss").ifBlank { null },
                        testUrl = ShareJson.readString(obj, "testUrl").ifBlank { null },
                        order = order,
                    )
                }
            }
            plugins.sortedBy { it.order } to parses.sortedBy { it.order }
        } catch (_: Throwable) {
            emptyList<RouterRule>() to emptyList()
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
