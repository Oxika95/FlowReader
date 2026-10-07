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
    fun txtFoldsHardWrapsWhenBlankLinesSeparateParagraphs() {
        val blocks = TxtIngest.paragraphs("One line\nwrapped.\n\nTwo.", "t")
        assertEquals(listOf("One line wrapped.", "Two."), blocks.map { it.text })
    }

    @Test
    fun txtWithoutBlankLinesKeepsEachLine() {
        val blocks = TxtIngest.paragraphs("First para.\nSecond para.\r\nThird.", "t")
        assertEquals(listOf("First para.", "Second para.", "Third."), blocks.map { it.text })
    }

    @Test
    fun txtLoneCarriageReturnAndUnicodeSeparatorsBreak() {
        val blocks = TxtIngest.paragraphs("Alpha\rBeta\u2029Gamma\u2028Delta", "t")
        assertEquals(listOf("Alpha Beta", "Gamma Delta"), blocks.map { it.text })
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
