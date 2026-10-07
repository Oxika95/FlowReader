package com.personal.flowreader.share

import com.personal.flowreader.plugin.api.PluginManifest
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

    @Test
    fun paragraphsKeepBlankLineBreaks() {
        val article = WebPageIngest.extractArticle(
            html = "<html><body><article><p>One.</p><p>Two.</p></article></body></html>",
            url = "https://example.com/a",
        )
        assertEquals("One.\n\nTwo.", article.text)
    }
}

class PluginShareSeedTest {
    private val rr = PluginManifest(
        id = "royalroad",
        name = "Royal Road",
        version = "1.0.0",
        apiVersion = 1,
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
                apiVersion = 1,
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
