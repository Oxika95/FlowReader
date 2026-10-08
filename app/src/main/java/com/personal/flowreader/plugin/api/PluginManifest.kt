package com.personal.flowreader.plugin.api

import org.json.JSONArray
import org.json.JSONObject

/**
 * Host contract version. Alpha builds support only the current contract: plugins declaring any
 * other `apiVersion` are refused (the official repo is updated in lockstep with the app).
 */
const val PLUGIN_HOST_API_VERSION = 3

/** Oldest plugin contract this host still runs. */
const val PLUGIN_MIN_API_VERSION = 3

enum class PluginCapability(val key: String) {
    Search("search"),
    Lists("lists"),
    Auth("auth"),
    Membership("membership"),
    ProgressSync("progressSync"),
    ResolveUrl("resolveUrl"),
    /** Cheap `checkUpdates` for the background new-chapter check. */
    Updates("updates"),
    ;

    companion object {
        fun parse(raw: String): PluginCapability? = entries.firstOrNull { it.key == raw }
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
)

data class PluginAuthField(
    val key: String,
    val label: String,
    val secret: Boolean = false,
    /** `text` | `email` | `password` — keyboard hint only. */
    val type: String = if (secret) "password" else "text",
)

data class PluginAuth(
    val fields: List<PluginAuthField>,
    val note: String = "",
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
    val description: String = "",
    val site: String = "",
    val iconUrl: String = "",
    val lang: String = "",
    /** Book id prefix (`{prefix}:{workId}`); defaults to [id]. */
    val bookIdPrefix: String = id,
    val allowedHosts: List<String> = emptyList(),
    /** Host-enforced gap between requests from this plugin. */
    val minRequestIntervalMs: Long = 500L,
    val capabilities: Set<PluginCapability> = emptySet(),
    val lists: List<PluginList> = emptyList(),
    val auth: PluginAuth? = null,
    val settings: List<PluginSetting> = emptyList(),
    val shareHosts: List<String> = emptyList(),
) {
    fun has(cap: PluginCapability): Boolean = cap in capabilities

    fun list(id: String): PluginList? = lists.firstOrNull { it.id == id }

    companion object {
        private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{1,39}$")

        fun parse(json: String): PluginManifest = parse(JSONObject(json))

        fun parse(obj: JSONObject): PluginManifest {
            val id = obj.getString("id").trim()
            require(ID_PATTERN.matches(id)) { "Invalid plugin id: $id" }
            val prefix = obj.optString("bookIdPrefix").trim().ifBlank { id }
            require(ID_PATTERN.matches(prefix)) { "Invalid bookIdPrefix: $prefix" }
            return PluginManifest(
                id = id,
                name = obj.optString("name").ifBlank { id },
                version = obj.optString("version").ifBlank { "0" },
                apiVersion = obj.optInt("apiVersion", 1),
                description = obj.optString("description"),
                site = obj.optString("site"),
                iconUrl = obj.optString("iconUrl"),
                lang = obj.optString("lang"),
                bookIdPrefix = prefix,
                allowedHosts = obj.optJSONArray("allowedHosts").strings().map { it.lowercase() },
                minRequestIntervalMs = obj.optLong("minRequestIntervalMs", 500L).coerceIn(0L, 10_000L),
                capabilities = obj.optJSONArray("capabilities").strings()
                    .mapNotNull { PluginCapability.parse(it) }
                    .toSet(),
                lists = obj.optJSONArray("lists").objects().map { l ->
                    PluginList(
                        id = l.getString("id"),
                        title = l.optString("title").ifBlank { l.getString("id") },
                        icon = l.optString("icon").ifBlank { "bookmark" },
                        membershipToggle = l.optBoolean("membershipToggle", true),
                        syncable = l.optBoolean("syncable", false),
                    )
                },
                auth = obj.optJSONObject("auth")?.let { a ->
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
                    )
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
                shareHosts = obj.optJSONArray("shareHosts").strings().map { it.lowercase() },
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
