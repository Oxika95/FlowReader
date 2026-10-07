package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ChapterSourceTest {
    private fun tempFile(suffix: String) = File.createTempFile("src", suffix).apply { deleteOnExit() }

    @Test
    fun txtSegmentsCutAtBlankLines() {
        val para = "word ".repeat(40).trim()
        val text = List(30) { "$para $it" }.joinToString("\n\n")
        val file = tempFile(".txt").apply { writeText(text) }
        val segments = TxtChapterSource.segment(file, 1024)
        assertTrue(segments.size > 1)
        assertEquals(0L, segments.first().first)
        assertEquals(file.length() - 1, segments.last().last)
        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        val source = TxtChapterSource.open(file, "T")
        val all = (0 until source.chapterCount).flatMap { source.load(it).blocks.map { b -> b.text } }
        assertEquals(30, all.size)
        assertEquals("$para 29", all.last())
    }

    @Test
    fun txtSmallFileIsOneChapterTitledLikeTheBook() {
        val file = tempFile(".txt").apply { writeText("Hello.\n\nWorld.") }
        val source = ChapterSource.open(file, "My Book")
        assertEquals(1, source.chapterCount)
        assertEquals("My Book", source.chapterTitle(0))
        assertEquals(2, source.load(0).blocks.size)
    }

    @Test
    fun txtLegacyBlockSpreadsAcrossSegments() {
        val para = "x".repeat(3000)
        val file = tempFile(".txt").apply { writeText(List(40) { "$para$it" }.joinToString("\n\n")) }
        val source = TxtChapterSource.open(file, "T")
        assertTrue(source.chapterCount > 1)
        val firstCount = source.load(0).blocks.size
        assertEquals(Locus(1, 1, 5), source.legacyLocus(Locus(0, firstCount + 1, 5)))
        assertEquals(Locus(), source.legacyLocus(Locus(2, 0, 0)))
    }

    @Test
    fun epubChaptersLoadIndividually() {
        val file = epub(
            nav = true,
            "cover.xhtml" to "<html><body><img src='c.jpg'/></body></html>",
            "one.xhtml" to "<html><body><h1>First</h1><p>Alpha.</p></body></html>",
            "two%20b.xhtml" to "<html><body><p>Beta.</p><p>Gamma.</p></body></html>",
        )
        val source = ChapterSource.open(file)
        assertEquals("Tiny Book", source.title)
        assertEquals(3, source.chapterCount)
        assertEquals("Opening", source.chapterTitle(1))
        assertEquals(listOf(1 to "Opening", 2 to "Second"), source.tocEntries())
        assertTrue(source.load(0).blocks.isEmpty())
        assertEquals(listOf("Beta.", "Gamma."), source.load(2).blocks.map { it.text })
        assertEquals(2, source.indexOfHref(source.href(2)))
        assertEquals("OEBPS/two b.xhtml", source.href(2))
        assertTrue(source.weight(1) > 0)
    }

    @Test
    fun epubNcxTitlesWithoutNav() {
        val file = epub(nav = false, "one.xhtml" to "<html><body><p>Alpha.</p></body></html>")
        assertEquals("From NCX", ChapterSource.open(file).chapterTitle(0))
    }

    @Test
    fun epubLegacyLocusSkipsEmptyChapters() {
        val file = epub(
            nav = false,
            "cover.xhtml" to "<html><body></body></html>",
            "one.xhtml" to "<html><body><p>Alpha.</p></body></html>",
            "blank.xhtml" to "<html><body> </body></html>",
            "two.xhtml" to "<html><body><p>Beta.</p></body></html>",
        )
        val source = ChapterSource.open(file)
        assertEquals(Locus(1, 0, 3), source.legacyLocus(Locus(0, 0, 3)))
        assertEquals(Locus(3, 0, 0), source.legacyLocus(Locus(1, 0, 0)))
    }

    @Test
    fun epubReadKeepsOnlyChaptersWithText() {
        val file = epub(
            nav = false,
            "cover.xhtml" to "<html><body></body></html>",
            "one.xhtml" to "<html><body><p>Alpha.</p></body></html>",
        )
        val doc = EpubIngest.read(file)
        assertEquals(1, doc.chapters.size)
        assertEquals("Tiny Book", EpubIngest.readTitle(file))
    }

    private fun epub(nav: Boolean, vararg chapters: Pair<String, String>): File {
        val file = tempFile(".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            fun put(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
            put("mimetype", "application/epub+zip")
            put(
                "META-INF/container.xml",
                """<container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>""",
            )
            val manifest = chapters.mapIndexed { i, (href, _) ->
                """<item id="c$i" href="$href" media-type="application/xhtml+xml"/>"""
            }.joinToString("")
            val spine = chapters.indices.joinToString("") { """<itemref idref="c$it"/>""" }
            val navItem = if (nav) {
                """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
            } else {
                """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
            }
            put(
                "OEBPS/content.opf",
                """<package><metadata><dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Tiny Book</dc:title></metadata>""" +
                    """<manifest>$manifest$navItem</manifest><spine toc="ncx">$spine</spine></package>""",
            )
            if (nav) {
                put(
                    "OEBPS/nav.xhtml",
                    """<html><body><nav epub:type="toc"><ol>""" +
                        """<li><a href="one.xhtml">Opening</a></li><li><a href="two%20b.xhtml#x">Second</a></li>""" +
                        """</ol></nav></body></html>""",
                )
            } else {
                put(
                    "OEBPS/toc.ncx",
                    """<ncx><navMap><navPoint><navLabel><text>From NCX</text></navLabel>""" +
                        """<content src="${chapters.first().first}"/></navPoint></navMap></ncx>""",
                )
            }
            chapters.forEach { (href, html) -> put("OEBPS/" + java.net.URLDecoder.decode(href, "UTF-8"), html) }
        }
        return file
    }
}
