package com.personal.flowreader.plugin.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host contract version. Alpha builds support only the current contract: plugins declaring any
 * other `apiVersion` are refused (the official repo is updated in lockstep with the app).
 */
const val PLUGIN_HOST_API_VERSION = 5

/** Oldest plugin contract this host still runs. */
const val PLUGIN_MIN_API_VERSION = 5

enum class PluginKind(val key: String) {
    Source("source"),
    Tts("tts"),
    ;

    companion object {
        fun parse(raw: String): PluginKind =
            entries.firstOrNull { it.key.equals(raw, ignoreCase = true) } ?: Source
    }
}

enum class PluginCapability(val key: String) {
    Search("search"),
    Lists("lists"),
    Auth("auth"),
    Membership("membership"),
    ProgressSync("progressSync"),
    ResolveUrl("resolveUrl"),
    /** Cheap `checkUpdates` for the background new-chapter check. */
    Updates("updates"),
    /** `browse(id, page, sort)` for rows of a [PluginListKind.Browse] list. */
    Browse("browse"),
    /** TTS: returns clip audio (MP3). */
    ClipAudio("clipAudio"),
    /** TTS: word-boundary metadata with audio. */
    WordBoundaries("wordBoundaries"),
    /** TTS: host may prefetch clips. */
    Prefetch("prefetch"),
    /** TTS: host may use multi-network race / mobile-data UI. */
    NetworkLanes("networkLanes"),
    ;

    companion object {
        fun parse(raw: String): PluginCapability? = entries.firstOrNull { it.key == raw }
    }
}

enum class PluginListKind(val key: String) {
    /** Rows are stories (books in the library). */
    Stories("stories"),
    /** Rows are entry points (e.g. creators) opened with `browse`; never books, never notified. */
    Browse("browse"),
    ;

    companion object {
        fun parse(raw: String): PluginListKind = entries.firstOrNull { it.key == raw } ?: Stories
    }
}

/** One personalized list (Follow / Favorite / ...). Drives sub-tabs and the media-card toggle rail. */
data class PluginList(
    val id: String,
    val title: String,
    /** Icon token; see `FlowIcons.tokens`. */
    val icon: String = "bookmark",
    /** Show a toggle for this list on the story media card. */
    val membershipToggle: Boolean = true,
    /** Account sync can page through `list(id, page)` to import remote membership. */
    val syncable: Boolean = false,
    /** New-chapter notifications default on for stories on this list (the only lists that do). */
    val notifyDefault: Boolean = false,
    val kind: PluginListKind = PluginListKind.Stories,
) {
    val isBrowse: Boolean get() = kind == PluginListKind.Browse
}

data class PluginAuthField(
    val key: String,
    val label: String,
    val secret: Boolean = false,
    /** `text` | `email` | `password` — keyboard hint only. */
    val type: String = if (secret) "password" else "text",
)

/** Sign-in on the site itself in an in-app browser; done once [doneCookie] is set on an allowed host. */
data class PluginWebLogin(
    val url: String,
    val doneCookie: String,
)

/**
 * Account → Site page. When [listId] is set, Sync/Replace (and Site Done) crawl this URL into that
 * list via WebView HTML instead of OkHttp `list()`.
 */
data class PluginSitePage(
    val title: String,
    val url: String,
    val listId: String? = null,
)

data class PluginAuth(
    val fields: List<PluginAuthField>,
    val note: String = "",
    /** When set, sign-in opens [PluginWebLogin.url] instead of the field form. */
    val web: PluginWebLogin? = null,
)

enum class PluginSettingType(val key: String) {
    Toggle("toggle"),
    Integer("int"),
    Choice("choice"),
    Text("text"),
    ;

    companion object {
        fun parse(raw: String): PluginSettingType? = entries.firstOrNull { it.key == raw }
    }
}

data class PluginSettingOption(val value: String, val label: String)

