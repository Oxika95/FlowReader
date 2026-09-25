package com.personal.flowreader.share

import org.junit.Assert.assertEquals
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
    fun migratesLegacyRrPluginAction() {
        val legacy =
            """[{"id":"1","hostPattern":"royalroad.com","enabled":true,"matchSubdomains":true,"action":"RoyalRoadPlugin","order":0}]"""
        val (plugins, parses) = ParseRules.migrateLegacy(legacy)
        assertTrue(parses.isEmpty())
        assertEquals(RouterLanding.PLUGIN, plugins.first().destination.id)
        assertEquals("royalroad", plugins.first().pluginId)
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
        assertEquals(Triple("article", "h1", ".ad"), ParseRules.effectiveSelectors(custom))
        assertEquals(Triple(null, null, null), ParseRules.effectiveSelectors(def))
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
    fun parseMatchInputSplitsHostAndPath() {
        val parsed = ShareUrlMatch.parseMatchInput(
            "https://www.Example.com/fiction/1/story",
            isRegex = false,
        )
        assertEquals("www.example.com", parsed.host)
        assertEquals("/fiction/1/story", parsed.path)
    }
}

class WebPageIngestTest {
    private val sampleHtml = """
        <html><head><title>Page Title</title>
        <meta property="og:title" content="OG Title"/>
        </head><body>
        <h1 class="chapter">Chapter One</h1>
        <article class="content">
          <p>Hello body.</p>
          <div class="ads">Buy now</div>
        </article>
        </body></html>
    """.trimIndent()

    @Test
    fun titleCssWins() {
        val article = WebPageIngest.extractArticle(
            html = sampleHtml,
            url = "https://example.com/ch1",
            contentCss = "article.content",
            titleCss = "h1.chapter",
        )
        assertEquals("Chapter One", article.title)
        assertTrue(article.text.contains("Hello body"))
    }

    @Test
    fun removeCssStripsJunk() {
        val article = WebPageIngest.extractArticle(
            html = sampleHtml,
            url = "https://example.com/ch1",
            contentCss = "article.content",
            removeCss = ".ads",
        )
        assertTrue(article.text.contains("Hello body"))
        assertTrue(!article.text.contains("Buy now"))
    }
}

class ShareRouterTest {
    private val rules = RouterRules.seed()
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
        assertTrue(action is ShareAction.RoyalRoadPlugin)
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
