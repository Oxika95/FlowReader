package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IngestTest {
    @Test
    fun txtSplitsParagraphs() {
        val doc = TxtIngest.readText("Demo", "Hello world.\n\nSecond para.")
        assertEquals("Demo", doc.title)
        assertEquals(2, doc.chapters[0].blocks.size)
        assertEquals("Hello world.", doc.chapters[0].blocks[0].text)
    }

    @Test
    fun htmlBlocksFromParagraphs() {
        val blocks = EpubIngest.extractBlocks("<p>One</p><h2>Head</h2><p>Two</p>", "c0")
        assertEquals(3, blocks.size)
        assertEquals(BlockKind.Heading, blocks[1].kind)
        assertEquals("Two", blocks[2].text)
    }

    @Test
    fun epubSpineToChapters() {
        val file = File.createTempFile("tiny", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0"?>
                <container>
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0"?>
                <package>
                  <metadata>
                    <dc:title xmlns:dc="http://purl.org/dc/elements/1.1/">Tiny Book</dc:title>
                  </metadata>
                  <manifest>
                    <item id="c1" href="chap.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine>
                    <itemref idref="c1"/>
                  </spine>
                </package>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/chap.xhtml"))
            zip.write(
                """
                <html><body>
                <h1>Chapter One</h1>
                <p>First sentence. Second sentence.</p>
                </body></html>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
        }
        val doc = EpubIngest.read(file)
        file.delete()
        assertEquals("Tiny Book", doc.title)
        assertTrue(doc.chapters.first().blocks.any { it.text.contains("First sentence") })
    }
}

class SentenceSplitterTest {
    @Test
    fun splitsOnPeriod() {
        val doc = TxtIngest.readText("t", "Hello world. Next one.")
        val s = SentenceSplitter.split(doc)
        assertEquals(2, s.size)
        assertEquals("Hello world.", s[0].text)
        assertEquals("Next one.", s[1].text)
    }

    @Test
    fun indexAtCharOffset() {
        val doc = TxtIngest.readText("t", "Hello world. Next one.")
        val s = SentenceSplitter.split(doc)
        val i = SentenceSplitter.indexAt(s, Locus(0, 0, s[1].start))
        assertEquals(1, i)
    }
}
