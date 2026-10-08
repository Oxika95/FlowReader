package com.personal.flowreader.plugin.api

import org.json.JSONArray
import org.json.JSONObject

/** List/search row returned by a plugin (`Work` in the JS contract). */
data class PluginWork(
    val id: String,
    val title: String,
    val url: String = "",
    val author: String = "",
    val cover: String = "",
    /** Secondary line: latest chapter, status, etc. */
    val subtitle: String = "",
    /** Short pills on the display card (e.g. "Ongoing"). */
    val badges: List<String> = emptyList(),
    /** Compact stats on the display card. */
    val stats: List<PluginStat> = emptyList(),
)

// --- Declarative media card slots --------------------------------------------------------------

/** One stat on a media or display card: icon token + value (e.g. `star` / `4.61`). */
data class PluginStat(
    val icon: String,
    val value: String,
    /** Accessible label / long form ("Rating"). */
    val label: String = "",
)

/** Link row on the media card; the host opens [url] in the browser. */
data class PluginLink(val label: String, val url: String)

enum class PluginActionPlacement(val key: String) {
    Rail("rail"),
    Footer("footer"),
    ;

    companion object {
        fun parse(raw: String?): PluginActionPlacement? = entries.firstOrNull { it.key == raw }
    }
}

/**
 * A plugin-defined media card action. Tapping it calls `cardAction(workId, id, on?)`.
 * [toggle] actions show a filled accent state while [on] (rail only shows toggles and icons).
 */
