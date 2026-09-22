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

class ShareDomainRulesTest {
    @Test
    fun topRuleWinsOverMoreSpecificLowerRule() {
        val rules = listOf(
            ShareDomainRule(id = "1", hostPattern = "example.com", destination = ShareDestination.Files, order = 0),
            ShareDomainRule(id = "2", hostPattern = "blog.example.com", destination = ShareDestination.Queue, order = 1),
        )
        assertEquals("1", ShareDomainRules.match("blog.example.com", rules)?.id)
        assertEquals("1", ShareDomainRules.match("www.example.com", rules)?.id)
    }

    @Test
    fun specificRuleListedFirstWins() {
        val rules = listOf(
            ShareDomainRule(id = "2", hostPattern = "blog.example.com", destination = ShareDestination.Queue, order = 0),
            ShareDomainRule(id = "1", hostPattern = "example.com", destination = ShareDestination.Files, order = 1),
        )
        assertEquals("2", ShareDomainRules.match("blog.example.com", rules)?.id)
        assertEquals("1", ShareDomainRules.match("www.example.com", rules)?.id)
    }

    @Test
    fun disabledTopRuleFallsThrough() {
        val rules = listOf(
            ShareDomainRule(id = "1", hostPattern = "example.com", enabled = false),
            ShareDomainRule(id = "2", hostPattern = "example.com"),
        )
        assertEquals("2", ShareDomainRules.match("example.com", rules)?.id)
    }

    @Test
    fun subdomainToggle() {
        val rule = ShareDomainRule(
            hostPattern = "example.com",
            matchSubdomains = false,
        )
        assertTrue(ShareDomainRules.matchesHost("example.com", rule))
        assertTrue(!ShareDomainRules.matchesHost("a.example.com", rule))
    }

    @Test
    fun roundTripEncode() {
        val seed = ShareDomainRules.seed()
        val again = ShareDomainRules.decode(ShareDomainRules.encode(seed))
        assertEquals(seed.size, again.size)
        assertEquals(seed.first().hostPattern, again.first().hostPattern)
        assertEquals(ShareDestination.Plugin, again.first().destination)
        assertEquals("royalroad", again.first().pluginId)
    }

    @Test
    fun migratesLegacyCssSelectorAndAction() {
        val legacy = """[{"id":"1","hostPattern":"blog.example.com","enabled":true,"matchSubdomains":true,"action":"CrawlArticle","cssSelector":"div.post","order":0}]"""
        val decoded = ShareDomainRules.decode(legacy).first()
        assertEquals("div.post", decoded.contentCss)
        assertEquals(ShareParseMode.Custom, decoded.parseMode)
        assertEquals(ShareDestination.Files, decoded.destination)
    }

    @Test
    fun migratesLegacyRrPluginAction() {
        val legacy = """[{"id":"1","hostPattern":"royalroad.com","enabled":true,"matchSubdomains":true,"action":"RoyalRoadPlugin","order":0}]"""
        val decoded = ShareDomainRules.decode(legacy).first()
        assertEquals(ShareDestination.Plugin, decoded.destination)
        assertEquals("royalroad", decoded.pluginId)
    }

    @Test
    fun effectiveSelectorsOnlyForCustom() {
        val custom = ShareDomainRule(
            hostPattern = "x.com",
            parseMode = ShareParseMode.Custom,
            contentCss = "article",
            titleCss = "h1",
            removeCss = ".ad",
        )
        val def = custom.copy(parseMode = ShareParseMode.Default)
        assertEquals(Triple("article", "h1", ".ad"), ShareDomainRules.effectiveSelectors(custom))
        assertEquals(Triple(null, null, null), ShareDomainRules.effectiveSelectors(def))
    }

    @Test
    fun chapterRuleAboveStoryRuleWins() {
        val rules = listOf(
            ShareDomainRule(
                id = "chapter",
                hostPattern = "royalroad.com",
                pathPattern = "/fiction/1/my-story/chapter",
                parseMode = ShareParseMode.Default,
                destination = ShareDestination.Queue,
            ),
            ShareDomainRule(
                id = "story",
                hostPattern = "royalroad.com",
                pathPattern = "/fiction/1/my-story",
                destination = ShareDestination.Plugin,
                pluginId = "royalroad",
            ),
        )
        val chapter = ShareDomainRules.matchUrl(
            "https://www.royalroad.com/fiction/1/my-story/chapter/99/title",
            rules,
        )
        assertEquals("chapter", chapter?.id)
        assertEquals(ShareDestination.Queue, chapter?.destination)

        val fiction = ShareDomainRules.matchUrl(
            "https://www.royalroad.com/fiction/1/my-story",
            rules,
        )
        assertEquals("story", fiction?.id)
        assertEquals(ShareDestination.Plugin, fiction?.destination)
    }

    @Test
    fun blankPathMatchesAny() {
        val rules = listOf(
            ShareDomainRule(
                id = "nested",
                hostPattern = "example.com",
                pathPattern = "/blog/post",
                destination = ShareDestination.Queue,
            ),
            ShareDomainRule(
                id = "any",
                hostPattern = "example.com",
                destination = ShareDestination.Files,
            ),
        )
        assertEquals(
            "nested",
            ShareDomainRules.matchUrl("https://example.com/blog/post/1", rules)?.id,
        )
        assertEquals(
            "any",
            ShareDomainRules.matchUrl("https://example.com/other", rules)?.id,
        )
    }

