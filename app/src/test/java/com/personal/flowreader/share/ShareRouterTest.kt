package com.personal.flowreader.share

import com.personal.flowreader.plugin.api.PluginManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlDetectorTest {
    @Test
    fun findsHttpsInText() {
        assertEquals(
            "https://example.com/a",
            UrlDetector.firstUrl("See https://example.com/a for more"),
        )
    }

    @Test
    fun normalizesWww() {
        assertEquals("https://www.example.com/x", UrlDetector.firstUrl("www.example.com/x"))
    }

    @Test
    fun bareUrl() {
        assertEquals("https://royalroad.com/fiction/1", UrlDetector.firstUrl("https://royalroad.com/fiction/1"))
    }

    @Test
    fun none() {
        assertNull(UrlDetector.firstUrl("plain shared text only"))
    }
}

class ShareUrlMatchTest {
    @Test
    fun topParseRuleWinsOverMoreSpecificLowerRule() {
        val rules = listOf(
            ParseRule(id = "1", hostPattern = "example.com", order = 0),
            ParseRule(id = "2", hostPattern = "blog.example.com", order = 1),
        )
        assertEquals(
            "1",
            ShareUrlMatch.matchParseHost("blog.example.com", "/", rules)?.id,
        )
    }

    @Test
    fun starHostMatchesAny() {
        val rule = RouterRule(
            kind = RouterContentKind.Url,
            hostPattern = "*",
            destination = RouterLanding.Queue,
        )
        assertTrue(ShareUrlMatch.matches("anything.com", "/", rule))
    }

    @Test
    fun handoffRoundTripEncode() {
        val seed = RouterRules.seed()
        val again = RouterRules.decode(RouterRules.encode(seed))
        assertEquals(seed.size, again.size)
        assertEquals(RouterContentKind.BookFile, again.first().kind)
        assertEquals(RouterLanding.FILES, again.first().destination.id)
    }

    @Test
    fun effectiveSelectorsOnlyForCustom() {
        val custom = ParseRule(
            hostPattern = "x.com",
            parseMode = ShareParseMode.Custom,
            contentCss = "article",
            titleCss = "h1",
            removeCss = ".ad",
        )
        val def = custom.copy(parseMode = ShareParseMode.Default)
        assertEquals(
            ParseSelectors(title = "h1", body = "article", remove = ".ad"),
            ParseRules.effectiveSelectors(custom),
        )
        assertNull(ParseRules.effectiveSelectors(def))
    }

    @Test
    fun chapterParseAboveStoryHandoffUsesSeparateLists() {
        val rules = listOf(
            RouterRule(
                id = "story",
                kind = RouterContentKind.Url,
                hostPattern = "royalroad.com",
                pathPattern = """/fiction/\d+/[^/]+$""",
                pathIsRegex = true,
                destination = RouterLanding.Plugin,
                pluginId = "royalroad",
            ),
            RouterRule(
                id = "chapter",
                kind = RouterContentKind.Url,
                hostPattern = "royalroad.com",
                pathPattern = """/fiction/\d+/[^/]+/chapter""",
                pathIsRegex = true,
                parseUrl = true,
                destination = RouterLanding.Queue,
            ),
        )
        assertEquals(
            "story",
            ShareUrlMatch.matchUrlRule(
                "https://www.royalroad.com/fiction/1/my-story",
                rules,
            )?.id,
        )
        assertEquals(
            "chapter",
            ShareUrlMatch.matchUrlRule(
                "https://www.royalroad.com/fiction/1/my-story/chapter/99/title",
                rules,
            )?.id,
        )
    }

    @Test
    fun wildcardPathMatchesParseRule() {
        val parsed = ShareUrlMatch.parseMatchInput("royalroad.com/*", isRegex = false)
        val rule = ParseRule(id = "rr", hostPattern = parsed.host, pathPattern = parsed.path)
        val rules = listOf(rule, ParseRule(id = ParseRules.DEFAULT_ID, hostPattern = "*"))
        assertEquals("rr", ShareUrlMatch.matchParse("https://www.royalroad.com/fiction/1/s/chapter/2/t", rules)?.id)
        assertEquals("rr", ShareUrlMatch.matchParse("https://royalroad.com/", rules)?.id)
        assertTrue(ParseRules.appliesTo(rule, "https://www.royalroad.com/fiction/1"))
    }

