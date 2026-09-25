package com.personal.flowreader.share

object ShareRouter {
    fun decide(
        payload: SharePayload,
        prefs: SharePrefs,
        routerRules: List<RouterRule>,
        parseRules: List<ParseRule>,
    ): ShareAction {
        val url = payload.url
        if (url != null) {
            val matched = ShareUrlMatch.matchUrlRule(url, routerRules)
                ?: ShareUrlMatch.firstOfKind(RouterContentKind.Url, routerRules)
            if (matched != null && matched.destination.id == RouterLanding.PLUGIN) {
                if (prefs.askMode == ShareAskMode.Ask) {
                    val parse = ShareUrlMatch.matchParse(url, parseRules)
                        ?: ParseRules.defaultForUrl(url)
                    val fallbackRule = nextNonPluginUrlRule(url, routerRules, matched.id)
                    return ShareAction.ShowChooser(
                        payload = payload,
                        pluginRule = matched,
                        parseRule = parse,
                        urlFallback = applyUrlRule(url, fallbackRule, parse),
                    )
                }
                return pluginAction(url, matched.pluginId)
            }
            val parse = ShareUrlMatch.matchParse(url, parseRules)
                ?: ParseRules.defaultForUrl(url)
            return applyUrlRule(url, matched, parse)
        }
        val textRule = ShareUrlMatch.firstOfKind(RouterContentKind.RawText, routerRules)
        return applyText(payload.text, textRule?.destination ?: RouterLanding.Queue)
    }

    fun bookFileLanding(routerRules: List<RouterRule>): RouterLanding =
        ShareUrlMatch.firstOfKind(RouterContentKind.BookFile, routerRules)?.destination
            ?: RouterLanding.Files

    private fun nextNonPluginUrlRule(
        url: String,
        rules: List<RouterRule>,
        skipId: String,
    ): RouterRule? {
        val host = UrlDetector.hostOf(url) ?: return null
        val path = UrlDetector.pathOf(url) ?: "/"
        return rules.firstOrNull {
            it.enabled &&
                it.kind == RouterContentKind.Url &&
                it.id != skipId &&
                it.destination.id != RouterLanding.PLUGIN &&
                ShareUrlMatch.matches(host, path, it)
        } ?: rules.firstOrNull {
            it.enabled &&
                it.kind == RouterContentKind.Url &&
                it.id != skipId &&
                it.destination.id != RouterLanding.PLUGIN
        }
    }

    private fun applyUrlRule(
        url: String,
        rule: RouterRule?,
        parse: ParseRule,
    ): ShareAction {
        if (rule == null) {
            return ShareAction.Crawl(url, parse, RouterLanding.Queue)
        }
        if (rule.destination.id == RouterLanding.PLUGIN) {
            return pluginAction(url, rule.pluginId)
        }
        if (!rule.parseUrl) {
            return toLibraryOrQueue(url, rule.destination)
        }
        return ShareAction.Crawl(url, parse, rule.destination)
    }

    private fun applyText(text: String, landing: RouterLanding): ShareAction =
        toLibraryOrQueue(text, landing)

    private fun toLibraryOrQueue(text: String, landing: RouterLanding): ShareAction =
        when {
            landing.isQueue -> ShareAction.ToQueue(text)
            else -> ShareAction.ToFiles(
                text = text,
                libraryTabId = landing.libraryShelfId,
            )
        }

    @Suppress("UNUSED_PARAMETER")
    private fun pluginAction(url: String, pluginId: String?): ShareAction =
        ShareAction.RoyalRoadPlugin(url)
}
