package com.personal.flowreader.data

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubCfiTest {
    private fun fixture(): File {
        val file = File.createTempFile("cfi", ".epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """
                <?xml version="1.0"?>
                <container><rootfiles>
                <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles></container>
                """.trimIndent().toByteArray(),
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """
                <?xml version="1.0"?>
                <package>
                  <metadata><dc:title>CFI Book</dc:title></metadata>
                  <manifest>
                    <item id="c1" href="chap.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
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
        return file
    }

    @Test
    fun writeThenReadRoundTripsBlock() {
        val file = fixture()
        try {
            val written = EpubCfi.write(file, EpubCfi.Locus(spineIndex = 0, blockIndex = 1, charOffset = 6))
            assertTrue(written.startsWith("epubcfi(/6/2!"))
            val read = EpubCfi.read(file, written)
            assertNotNull(read)
            assertEquals(0, read!!.spineIndex)
            assertEquals(1, read.blockIndex)
            assertEquals(6, read.charOffset)
        } finally {
            file.delete()
        }
    }

    @Test
    fun foreignCfiResolvesSpine() {
        val file = fixture()
        try {
            val read = EpubCfi.read(file, "epubcfi(/6/2!/4/2/2:0)")
            assertNotNull(read)
            assertEquals(0, read!!.spineIndex)
        } finally {
            file.delete()
        }
    }

    @Test
    fun unresolvedTailOpensSpineStart() {
        val file = fixture()
        try {
            val read = EpubCfi.read(file, "epubcfi(/6/2!/99/99:0)")
            assertNotNull(read)
            assertEquals(0, read!!.spineIndex)
            assertEquals(0, read.blockIndex)
            assertEquals(0, read.charOffset)
        } finally {
            file.delete()
        }
    }
}
