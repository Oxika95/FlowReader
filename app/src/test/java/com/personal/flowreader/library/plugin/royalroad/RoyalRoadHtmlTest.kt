package com.personal.flowreader.library.plugin.royalroad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyalRoadHtmlTest {
    @Test
    fun acceptsRoyalRoadHosts() {
        assertTrue(RoyalRoadHtml.isRoyalRoadUrl("https://www.royalroad.com/fiction/1/demo"))
        assertTrue(RoyalRoadHtml.isRoyalRoadUrl("https://royalroadl.com/fiction/1/demo"))
        assertFalse(RoyalRoadHtml.isRoyalRoadUrl("https://example.com/fiction/1"))
        assertFalse(RoyalRoadHtml.isRoyalRoadUrl("not a url"))
    }

    @Test
    fun detectsChapterPath() {
        assertTrue(
            RoyalRoadHtml.isChapterUrl(
                "https://www.royalroad.com/fiction/1/demo/chapter/9/start",
            ),
        )
        assertFalse(RoyalRoadHtml.isChapterUrl("https://www.royalroad.com/fiction/1/demo"))
    }

    @Test
    fun firstChapterFromWindowChapters() {
        val html = """
            <html><body>
            <script>
            window.chapters = [
              {"id":9,"title":"Start","slug":"start","url":"/fiction/1/demo/chapter/9/start"}
            ];
            </script>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://www.royalroad.com/fiction/1/demo/chapter/9/start",
            RoyalRoadHtml.firstChapterUrl(html, "https://www.royalroad.com/fiction/1/demo"),
        )
    }

    @Test
    fun firstChapterFromLegacyTable() {
        val html = """
            <html><body>
            <table id="chapters">
              <tr data-url="/fiction/1/demo/chapter/9/start"><td><a href="/fiction/1/demo/chapter/9/start">Start</a></td></tr>
            </table>
            </body></html>
        """.trimIndent()
        assertEquals(
            "https://www.royalroad.com/fiction/1/demo/chapter/9/start",
            RoyalRoadHtml.firstChapterUrl(html, "https://www.royalroad.com/fiction/1/demo"),
        )
    }

    @Test
    fun missingToCReturnsNull() {
        assertNull(
            RoyalRoadHtml.firstChapterUrl(
                "<html><body><p>No chapters</p></body></html>",
                "https://www.royalroad.com/fiction/1/demo",
            ),
        )
    }

    @Test
    fun extractsChapterContentAndInner() {
        val content = """
            <html><body>
            <h1>The First Day</h1>
            <div class="chapter-content"><p>Hello world.</p><p>Second paragraph.</p></div>
            </body></html>
        """.trimIndent()
        val inner = RoyalRoadHtml.chapterInnerHtml(content)
        assertTrue(inner!!.contains("Hello world."))
        val blocks = RoyalRoadHtml.blocksFromChapterHtml(inner)
        assertEquals(2, blocks.size)
        assertEquals(
            "Hello world.\n\nSecond paragraph.",
            RoyalRoadHtml.blocksToPlainText(blocks),
        )

        val redesigned = """
            <html><body>
            <div class="chapter-inner"><p>Redesign text.</p></div>
            </body></html>
        """.trimIndent()
        assertTrue(RoyalRoadHtml.chapterInnerHtml(redesigned)!!.contains("Redesign text."))
    }

    @Test
    fun missingChapterBodyReturnsNull() {
        assertNull(
            RoyalRoadHtml.chapterInnerHtml("<html><body><p>No chapter wrapper</p></body></html>"),
        )
    }

    @Test
    fun windowChaptersUnescapesUnicodeTitles() {
        val html = """
            <html><body>
            <script>
            window.chapters = [
              {"id":9,"title":"Start","url":"/fiction/1/demo/chapter/9/start"},
              {"id":10,"title":"Life\u2019s Little Problems","url":"/fiction/1/demo/chapter/10/next"}
            ];
            </script>
            </body></html>
        """.trimIndent()
        val toc = RoyalRoadHtml.windowChapters(html, "https://www.royalroad.com/fiction/1/demo")
        assertEquals(2, toc.size)
        assertEquals("Life’s Little Problems", toc[1].title)
        assertEquals(
            "https://www.royalroad.com/fiction/1/demo/chapter/10/next",
            toc[1].url,
        )
    }

    @Test
    fun stripsDisplayNoneWatermarksAndKeepsVisibleText() {
        val html = """
            <html><head>
            <style>
            .csecret { display: none; speak: never; }
            </style>
            </head><body>
            <div class="chapter-inner">
              <p>Hello <span class="csecret">WATERMARK</span> world.</p>
              <p class="cnNiYjEyNGYxMDQwMzQyY2FiNzYwNzU5MjNmOWQ0MDEw">Second.</p>
            </div>
            </body></html>
        """.trimIndent()
        val inner = RoyalRoadHtml.chapterInnerHtml(html)!!
        assertFalse(inner.contains("WATERMARK"))
        val text = RoyalRoadHtml.blocksToPlainText(RoyalRoadHtml.blocksFromChapterHtml(inner))
        assertTrue(text.contains("Hello"))
        assertTrue(text.contains("world"))
        assertTrue(text.contains("Second."))
        assertFalse(text.contains("WATERMARK"))
    }

    @Test
    fun includesAuthorNotesAfterChapterBody() {
        val html = """
            <html><body>
            <div class="chapter-inner"><p>Body paragraph.</p></div>
            <div class="portlet solid author-note-portlet">
              <div class="portlet-title"><span class="caption-subject">A note from Alice</span></div>
              <div class="portlet-body author-note"><p>Note here.</p></div>
            </div>
            </body></html>
        """.trimIndent()
        val inner = RoyalRoadHtml.chapterInnerHtml(html)!!
        val text = RoyalRoadHtml.blocksToPlainText(RoyalRoadHtml.blocksFromChapterHtml(inner))
        assertTrue(text.contains("Body paragraph."))
        assertTrue(text.contains("Note here."))
        assertTrue(text.contains("A note from Alice"))
    }

    @Test
    fun parseFictionListAndPage() {
        val list = """
            <html><body>
            <div class="fiction-list-item">
              <h2 class="fiction-title"><a href="/fiction/21220/mother-of-learning">Mother of Learning</a></h2>
              <div class="stats"><span>109 Chapters</span></div>
            </div>
            </body></html>
        """.trimIndent()
        val items = RoyalRoadHtml.parseFictionList(list, "https://www.royalroad.com/fictions/best-rated")
        assertEquals(1, items.size)
        assertEquals("Mother of Learning", items[0].title)
        assertTrue(items[0].url.contains("/fiction/21220/"))

        val page = """
            <html><body>
            <div class="fic-header">
              <h1 class="font-white">Mother of Learning</h1>
              <h4 class="font-white"><a href="/profile/1">nobody103</a></h4>
            </div>
            <div class="description"><div class="hidden-content"><p>A time loop.</p></div></div>
            <script>
            window.chapters = [{"id":1,"title":"1. Good Morning Brother","url":"/fiction/21220/x/chapter/1/a"}];
            </script>
            </body></html>
        """.trimIndent()
        val fiction = RoyalRoadHtml.parseFictionPage(page, "https://www.royalroad.com/fiction/21220/mother-of-learning")
        assertEquals("Mother of Learning", fiction.title)
        assertEquals("nobody103", fiction.author)
        assertEquals("21220", fiction.fictionId)
        assertTrue(fiction.synopsis.contains("time loop"))
        assertEquals(1, fiction.chapters.size)
    }

    @Test
    fun loginTokenFromDetailsForm() {
        val html = """
            <form class="form-horizontal form-social-buttons">
              <input name="__RequestVerificationToken" value="oauth" />
            </form>
            <form method="post" class="form-login-details">
              <input name="Email" />
              <input name="__RequestVerificationToken" value="login-token" />
            </form>
        """.trimIndent()
        assertEquals("login-token", RoyalRoadHtml.loginToken(html))
        assertEquals("rr:21220", RoyalRoadHtml.bookIdFor("21220"))
        assertTrue(RoyalRoadHtml.isPluginBookId("rr:21220"))
    }
}