    @Test
    fun wildcardInMiddleOfPath() {
        assertTrue(ShareUrlMatch.matchesPath("/fiction/12/story/chapter/3", "/fiction/*/chapter/*"))
        assertTrue(ShareUrlMatch.matchesPath("/fiction", "/fiction/*"))
        assertTrue(!ShareUrlMatch.matchesPath("/forum/12", "/fiction/*"))
        assertTrue(!ShareUrlMatch.matchesPath("/fiction/12", "/fiction/*/chapter/*"))
    }

    @Test
    fun wildcardsOffKeepsStarLiteral() {
        assertTrue(!ShareUrlMatch.matchesPath("/fiction/12", "/fiction/*", wildcards = false))
    }

    @Test
    fun wwwIsSameHost() {
        assertTrue(ShareUrlMatch.matchesHost("www.example.com", "example.com", matchSubdomains = false))
        assertTrue(ShareUrlMatch.matchesHost("example.com", "www.example.com", matchSubdomains = false))
        assertTrue(ShareUrlMatch.matchesHost("chapters.example.com", "*example.com", matchSubdomains = true))
    }

    @Test
    fun starWrappedHostMatchesShare() {
        val parsed = ShareUrlMatch.parseMatchInput("*royalroad.com*", isRegex = false)
        val rule = ParseRule(id = "rr", hostPattern = parsed.host, pathPattern = parsed.path)
        assertTrue(ParseRules.appliesTo(rule, "https://www.royalroad.com/fiction/21220/x/chapter/1/y"))
    }

    @Test
    fun parseMatchInputSplitsHostAndPath() {
        val parsed = ShareUrlMatch.parseMatchInput(
            "https://www.Example.com/fiction/1/story",
            isRegex = false,
        )
        assertEquals("www.example.com", parsed.host)
        assertEquals("/fiction/1/story", parsed.path)
    }
}

class ParseRulesJsonTest {
    @Test
    fun newFieldsRoundTrip() {
        val rule = ParseRule(
            id = "r",
            hostPattern = "example.com",
            parseMode = ShareParseMode.Custom,
            contentCss = "div.text",
            titleCss = "h1",
            removeCss = ".ads, .note",
            prevCss = "a.prev",
            nextCss = "a[rel=\"next\"]",
            coverCss = "meta[property=og:image]",
            crawlLimit = 25,
            desktop = true,
        )
        assertEquals(listOf(rule), ParseRules.decode(ParseRules.encode(listOf(rule))))
    }

    @Test
    fun oldJsonGetsDefaults() {
        val old = """[{"id":"r","hostPattern":"a.com","parseMode":"Custom","contentCss":"div","order":0}]"""
        val rule = ParseRules.decode(old).single()
        assertNull(rule.nextCss)
        assertNull(rule.coverCss)
        assertFalse(rule.desktop)
        assertEquals(ParseRule.DEFAULT_CRAWL_LIMIT, rule.crawlLimit)
    }

    @Test
    fun crawlOfferedOnlyForCustomWithNext() {
        val custom = ParseRule(hostPattern = "a", parseMode = ShareParseMode.Custom, contentCss = "p", nextCss = "a.n")
        assertTrue(ParseRules.canCrawl(custom))
        assertTrue(!ParseRules.canCrawl(custom.copy(parseMode = ShareParseMode.Default)))
        assertTrue(!ParseRules.canCrawl(custom.copy(nextCss = " ")))
    }
}

class PluginShareSeedTest {
    private val rr = PluginManifest(
        id = "royalroad",
        name = "Royal Road",
        version = "1.0.0",
        apiVersion = 4,
        shareHosts = listOf("royalroad.com", "www.royalroadl.com"),
    )

    @Test
    fun insertsPluginRulesBeforeCatchAll() {
        val (rules, seeded) = RouterRules.withPluginHosts(RouterRules.seed(), listOf(rr), emptySet())
        val ids = rules.map { it.id }
        val anyIdx = ids.indexOf(RouterRules.URL_ANY_ID)
        assertTrue(ids.indexOf(RouterRules.pluginRuleId("royalroad", "royalroad.com")) in 0 until anyIdx)
        assertTrue(ids.indexOf(RouterRules.pluginRuleId("royalroad", "royalroadl.com")) in 0 until anyIdx)
        assertEquals(2, seeded.size)
        assertEquals(rules.indices.toList(), rules.map { it.order })
    }

    @Test
    fun deletedSeedRuleIsNotReAdded() {
        val (first, seeded) = RouterRules.withPluginHosts(RouterRules.seed(), listOf(rr), emptySet())
        val deleted = first.filterNot { it.id == RouterRules.pluginRuleId("royalroad", "royalroad.com") }
        val (again, _) = RouterRules.withPluginHosts(deleted, listOf(rr), seeded)
        assertEquals(deleted, again)
    }

