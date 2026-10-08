package com.personal.flowreader.share

import com.personal.flowreader.data.EpubChapterSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class WebCrawlTest {
    private val selectors = ParseSelectors.of(title = "h1", body = ".text", next = "a.next")

    private fun page(n: Int, next: String?) =
        """<html><head><title>Story</title></head><body><h1>Chapter $n</h1>
           <div class="text"><p>Body $n.</p></div>
           ${next?.let { "<a class=\"next\" href=\"$it\">Next</a>" }.orEmpty()}</body></html>"""

    private fun crawl(site: Map<String, String>, limit: Int = 100) =
        WebCrawl(selectors, limit, fetchHtml = { site[it] ?: error("404 $it") }, pause = {})

    private val base = "https://s.test/c/"

    @Test
    fun followsNextUntilNoLink() = runBlocking {
        val site = mapOf(base + "1" to page(1, "2"), base + "2" to page(2, "3"), base + "3" to page(3, null))
        val r = crawl(site).run(base + "1")
        assertEquals(CrawlStop.NoNext, r.stop)
        assertEquals(listOf("Chapter 1", "Chapter 2", "Chapter 3"), r.pages.map { it.title })
    }

    @Test
    fun stopsAtLimit() = runBlocking {
        val site = (1..10).associate { base + it to page(it, "${it + 1}") }
        val r = crawl(site, limit = 4).run(base + "1")
        assertEquals(CrawlStop.Limit, r.stop)
        assertEquals(4, r.pages.size)
    }

    @Test
    fun stopsOnRepeatedUrl() = runBlocking {
        val site = mapOf(base + "1" to page(1, "2"), base + "2" to page(2, "1#top"))
        val r = crawl(site).run(base + "1")
        assertEquals(CrawlStop.Repeated, r.stop)
        assertEquals(2, r.pages.size)
    }

    @Test
    fun laterFailureKeepsEarlierPages() = runBlocking {
        val site = mapOf(base + "1" to page(1, "2"), base + "2" to "<body><p>gone</p></body>")
        val r = crawl(site).run(base + "1")
        assertEquals(CrawlStop.Failed, r.stop)
        assertEquals(1, r.pages.size)
        assertEquals("Body selector matched nothing", r.error)
    }

    @Test
    fun firstPageFailureThrows() = runBlocking {
        try {
            crawl(mapOf(base + "1" to "<body></body>")).run(base + "1")
            fail("expected error")
        } catch (e: IllegalArgumentException) {
            assertEquals("Body selector matched nothing", e.message)
        }
    }

    @Test
    fun stopRequestKeepsFetchedPages() = runBlocking {
        val site = (1..10).associate { base + it to page(it, "${it + 1}") }
        var seen = 0
        val r = crawl(site).run(base + "1", stopRequested = { seen >= 2 }) { count, _ -> seen = count }
        assertEquals(CrawlStop.Stopped, r.stop)
        assertEquals(2, r.pages.size)
    }

    @Test
    fun epubRoundTripsThroughReader() = runBlocking {
        val site = mapOf(base + "1" to page(1, "2"), base + "2" to page(2, null))
        val pages = crawl(site).run(base + "1").pages
        val bytes = WebCrawl.toEpub(pages, base + "1")
        assertArrayEquals(bytes, WebCrawl.toEpub(pages, base + "1"))
        val file = File.createTempFile("crawl", ".epub").apply { writeBytes(bytes); deleteOnExit() }
        val book = EpubChapterSource.open(file)
        assertEquals("Story", book.title)
        assertEquals(2, book.chapterCount)
        assertEquals(listOf(0 to "Chapter 1", 1 to "Chapter 2"), book.tocEntries())
        val text = book.load(1).blocks.joinToString(" ") { it.text }
        assertTrue(text, "Body 2." in text)
    }

    @Test
    fun epubCarriesCoverImage() = runBlocking {
        val pages = crawl(mapOf(base + "1" to page(1, null))).run(base + "1").pages
        val png = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 1, 2, 3)
        val type = EpubWriter.imageType(png)
        assertEquals(EpubWriter.ImageType.Png, type)
        val bytes = WebCrawl.toEpub(pages, base + "1", EpubWriter.Cover(png, type!!))
        val file = File.createTempFile("cover", ".epub").apply { writeBytes(bytes); deleteOnExit() }
        ZipFile(file).use { zip ->
            val opf = zip.getInputStream(zip.getEntry("OEBPS/content.opf")).use { it.readBytes().decodeToString() }
            assertTrue(opf, "properties=\"cover-image\"" in opf && "<meta name=\"cover\" content=\"cover-image\"/>" in opf)
            assertArrayEquals(png, zip.getInputStream(zip.getEntry("OEBPS/cover.png")).use { it.readBytes() })
        }
        assertEquals(1, EpubChapterSource.open(file).chapterCount)
    }

    @Test
    fun imageTypeRejectsNonImages() {
        assertEquals(null, EpubWriter.imageType("<html>".toByteArray()))
        assertEquals(EpubWriter.ImageType.Jpeg, EpubWriter.imageType(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0)))
    }
}
