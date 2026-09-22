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

/** Where extracted / shared content goes. */
enum class ShareDestination {
    Files,
    Queue,
    Plugin,
    ;

    val label: String
        get() = when (this) {
            Files -> "Files"
            Queue -> "Queue"
            Plugin -> "Plugin"
        }
}

data class SharePrefs(
    val showQueInShareSheet: Boolean = true,
    val askMode: ShareAskMode = ShareAskMode.Ask,
)

data class ShareDomainRule(
    val id: String = UUID.randomUUID().toString(),
    val hostPattern: String,
    /**
     * Optional URL path prefix (e.g. `/fiction/1/my-story/chapter`), or a regex when
     * [pathIsRegex] is true (e.g. `/fiction/\d+/[^/]+/chapter(?:/.*)?`).
     * Empty = any path on the host. When several rules match, the one highest in the list wins.
     */
    val pathPattern: String? = null,
    /** When true, [pathPattern] is a Java regex matched against the URL path. */
    val pathIsRegex: Boolean = false,
    val enabled: Boolean = true,
    val matchSubdomains: Boolean = true,
    val parseMode: ShareParseMode = ShareParseMode.Default,
    val destination: ShareDestination = ShareDestination.Files,
    /** When [destination] is Plugin — e.g. "royalroad". */
    val pluginId: String? = null,
    /** Content root CSS (Custom parse). */
    val contentCss: String? = null,
    val titleCss: String? = null,
    val removeCss: String? = null,
    val testUrl: String? = null,
    val order: Int = 0,
)

data class SharePayload(
    val text: String,
    val fromQueAlias: Boolean = false,
    val url: String? = UrlDetector.firstUrl(text),
)

sealed class ShareAction {
    data class ToFiles(val text: String, val titleHint: String? = null) : ShareAction()
    data class ToQueue(val text: String, val titleHint: String? = null) : ShareAction()
    data class ShowChooser(val payload: SharePayload) : ShareAction()
    data class Crawl(val url: String, val rule: ShareDomainRule, val toQueue: Boolean) : ShareAction()
    data class RoyalRoadPlugin(val url: String) : ShareAction()
    data class RoyalRoadSimple(val url: String, val toQueue: Boolean = true) : ShareAction()
}