    @Test
    fun legacySeedRuleCountsAsExisting() {
        val legacy = RouterRules.seed() + RouterRule(
            id = "seed-royalroad",
            kind = RouterContentKind.Url,
            hostPattern = "royalroad.com",
            parseUrl = false,
            destination = RouterLanding.Plugin,
            pluginId = "royalroad",
            order = 10,
        )
        val (rules, _) = RouterRules.withPluginHosts(legacy, listOf(rr), emptySet())
        assertEquals(1, rules.count { it.pluginId == "royalroad" && it.hostPattern == "royalroad.com" })
    }
}

class ShareRouterTest {
    private val rules = RouterRules.withPluginHosts(
        RouterRules.seed(),
        listOf(
            PluginManifest(
                id = "royalroad",
                name = "Royal Road",
                version = "1.0.0",
                apiVersion = 4,
                shareHosts = listOf("royalroad.com"),
            ),
        ),
        emptySet(),
    ).first
    private val parseRules = emptyList<ParseRule>()
    private val auto = SharePrefs(askMode = ShareAskMode.Auto)
    private val ask = SharePrefs(askMode = ShareAskMode.Ask)

    @Test
    fun plainTextGoesQueue() {
        val action = ShareRouter.decide(SharePayload("just text"), auto, rules, parseRules)
        assertTrue(action is ShareAction.ToQueue)
    }

    @Test
    fun rawTextCanGoFiles() {
        val custom = rules.map {
            if (it.kind == RouterContentKind.RawText) it.copy(destination = RouterLanding.Files) else it
        }
        val action = ShareRouter.decide(SharePayload("just text"), auto, custom, parseRules)
        assertTrue(action is ShareAction.ToFiles)
    }

    @Test
    fun urlPassThroughQueuesUrlString() {
        val custom = rules.map {
            if (it.id == "seed-url-any") it.copy(parseUrl = false, destination = RouterLanding.Queue) else it
        }
        val action = ShareRouter.decide(
            SharePayload("https://blog.example.com/post/1"),
            auto,
            custom,
            parseRules,
        )
        assertTrue(action is ShareAction.ToQueue)
        assertEquals("https://blog.example.com/post/1", (action as ShareAction.ToQueue).text)
    }

    @Test
    fun urlParseToFiles() {
        val custom = rules.map {
            if (it.id == "seed-url-any") {
                it.copy(parseUrl = true, destination = RouterLanding.Files)
            } else {
                it
            }
        }
        val action = ShareRouter.decide(
            SharePayload("https://blog.example.com/post/1"),
            auto,
            custom,
            parseRules,
        )
        assertTrue(action is ShareAction.Crawl)
        assertEquals(RouterLanding.FILES, (action as ShareAction.Crawl).landing.id)
    }

    @Test
    fun autoRrUsesSeedPlugin() {
        val action = ShareRouter.decide(
            SharePayload("https://www.royalroad.com/fiction/1/foo"),
            auto,
            rules,
            parseRules,
        )
        assertTrue(action is ShareAction.Plugin)
        assertEquals("royalroad", (action as ShareAction.Plugin).pluginId)
    }

    @Test
    fun seedAloneHasNoPluginRules() {
        val action = ShareRouter.decide(
            SharePayload("https://www.royalroad.com/fiction/1/foo"),
            auto,
            RouterRules.seed(),
            parseRules,
        )
        assertTrue(action is ShareAction.Crawl)
    }

    @Test
    fun askOnPluginShowsChooser() {
        val action = ShareRouter.decide(
            SharePayload("https://www.royalroad.com/fiction/1/foo"),
            ask,
            rules,
            parseRules,
        )
        assertTrue(action is ShareAction.ShowChooser)
        assertEquals("royalroad", (action as ShareAction.ShowChooser).pluginRule.pluginId)
    }

    @Test
    fun unmatchedUrlCrawlsToQueue() {
        val action = ShareRouter.decide(
            SharePayload("https://blog.example.com/post/1"),
            auto,
            rules,
            parseRules,
        )
        assertTrue(action is ShareAction.Crawl)
        assertEquals(RouterLanding.QUEUE, (action as ShareAction.Crawl).landing.id)
    }

