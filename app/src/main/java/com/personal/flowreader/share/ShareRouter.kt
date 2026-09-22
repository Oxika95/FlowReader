package com.personal.flowreader.share

object ShareRouter {
    fun decide(
        payload: SharePayload,
        prefs: SharePrefs,
        rules: List<ShareDomainRule>,
    ): ShareAction {
        if (payload.fromQueAlias) {
            return ShareAction.ToQueue(payload.text)
        }

        if (prefs.askMode == ShareAskMode.Ask) {
            return ShareAction.ShowChooser(payload)
        }

        val url = payload.url
        if (url == null) {
            return ShareAction.ToFiles(payload.text)
        }

        val matched = ShareDomainRules.matchUrl(url, rules)
            ?: ShareDomainRule(
                hostPattern = UrlDetector.hostOf(url).orEmpty(),
                parseMode = ShareParseMode.Default,
                destination = ShareDestination.Files,
            )

        return when (matched.destination) {
            ShareDestination.Plugin -> pluginAction(url, matched.pluginId)
            ShareDestination.Files -> crawlOrRaw(url, matched, toQueue = false)
            ShareDestination.Queue -> crawlOrRaw(url, matched, toQueue = true)
        }
    }

    private fun pluginAction(url: String, pluginId: String?): ShareAction {
        return when (pluginId) {
            "royalroad", null, "" -> ShareAction.RoyalRoadPlugin(url)
            else -> ShareAction.RoyalRoadPlugin(url) // only RR exists today
        }
    }

    private fun crawlOrRaw(url: String, rule: ShareDomainRule, toQueue: Boolean): ShareAction {
        // Domain rules for URLs always fetch; parse mode controls selectors vs heuristics.
        return ShareAction.Crawl(url = url, rule = rule, toQueue = toQueue)
    }

    fun isRoyalRoadHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        return host == "royalroad.com" || host.endsWith(".royalroad.com") ||
            host == "royalroadl.com" || host.endsWith(".royalroadl.com")
    }
}
