package com.personal.flowreader.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebPageIngestTest {
    private val url = "https://example.com/novel/ch-5"

    private val page = """
        <html><head><title>Ch 5 | Novel</title></head><body>
        <nav class="site">Site menu</nav>
        <h1 class="chapter-title">Chapter 5</h1>
        <div class="wrap">
          <aside class="chapter"><p>Aside text kept.</p></aside>
          <div class="chapter"><p>First.</p><div class="ads">Buy now</div><p>Second.</p>
            <div class="chapter"><p>Nested.</p></div>
          </div>
          <div class="nav-buttons"><a class="prev" href="/novel/ch-4">Prev</a>
            <span class="next"><a href="ch-6">Next</a></span></div>
        </div>
        <footer>Footer junk</footer>
        </body></html>
    """.trimIndent()

    @Test
    fun bodyKeepsEveryMatchOnceInDocumentOrder() {
        val a = WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = ".chapter"))
        assertEquals(listOf("Aside text kept.", "First.", "Buy now", "Second.", "Nested."), a.paragraphs)
        assertEquals(2, a.diagnostics?.bodyMatches)
    }

    @Test
    fun bodyOnlyIgnoresRestOfPage() {
        val a = WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = "div.chapter"))
        assertEquals(listOf("First.", "Buy now", "Second.", "Nested."), a.paragraphs)
    }

    @Test
    fun noMatchFailsInsteadOfImportingWholePage() {
        try {
            WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = ".missing"))
            fail("expected error")
        } catch (e: IllegalArgumentException) {
            assertEquals("Body selector matched nothing", e.message)
        }
    }

    @Test
    fun invalidBodyIsReported() {
        try {
            WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = "div[class"))
            fail("expected error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Body selector is invalid"))
        }
    }

    @Test
    fun removeAppliesValidEntriesAndSkipsInvalidOnes() {
        val a = WebPageIngest.extractArticle(
            page,
            url,
            ParseSelectors.of(body = "div.wrap > div.chapter", remove = ".ads, .bad[, div.chapter div.chapter"),
        )
        assertEquals(listOf("First.", "Second."), a.paragraphs)
        assertEquals(2, a.diagnostics?.removeApplied)
        assertEquals(listOf(".bad["), a.diagnostics?.removeSkipped)
    }

    @Test
    fun customKeepsNavAsideFooterWhenTargeted() {
        val a = WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = "nav, footer"))
        assertEquals(listOf("Site menu", "Footer junk"), a.paragraphs)
    }

    @Test
    fun titleFromSelectorElsePageTitle() {
        val custom = WebPageIngest.extractArticle(page, url, ParseSelectors.of(title = "h1.chapter-title", body = "div.wrap"))
        assertEquals("Chapter 5", custom.title)
        assertEquals("Ch 5 | Novel", custom.pageTitle)
        val noTitle = WebPageIngest.extractArticle(page, url, ParseSelectors.of(body = "div.wrap"))
        assertEquals("Ch 5 | Novel", noTitle.title)
    }

    @Test
    fun prevAndNextResolveOwnHrefOrInnerLink() {
        val a = WebPageIngest.extractArticle(
            page,
            url,
            ParseSelectors.of(body = "div.wrap", prev = "a.prev", next = "span.next"),
        )
        assertEquals("https://example.com/novel/ch-4", a.prevUrl)
        assertEquals("https://example.com/novel/ch-6", a.nextUrl)
    }

    @Test
    fun nextResolvesEnclosingLink() {
        val html = """<body><div class="c"><p>x</p></div><a href="/p2"><span class="lbl">Next</span></a></body>"""
        val a = WebPageIngest.extractArticle(html, url, ParseSelectors.of(body = ".c", next = "span.lbl"))
        assertEquals("https://example.com/p2", a.nextUrl)
    }

    @Test
    fun nextLinkToSamePageOrMissingIsNull() {
        val html = """<body><div class="c"><p>x</p></div><a class="n" href="#top">Next</a></body>"""
        val a = WebPageIngest.extractArticle(html, url, ParseSelectors.of(body = ".c", next = "a.n, .none"))
        assertNull(a.nextUrl)
        assertTrue(a.diagnostics!!.summary().contains("Next not found"))
    }

    @Test
    fun linksAreReadBeforeRemoveStripsThem() {
        val a = WebPageIngest.extractArticle(
            page,
            url,
            ParseSelectors.of(body = "div.wrap", next = "span.next", remove = ".nav-buttons"),
        )
        assertEquals("https://example.com/novel/ch-6", a.nextUrl)
        assertTrue("Next" !in a.paragraphs)
    }

    @Test
    fun defaultModeUnchanged() {
        val a = WebPageIngest.extractArticle(page, url, null)
        assertTrue("Site menu" !in a.text && "Footer junk" !in a.text && "Aside text kept." !in a.text)
        assertTrue("First." in a.text)
        assertEquals("Default heuristics", a.diagnostics?.summary())
    }

    @Test
    fun paragraphsKeepBlankLineBreaks() {
        val a = WebPageIngest.extractArticle("<html><body><article><p>One.</p><p>Two.</p></article></body></html>", url)
        assertEquals("One.\n\nTwo.", a.text)
    }

    @Test
    fun cssListSplitsTopLevelCommasOnly() {
        assertEquals(
            listOf(".a", "div:not(.b, .c)", "a[title=\"x, y\"]", "[data-x='1,2']", ".bad[", ".ok"),
            CssList.split(" .a , div:not(.b, .c),a[title=\"x, y\"], [data-x='1,2'],, .bad[, .ok"),
        )
        assertEquals(listOf("div:not(.a", ".b"), CssList.split("div:not(.a, .b"))
    }

    private val coverPage = """
        <html><head><meta property="og:image" content="/img/og.png"></head><body>
        <div class="cover"><img src="/img/placeholder.gif" data-src="cover.jpg"></div>
        <div class="banner" style="background-image: url('/img/bg.webp')"></div>
        <img class="srcset" srcset="/img/a.jpg 1x, /img/b.jpg 2x">
        <div class="text"><p>Body.</p></div>
        </body></html>
    """.trimIndent()

    private fun cover(css: String) =
        WebPageIngest.extractArticle(coverPage, url, ParseSelectors.of(body = ".text", cover = css)).coverUrl

    @Test
    fun coverReadsLazySourceInsideContainer() {
        assertEquals("https://example.com/novel/cover.jpg", cover("div.cover"))
    }

    @Test
    fun coverReadsMetaContent() {
        assertEquals("https://example.com/img/og.png", cover("meta[property=og:image]"))
    }

    @Test
    fun coverReadsBackgroundAndSrcset() {
        assertEquals("https://example.com/img/bg.webp", cover(".banner"))
        assertEquals("https://example.com/img/a.jpg", cover("img.srcset"))
    }

    @Test
    fun coverMissingIsReportedNotFound() {
        val a = WebPageIngest.extractArticle(coverPage, url, ParseSelectors.of(body = ".text", cover = ".nope"))
        assertNull(a.coverUrl)
        assertTrue(a.diagnostics!!.summary(), "Cover not found" in a.diagnostics!!.summary())
    }

    @Test
    fun cssHiddenNoticesAreDroppedInDefaultAndCustom() {
        val hiddenPage = """
            <html><head><style>.cXyZ { display: none; speak: never; }</style></head><body>
            <article><div class="chapter-inner"><p>First.</p><p class="cXyZ">Report it on Amazon.</p><p>Second.</p></div></article>
            </body></html>
        """.trimIndent()
        assertEquals(listOf("First.", "Second."), WebPageIngest.extractArticle(hiddenPage, url).paragraphs)
        assertEquals(
            listOf("First.", "Second."),
            WebPageIngest.extractArticle(hiddenPage, url, ParseSelectors.of(body = ".chapter-inner")).paragraphs,
        )
        assertEquals(
            listOf("First.", "Report it on Amazon.", "Second."),
            WebPageIngest.extractArticle(hiddenPage, url, stripHidden = false).paragraphs,
        )
    }
}
