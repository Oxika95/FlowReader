package com.personal.flowreader.share

import java.util.UUID

enum class ShareAskMode {
    Ask,
    Auto,
}

/** How to extract text from a matched URL (WebToEpub Default vs Custom CSS). */
enum class ShareParseMode {
    Default,
    Custom,
    ;

    val label: String
        get() = when (this) {
            Default -> "Default"
            Custom -> "Custom"
        }
}

/** What kind of inbound content a Router rule handles. */
enum class RouterContentKind {
    BookFile,
    RawText,
    Url,
    ;

    val label: String
        get() = when (this) {
            BookFile -> "Book files"
            RawText -> "Raw text"
            Url -> "URL"
        }
}

/**
 * Where a rule sends content.
 * [id] is `files`, `queue`, `plugin`, or a custom library tab id.
 * [PLUGIN] uses [RouterRule.pluginId].
 */
data class RouterLanding(val id: String) {
    val label: String
        get() = when (id) {
            FILES -> "Files"
            QUEUE -> "Queue"
            PLUGIN -> "Plugin"
            else -> id
        }

    /** Empty for Files; custom tab id otherwise. Not used for Queue/Plugin. */
    val libraryShelfId: String
        get() = when (id) {
            FILES, QUEUE, PLUGIN -> ""
            else -> id
        }

    val isQueue: Boolean get() = id == QUEUE
    val isPlugin: Boolean get() = id == PLUGIN
    val isLibrary: Boolean get() = id != QUEUE && id != PLUGIN

    fun label(customTitles: Map<String, String>): String =
        customTitles[id] ?: label

    companion object {
        const val FILES = "files"
        const val QUEUE = "queue"
        const val PLUGIN = "plugin"

        val Files = RouterLanding(FILES)
        val Queue = RouterLanding(QUEUE)
        val Plugin = RouterLanding(PLUGIN)

        fun tabChoices(customTabIds: List<String> = emptyList()): List<RouterLanding> =
            buildList {
                add(Files)
                add(Queue)
                customTabIds.forEach { id ->
                    val trimmed = id.trim()
                    if (trimmed.isNotEmpty() &&
                        trimmed != FILES &&
                        trimmed != QUEUE &&
                        trimmed != PLUGIN
                    ) {
                        add(RouterLanding(trimmed))
                    }
                }
                add(Plugin)
            }

        fun parse(raw: String?, fallback: RouterLanding): RouterLanding {
            val id = raw?.trim().orEmpty()
            if (id.isEmpty()) return fallback
            return RouterLanding(id)
        }
    }
}

data class SharePrefs(
    val askMode: ShareAskMode = ShareAskMode.Auto,
)

/**
 * One Router line: pick content kind, then match/parse options, then destination.
 * Seeded with the import flow-tree defaults; list order wins.
 */
data class RouterRule(
    val id: String = UUID.randomUUID().toString(),
    val kind: RouterContentKind = RouterContentKind.Url,
    val enabled: Boolean = true,
    /** URL match — ignored for BookFile / RawText. Blank or `*` = any host. */
    val hostPattern: String = "*",
    val pathPattern: String? = null,
    val pathIsRegex: Boolean = false,
    val matchSubdomains: Boolean = true,
    /** When [kind] is Url: fetch & parse the page (Parser tab CSS) instead of passing the URL through. */
    val parseUrl: Boolean = true,
    val destination: RouterLanding = RouterLanding.Queue,
    /** When [destination] is Plugin. */
    val pluginId: String? = null,
    val order: Int = 0,
)

/**
 * Parser rule: CSS / heuristics for a host when a Router URL rule has [RouterRule.parseUrl].
 */
data class ParseRule(
    val id: String = UUID.randomUUID().toString(),
    val hostPattern: String,
    val pathPattern: String? = null,
    val pathIsRegex: Boolean = false,
    val enabled: Boolean = true,
    val matchSubdomains: Boolean = true,
    val parseMode: ShareParseMode = ShareParseMode.Default,
    val contentCss: String? = null,
    val titleCss: String? = null,
    val removeCss: String? = null,
    val testUrl: String? = null,
    val order: Int = 0,
)

data class SharePayload(
    val text: String,
    val url: String? = UrlDetector.firstUrl(text),
)

sealed class ShareAction {
    data class ToFiles(
        val text: String,
        val titleHint: String? = null,
        /** Blank = Files tab; otherwise a custom library tab id. */
        val libraryTabId: String = "",
    ) : ShareAction()
    data class ToQueue(val text: String, val titleHint: String? = null) : ShareAction()
    data class ShowChooser(
        val payload: SharePayload,
        val pluginRule: RouterRule,
        val parseRule: ParseRule,
        val urlFallback: ShareAction,
    ) : ShareAction()
    data class Crawl(
        val url: String,
        val rule: ParseRule,
        val landing: RouterLanding = RouterLanding.Queue,
    ) : ShareAction()
    data class RoyalRoadPlugin(val url: String) : ShareAction()
}