    @Test
    fun rawTextCanGoCustomTab() {
        val shelf = "custom-shelf-1"
        val custom = rules.map {
            if (it.kind == RouterContentKind.RawText) {
                it.copy(destination = RouterLanding(shelf))
            } else {
                it
            }
        }
        val action = ShareRouter.decide(SharePayload("just text"), auto, custom, parseRules)
        assertTrue(action is ShareAction.ToFiles)
        assertEquals(shelf, (action as ShareAction.ToFiles).libraryTabId)
    }

    @Test
    fun urlParseToCustomTab() {
        val shelf = "custom-shelf-2"
        val custom = rules.map {
            if (it.id == "seed-url-any") {
                it.copy(parseUrl = true, destination = RouterLanding(shelf))
            } else {
                it
            }
        }
        val action = ShareRouter.decide(
            SharePayload("https://blog.example.com/post/1"),
            auto,
            custom,
            parseRules,
        )
        assertTrue(action is ShareAction.Crawl)
        assertEquals(shelf, (action as ShareAction.Crawl).landing.id)
    }

    @Test
    fun bookFileLandingFromSeed() {
        assertEquals(RouterLanding.FILES, ShareRouter.bookFileLanding(rules).id)
    }
}

class DefaultParseRuleTest {
    private val site = ParseRule(id = "site", hostPattern = "example.com", parseMode = ShareParseMode.Custom, contentCss = "div.text")

    @Test
    fun addedLastWhenMissing() {
        val rules = ParseRules.withDefault(listOf(site))
        assertEquals(listOf("site", ParseRules.DEFAULT_ID), rules.map { it.id })
        assertEquals(listOf(0, 1), rules.map { it.order })
    }

    @Test
    fun keptLastEnabledAndMatchingAnyUrl() {
        val tampered = ParseRule(
            id = ParseRules.DEFAULT_ID,
            hostPattern = "only.example.org",
            pathPattern = "/x",
            enabled = false,
            parseMode = ShareParseMode.Custom,
            removeCss = ".ads",
        )
        val rules = ParseRules.withDefault(listOf(tampered, site))
        val default = rules.last()
        assertEquals(ParseRules.DEFAULT_ID, default.id)
        assertTrue(default.enabled)
        assertEquals("*", default.hostPattern)
        assertNull(default.pathPattern)
        assertEquals(ShareParseMode.Custom, default.parseMode)
        assertEquals(".ads", default.removeCss)
        assertEquals(1, rules.count { ParseRules.isProtected(it) })
    }

    @Test
    fun catchesUrlsNoOtherRuleMatches() {
        val rules = ParseRules.withDefault(listOf(site))
        assertEquals("site", ShareUrlMatch.matchParse("https://example.com/ch/1", rules)?.id)
        assertEquals(ParseRules.DEFAULT_ID, ShareUrlMatch.matchParse("https://other.net/a", rules)?.id)
    }
}

class HtmlParagraphsTest {
    private fun paras(html: String) = HtmlParagraphs.of(org.jsoup.Jsoup.parseBodyFragment(html).body())

    @Test
    fun blockBoundariesNeverJoinWords() {
        assertEquals(listOf("Hello", "World"), paras("<div>Hello</div><div>World</div>"))
        assertEquals(listOf("End.", "Next"), paras("<p>End.</p><p>Next</p>"))
    }

    @Test
    fun brBreaksParagraphs() {
        assertEquals(listOf("One.", "Two.", "Three."), paras("<div>One.<br>Two.<br><br>Three.</div>"))
    }

    @Test
    fun looseTextBesideParagraphsIsKept() {
        assertEquals(listOf("Intro text", "Para.", "Tail"), paras("<div>Intro text<p>Para.</p>Tail</div>"))
    }

    @Test
    fun nestedBlocksReadOnce() {
        assertEquals(listOf("Quoted."), paras("<blockquote><p>Quoted.</p></blockquote>"))
    }

    @Test
    fun inlineMarkupKeepsSourceSpacing() {
        assertEquals(listOf("A bold word and joined"), paras("<p>A <b>bold</b> word and <i>join</i>ed</p>"))
    }

    @Test
    fun sourceNewlinesAreSpaces() {
        assertEquals(listOf("Wrapped in source"), paras("<p>Wrapped\n   in\nsource</p>"))
    }

    @Test
    fun pageWithoutParagraphTagsKeepsBreaks() {
        val article = WebPageIngest.extractArticle(
            html = "<html><body><article>First line.<br><br>Second line.</article></body></html>",
            url = "https://example.com/a",
        )
        assertEquals("First line.\n\nSecond line.", article.text)
    }
}