data class PluginSetting(
    val key: String,
    val type: PluginSettingType,
    val label: String,
    val description: String = "",
    /** Serialized default: `true`/`false`, a number, a choice value, or text. */
    val default: String = "",
    val options: List<PluginSettingOption> = emptyList(),
    val min: kotlin.Int? = null,
    val max: kotlin.Int? = null,
)

/** Parsed `plugin.json`. */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: kotlin.Int,
    val kind: PluginKind = PluginKind.Source,
    val description: String = "",
    val site: String = "",
    val lang: String = "",
    /** Book id prefix (`{prefix}:{workId}`); defaults to [id]. Source plugins only. */
    val bookIdPrefix: String = id,
    val allowedHosts: List<String> = emptyList(),
    /** Host-enforced gap between requests from this plugin. */
    val minRequestIntervalMs: Long = 500L,
    val capabilities: Set<PluginCapability> = emptySet(),
    val lists: List<PluginList> = emptyList(),
    val auth: PluginAuth? = null,
    /**
     * Pages for Account → Site (Sign in first, then plugin-specific). Empty: host defaults to
     * `auth.web.url` then `site` when distinct.
     */
    val sitePages: List<PluginSitePage> = emptyList(),
    val settings: List<PluginSetting> = emptyList(),
    val shareHosts: List<String> = emptyList(),
) {
    val isTts: Boolean get() = kind == PluginKind.Tts
    val isSource: Boolean get() = kind == PluginKind.Source

    fun has(cap: PluginCapability): Boolean = cap in capabilities

    fun list(id: String): PluginList? = lists.firstOrNull { it.id == id }

    /** Ordered Site pages for Account (declared [sitePages], or Sign in + home default). */
    fun resolvedSitePages(): List<PluginSitePage> {
        if (sitePages.isNotEmpty()) return sitePages
        return buildList {
            val login = auth?.web?.url?.trim().orEmpty()
            if (login.startsWith("https://")) add(PluginSitePage("Sign in", login))
            val home = site.trim()
            if (home.startsWith("https://") && home.trimEnd('/') != login.trimEnd('/')) {
                add(PluginSitePage(name, home))
            }
        }
    }

    /** Site pages that Sync/Replace crawl into a list (`listId` set and known). */
    fun listCrawlPages(): List<PluginSitePage> =
        resolvedSitePages().filter { page ->
            val id = page.listId?.trim().orEmpty()
            id.isNotEmpty() && list(id) != null
        }

    /** Site page for [url] (ignores query / trailing slash), if any. */
    fun sitePageForUrl(url: String): PluginSitePage? {
        val key = normalizeSiteUrl(url)
        if (key.isEmpty()) return null
        return resolvedSitePages().firstOrNull { normalizeSiteUrl(it.url) == key }
    }

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{1,39}$")

        fun normalizeSiteUrl(url: String): String =
            url.trim().substringBefore('#').substringBefore('?').trimEnd('/').lowercase()

        fun parse(json: String): PluginManifest = parse(JSONObject(json))

        fun parse(obj: JSONObject): PluginManifest {
            val id = obj.getString("id").trim()
            require(ID_PATTERN.matches(id)) { "Invalid plugin id: $id" }
            val kind = PluginKind.parse(obj.optString("kind"))
            val prefix = obj.optString("bookIdPrefix").trim().ifBlank { id }
            require(ID_PATTERN.matches(prefix)) { "Invalid bookIdPrefix: $prefix" }
            return PluginManifest(
                id = id,
                name = obj.optString("name").ifBlank { id },
                version = obj.optString("version").ifBlank { "0" },
                apiVersion = obj.optInt("apiVersion", 1),
                kind = kind,
                description = obj.optString("description"),
                site = obj.optString("site"),
                lang = obj.optString("lang"),
                bookIdPrefix = prefix,
                allowedHosts = obj.optJSONArray("allowedHosts").strings().map { it.lowercase() },
                minRequestIntervalMs = obj.optLong("minRequestIntervalMs", 500L).coerceIn(0L, 10_000L),
                capabilities = obj.optJSONArray("capabilities").strings()
                    .mapNotNull { PluginCapability.parse(it) }
                    .toSet(),
                lists = if (kind == PluginKind.Tts) {
                    emptyList()
                } else {
                    obj.optJSONArray("lists").objects().map { l ->
                        val listKind = PluginListKind.parse(l.optString("kind"))
                        PluginList(
                            id = l.getString("id"),
                            title = l.optString("title").ifBlank { l.getString("id") },
                            icon = l.optString("icon").ifBlank { "bookmark" },
                            membershipToggle = listKind == PluginListKind.Stories && l.optBoolean("membershipToggle", true),
                            syncable = l.optBoolean("syncable", false),
                            notifyDefault = listKind == PluginListKind.Stories && l.optBoolean("notifyDefault", false),
                            kind = listKind,
                        )
                    }
                },
                auth = if (kind == PluginKind.Tts) {
                    null
                } else {
                    obj.optJSONObject("auth")?.let { a ->
                        PluginAuth(
                            fields = a.optJSONArray("fields").objects().map { f ->
                                val secret = f.optBoolean("secret", false)
                                PluginAuthField(
                                    key = f.getString("key"),
                                    label = f.optString("label").ifBlank { f.getString("key") },
                                    secret = secret,
                                    type = f.optString("type").ifBlank { if (secret) "password" else "text" },
                                )
                            },
                            note = a.optString("note"),
                            web = a.optJSONObject("web")?.let { w ->
                                val url = w.optString("url").trim()
                                val cookie = w.optString("doneCookie").trim()
                                PluginWebLogin(url, cookie).takeIf {
                                    url.startsWith("https://") && cookie.isNotEmpty()
                                }
                            },
                        )
                    }
                },
                sitePages = if (kind == PluginKind.Tts) {
                    emptyList()
                } else {
                    obj.optJSONArray("sitePages").objects().mapNotNull { p ->
                        val title = p.optString("title").trim()
                        val url = p.optString("url").trim()
                        val listId = p.optString("listId").trim().ifBlank { null }
                        if (title.isEmpty() || !url.startsWith("https://")) null
                        else PluginSitePage(title, url, listId)
                    }
                },
                settings = obj.optJSONArray("settings").objects().mapNotNull { s ->
                    val type = PluginSettingType.parse(s.optString("type")) ?: return@mapNotNull null
                    PluginSetting(
                        key = s.getString("key"),
                        type = type,
                        label = s.optString("label").ifBlank { s.getString("key") },
                        description = s.optString("description"),
                        default = s.opt("default")?.toString().orEmpty(),
                        options = s.optJSONArray("options").objects().map { o ->
                            PluginSettingOption(
                                value = o.optString("value"),
                                label = o.optString("label").ifBlank { o.optString("value") },
                            )
                        },
                        min = if (s.has("min")) s.optInt("min") else null,
                        max = if (s.has("max")) s.optInt("max") else null,
                    )
                },
                shareHosts = if (kind == PluginKind.Tts) {
                    emptyList()
                } else {
                    obj.optJSONArray("shareHosts").strings().map { it.lowercase() }
                },
            )
        }
    }
}

internal fun JSONArray?.strings(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optString(i).trim().takeIf { it.isNotEmpty() } }
}

internal fun JSONArray?.objects(): List<JSONObject> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { i -> optJSONObject(i) }
}

/** Dotted-number compare (`1.10.0` > `1.9.3`); non-numeric parts compare as 0. */
fun compareVersions(a: String, b: String): kotlin.Int {
    val pa = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val pb = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val c = (pa.getOrNull(i) ?: 0).compareTo(pb.getOrNull(i) ?: 0)
        if (c != 0) return c
    }
    return 0
}