    @Test
    fun pathRoundTrip() {
        val rule = ShareDomainRule(
            hostPattern = "example.com",
            pathPattern = "/a/b",
            destination = ShareDestination.Queue,
        )
        val again = ShareDomainRules.decode(ShareDomainRules.encode(listOf(rule))).first()
        assertEquals("/a/b", again.pathPattern)
    }

    @Test
    fun regexPathMatchesChapters() {
        val rules = listOf(
            ShareDomainRule(
                id = "fiction",
                hostPattern = "royalroad.com",
                pathPattern = """/fiction/\d+/[^/]+$""",
                pathIsRegex = true,
                destination = ShareDestination.Plugin,
                pluginId = "royalroad",
            ),
            ShareDomainRule(
                id = "chapter",
                hostPattern = "royalroad.com",
                pathPattern = """/fiction/\d+/[^/]+/chapter""",
                pathIsRegex = true,
                parseMode = ShareParseMode.Default,
                destination = ShareDestination.Queue,
            ),
        )
        assertEquals(
            "chapter",
            ShareDomainRules.matchUrl(
                "https://www.royalroad.com/fiction/21220/my-story/chapter/1/prologue",
                rules,
            )?.id,
        )
        assertEquals(
            "fiction",
            ShareDomainRules.matchUrl(
                "https://www.royalroad.com/fiction/21220/my-story",
                rules,
            )?.id,
        )
    }

    @Test
    fun invalidRegexDoesNotMatch() {
        val rule = ShareDomainRule(
            hostPattern = "*",
            pathPattern = "[invalid",
            pathIsRegex = true,
            destination = ShareDestination.Queue,
        )
        assertTrue(!ShareDomainRules.matches("example.com", "/anything", rule))
    }

    @Test
    fun parseMatchInputSplitsHostAndPath() {
        val parsed = ShareDomainRules.parseMatchInput(
            "https://www.Example.com/fiction/1/story",
            isRegex = false,
        )
        assertEquals("www.example.com", parsed.host)
        assertEquals("/fiction/1/story", parsed.path)
        assertTrue(!parsed.isRegex)
    }

    @Test
    fun formatAndParseRoundTrip() {
        val rule = ShareDomainRule(
            hostPattern = "*.example.com",
            pathPattern = "/a/b",
            matchSubdomains = true,
        )
        val text = ShareDomainRules.formatMatch(rule)
        assertEquals("*.example.com/a/b", text)
        val parsed = ShareDomainRules.parseMatchInput(text, isRegex = false)
        assertEquals("*.example.com", parsed.host)
        assertEquals("/a/b", parsed.path)
    }

    @Test
    fun wildcardHostRequiresAllowFlagForSubdomains() {
        val withFlag = ShareDomainRule(
            hostPattern = "*.example.com",
            matchSubdomains = true,
        )
        val withoutFlag = ShareDomainRule(
            hostPattern = "*.example.com",
            matchSubdomains = false,
        )
        assertTrue(ShareDomainRules.matchesHost("a.example.com", withFlag))
        assertTrue(ShareDomainRules.matchesHost("example.com", withoutFlag))
        assertTrue(!ShareDomainRules.matchesHost("a.example.com", withoutFlag))
    }
}

class WebPageIngestTest {
    private val sampleHtml = """
        <html><head><title>Page Title</title>
        <meta property="og:title" content="OG Title"/>
        </head><body>
        <h1 class="chapter">Chapter One</h1>
        <nav>Skip nav</nav>
        <article class="content">
          <p>Hello body.</p>
          <div class="ads">Buy now</div>
          <p>More text.</p>
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

    @Test
    fun fallsBackToOgTitle() {
        val article = WebPageIngest.extractArticle(
            html = sampleHtml,
            url = "https://example.com/ch1",
            contentCss = "article.content",
        )
        assertEquals("OG Title", article.title)
    }
}

class ShareRouterTest {
    private val rules = ShareDomainRules.seed()

    @Test
    fun queAliasForcesQueue() {
        val action = ShareRouter.decide(
            SharePayload("hello", fromQueAlias = true),
            SharePrefs(askMode = ShareAskMode.Ask),
            rules,
        )
        assertTrue(action is ShareAction.ToQueue)
    }

    @Test
    fun askModeShowsChooser() {
        val action = ShareRouter.decide(
            SharePayload("https://example.com"),
            SharePrefs(askMode = ShareAskMode.Ask),
            rules,
        )
        assertTrue(action is ShareAction.ShowChooser)
    }

    @Test
    fun autoPlainGoesFiles() {
        val action = ShareRouter.decide(
            SharePayload("just text"),
            SharePrefs(askMode = ShareAskMode.Auto),
            rules,
        )
        assertTrue(action is ShareAction.ToFiles)
    }

    @Test
    fun autoRrUsesSeedPluginDestination() {
        val action = ShareRouter.decide(
            SharePayload("https://www.royalroad.com/fiction/1/foo"),
            SharePrefs(askMode = ShareAskMode.Auto),
            rules,
        )
        assertTrue(action is ShareAction.RoyalRoadPlugin)
    }

    @Test
    fun autoCustomCrawlToQueue() {
        val crawlRules = listOf(
            ShareDomainRule(
                hostPattern = "blog.example.com",
                parseMode = ShareParseMode.Custom,
                destination = ShareDestination.Queue,
                contentCss = "article",
            ),
        )
        val action = ShareRouter.decide(
            SharePayload("https://blog.example.com/post/1"),
            SharePrefs(askMode = ShareAskMode.Auto),
            crawlRules,
        )
        assertTrue(action is ShareAction.Crawl)
        assertTrue((action as ShareAction.Crawl).toQueue)
    }
}