data class PluginCardAction(
    val id: String,
    val label: String,
    val icon: String = "",
    val placement: PluginActionPlacement = PluginActionPlacement.Rail,
    val toggle: Boolean = false,
    val on: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * `WorkDetail.card`. Plugins fill slots; the host owns layout, theme and the
 * core actions (list toggles, Share, Download, Refresh, Delete, Read). See docs/plugins/ui-contract.md.
 */
data class PluginCard(
    val stats: List<PluginStat> = emptyList(),
    val badges: List<String> = emptyList(),
    val links: List<PluginLink> = emptyList(),
    val actions: List<PluginCardAction> = emptyList(),
) {
    val isEmpty: Boolean get() = stats.isEmpty() && badges.isEmpty() && links.isEmpty() && actions.isEmpty()

    /** Replace only the slots the patch sets. */
    fun apply(patch: PluginCardPatch): PluginCard = PluginCard(
        stats = patch.stats ?: stats,
        badges = patch.badges ?: badges,
        links = patch.links ?: links,
        actions = patch.actions ?: actions,
    ).capped()

    /** Enforce host caps and drop actions that would shadow host-owned ids. */
    fun capped(): PluginCard = PluginCard(
        stats = stats.take(PluginCardLimits.MAX_STATS),
        badges = badges.take(PluginCardLimits.MAX_BADGES),
        links = links.take(PluginCardLimits.MAX_LINKS),
        actions = actions
            .filter { PluginCardLimits.isValidActionId(it.id) }
            .distinctBy { it.id }
            .let { valid ->
                valid.filter { it.placement == PluginActionPlacement.Rail }.take(PluginCardLimits.MAX_RAIL_ACTIONS) +
                    valid.filter { it.placement == PluginActionPlacement.Footer }.take(PluginCardLimits.MAX_FOOTER_ACTIONS)
            },
    )
}

/** Partial card returned by `cardAction`; null slots are left unchanged. */
data class PluginCardPatch(
    val stats: List<PluginStat>? = null,
    val badges: List<String>? = null,
    val links: List<PluginLink>? = null,
    val actions: List<PluginCardAction>? = null,
)

/** `cardAction` result. */
data class PluginCardActionResult(
    val patch: PluginCardPatch? = null,
    val toast: String = "",
    /** Re-run `loadWork` and refresh the card. */
    val reload: Boolean = false,
)

object PluginCardLimits {
    const val MAX_STATS = 6
    const val MAX_BADGES = 4
    const val MAX_LINKS = 3
    const val MAX_RAIL_ACTIONS = 2
    const val MAX_FOOTER_ACTIONS = 2

    /** Ids the host uses for its own actions; plugin actions may not reuse them. */
    val RESERVED_IDS = setOf("read", "download", "refresh", "delete", "share", "open", "remove", "notify")
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

    fun isValidActionId(id: String): Boolean =
        ID_PATTERN.matches(id) && id !in RESERVED_IDS && !id.startsWith("list:")
}

data class PluginChapterRef(
    val title: String,
    val url: String,
    val id: String = "",
)

/** `loadWork` result: splash metadata plus the full ToC. */
data class PluginWorkDetail(
    val id: String,
    val title: String,
    val url: String = "",
    val author: String = "",
    val cover: String = "",
    val synopsis: String = "",
    val tags: List<String> = emptyList(),
    val chapters: List<PluginChapterRef> = emptyList(),
    /** Declarative media card slots; null when the plugin fills none. */
    val card: PluginCard? = null,
) {
    fun toWork(subtitle: String = ""): PluginWork =
        PluginWork(id = id, title = title, url = url, author = author, cover = cover, subtitle = subtitle)
}

/** `loadChapter` result. The host turns [html] (or [text]) into reader blocks. */
data class PluginChapter(
    val title: String,
    val html: String = "",
    val text: String = "",
)

data class PluginPage(
    val items: List<PluginWork>,
    val hasMore: Boolean = false,
)

data class PluginSession(
    val loggedIn: Boolean = false,
    val account: String = "",
)

/** `checkUpdates` input: what the host already has for one monitored work. */
data class PluginUpdateQuery(
    val id: String,
    val url: String,
    val chapters: Int,
    val lastChapterUrl: String,
)

/** `checkUpdates` result for one work the plugin could check. */
data class PluginUpdateInfo(
    val id: String,
    val chapters: Int? = null,
    val latestUrl: String = "",
) {
    /** True when the site has chapters the host has not stored yet. */
    fun isNewer(than: PluginUpdateQuery): Boolean =
        (chapters != null && chapters > than.chapters) ||
            (latestUrl.isNotEmpty() && latestUrl != than.lastChapterUrl)
}

enum class PluginErrorCode {
    AuthRequired,
    Network,
    Parse,
    Unsupported,
    Timeout,
    Blocked,
    Error,
    ;

    companion object {
        fun parse(raw: String?): PluginErrorCode = when (raw?.uppercase()) {
            "AUTH_REQUIRED" -> AuthRequired
            "NETWORK" -> Network
            "PARSE" -> Parse
            "UNSUPPORTED" -> Unsupported
            "TIMEOUT" -> Timeout
            "BLOCKED" -> Blocked
            else -> Error
        }
    }
}

class PluginException(
    val code: PluginErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object PluginJson {
    fun work(o: JSONObject): PluginWork? {
        val id = o.optString("id").trim()
        val title = o.optString("title").trim()
        if (id.isEmpty() || title.isEmpty()) return null
        return PluginWork(
            id = id,
            title = title,
            url = o.optString("url"),
            author = o.optString("author"),
            cover = o.optString("cover"),
            subtitle = o.optString("subtitle"),
            badges = o.optJSONArray("badges").strings().take(PluginCardLimits.MAX_BADGES),
            stats = stats(o.optJSONArray("stats")).take(PluginCardLimits.MAX_STATS),
        )
    }

    fun stats(arr: JSONArray?): List<PluginStat> = arr.objects().mapNotNull { s ->
        val value = s.optString("value").trim()
        if (value.isEmpty()) return@mapNotNull null
        PluginStat(icon = s.optString("icon").trim(), value = value, label = s.optString("label").trim())
    }

    fun links(arr: JSONArray?): List<PluginLink> = arr.objects().mapNotNull { l ->
        val url = l.optString("url").trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@mapNotNull null
        PluginLink(label = l.optString("label").trim().ifBlank { url }, url = url)
    }

    fun actions(arr: JSONArray?): List<PluginCardAction> = arr.objects().mapNotNull { a ->
        val id = a.optString("id").trim()
        val label = a.optString("label").trim()
        if (id.isEmpty() || label.isEmpty()) return@mapNotNull null
        PluginCardAction(
            id = id,
            label = label,
            icon = a.optString("icon").trim(),
            placement = PluginActionPlacement.parse(a.optString("placement")) ?: PluginActionPlacement.Rail,
            toggle = a.optBoolean("toggle", false),
            on = a.optBoolean("on", false),
            enabled = a.optBoolean("enabled", true),
        )
    }

    /** `WorkDetail.card`; null when absent or empty. */
    fun card(o: JSONObject?): PluginCard? {
        if (o == null) return null
        val card = PluginCard(
            stats = stats(o.optJSONArray("stats")),
            badges = o.optJSONArray("badges").strings(),
            links = links(o.optJSONArray("links")),
            actions = actions(o.optJSONArray("actions")),
        ).capped()
        return card.takeUnless { it.isEmpty }
    }

    fun cardPatch(o: JSONObject?): PluginCardPatch? {
        if (o == null) return null
        return PluginCardPatch(
            stats = if (o.has("stats")) stats(o.optJSONArray("stats")) else null,
            badges = if (o.has("badges")) o.optJSONArray("badges").strings() else null,
            links = if (o.has("links")) links(o.optJSONArray("links")) else null,
            actions = if (o.has("actions")) actions(o.optJSONArray("actions")) else null,
        )
    }

    fun cardActionResult(value: Any?): PluginCardActionResult = when (value) {
        is JSONObject -> PluginCardActionResult(
            patch = cardPatch(value.optJSONObject("card")),
            toast = value.optString("toast"),
            reload = value.optBoolean("reload", false),
        )
        else -> PluginCardActionResult()
    }

    /** Serialized form stored next to the splash (`card.json`). */
    fun cardToJson(card: PluginCard): JSONObject = JSONObject()
        .put("stats", JSONArray(card.stats.map { JSONObject().put("icon", it.icon).put("value", it.value).put("label", it.label) }))
        .put("badges", JSONArray(card.badges))
        .put("links", JSONArray(card.links.map { JSONObject().put("label", it.label).put("url", it.url) }))
        .put(
            "actions",
            JSONArray(
                card.actions.map {
                    JSONObject()
                        .put("id", it.id)
                        .put("label", it.label)
                        .put("icon", it.icon)
                        .put("placement", it.placement.key)
                        .put("toggle", it.toggle)
                        .put("on", it.on)
                        .put("enabled", it.enabled)
                },
            ),
        )

    fun page(value: Any?): PluginPage = when (value) {
        is JSONObject -> PluginPage(
            items = works(value.optJSONArray("items")),
            hasMore = value.optBoolean("hasMore", false),
        )
        is JSONArray -> PluginPage(items = works(value), hasMore = false)
        else -> PluginPage(emptyList())
    }

    fun works(arr: JSONArray?): List<PluginWork> = arr.objects().mapNotNull { work(it) }

    fun detail(o: JSONObject): PluginWorkDetail {
        val id = o.optString("id").trim()
        if (id.isEmpty()) throw PluginException(PluginErrorCode.Parse, "Plugin returned a work without an id")
        return PluginWorkDetail(
            id = id,
            title = o.optString("title").ifBlank { id },
            url = o.optString("url"),
            author = o.optString("author"),
            cover = o.optString("cover"),
            synopsis = o.optString("synopsis"),
            tags = o.optJSONArray("tags").strings(),
            chapters = o.optJSONArray("chapters").objects().mapNotNull { c ->
                val url = c.optString("url").trim()
                if (url.isEmpty()) return@mapNotNull null
                PluginChapterRef(title = c.optString("title"), url = url, id = c.optString("id"))
            },
            card = card(o.optJSONObject("card")),
        )
    }

    fun chapter(o: JSONObject): PluginChapter = PluginChapter(
        title = o.optString("title"),
        html = o.optString("html"),
        text = o.optString("text"),
    )

    fun session(value: Any?): PluginSession = when (value) {
        is JSONObject -> PluginSession(
            loggedIn = value.optBoolean("loggedIn", false),
            account = value.optString("account"),
        )
        else -> PluginSession()
    }

    fun chapterRef(ref: PluginChapterRef): JSONObject =
        JSONObject().put("title", ref.title).put("url", ref.url).put("id", ref.id)

    fun updateQuery(q: PluginUpdateQuery): JSONObject = JSONObject()
        .put("id", q.id)
        .put("url", q.url)
        .put("chapters", q.chapters)
        .put("lastChapterUrl", q.lastChapterUrl)

    fun updates(value: Any?): List<PluginUpdateInfo> = (value as? JSONArray).objects().mapNotNull { o ->
        val id = o.optString("id").trim()
        if (id.isEmpty()) return@mapNotNull null
        val chapters = if (o.has("chapters") && !o.isNull("chapters")) o.optInt("chapters", -1).takeIf { it >= 0 } else null
        PluginUpdateInfo(id = id, chapters = chapters, latestUrl = o.optString("latestUrl").trim())
    }
}
