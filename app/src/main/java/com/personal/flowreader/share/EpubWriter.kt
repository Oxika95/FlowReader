package com.personal.flowreader.share

import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Minimal EPUB 3: one XHTML file per chapter, a nav table of contents, text paragraphs and an optional cover. */
object EpubWriter {
    data class Chapter(val title: String, val paragraphs: List<String>)

    class Cover(val bytes: ByteArray, val type: ImageType)

    enum class ImageType(val mediaType: String, val extension: String) {
        Jpeg("image/jpeg", "jpg"),
        Png("image/png", "png"),
        Gif("image/gif", "gif"),
        Webp("image/webp", "webp"),
    }

    /** Image format from the file's magic bytes; null when it isn't a supported image. */
    fun imageType(bytes: ByteArray): ImageType? {
        fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
        return when {
            at(0) == 0xFF && at(1) == 0xD8 -> ImageType.Jpeg
            at(0) == 0x89 && at(1) == 'P'.code && at(2) == 'N'.code && at(3) == 'G'.code -> ImageType.Png
            at(0) == 'G'.code && at(1) == 'I'.code && at(2) == 'F'.code -> ImageType.Gif
            at(0) == 'R'.code && at(1) == 'I'.code && at(8) == 'W'.code && at(9) == 'E'.code -> ImageType.Webp
            else -> null
        }
    }

    fun write(title: String, chapters: List<Chapter>, sourceUrl: String? = null, cover: Cover? = null): ByteArray {
        require(chapters.isNotEmpty()) { "No chapters" }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.storedEntry("mimetype", "application/epub+zip".toByteArray())
            zip.entry("META-INF/container.xml", CONTAINER)
            zip.entry("OEBPS/content.opf", opf(title, chapters.size, sourceUrl, cover?.type))
            zip.entry("OEBPS/nav.xhtml", nav(chapters))
            cover?.let { zip.entry("OEBPS/${coverFile(it.type)}", it.bytes) }
            chapters.forEachIndexed { i, ch -> zip.entry("OEBPS/${file(i)}", chapterXhtml(ch)) }
        }
        return out.toByteArray()
    }

    private fun file(i: Int) = "ch%04d.xhtml".format(i + 1)

    private fun coverFile(type: ImageType) = "cover.${type.extension}"

    private fun opf(title: String, count: Int, sourceUrl: String?, cover: ImageType?): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">""")
        append("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
        val uuid = UUID.nameUUIDFromBytes("${sourceUrl.orEmpty()}|$title|$count".toByteArray())
        append("""<dc:identifier id="id">urn:uuid:$uuid</dc:identifier>""")
        append("<dc:title>${esc(title)}</dc:title>")
        append("<dc:language>en</dc:language>")
        sourceUrl?.let { append("<dc:source>${esc(it)}</dc:source>") }
        if (cover != null) append("""<meta name="cover" content="cover-image"/>""")
        append("</metadata><manifest>")
        append("""<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
        if (cover != null) {
            append("""<item id="cover-image" href="${coverFile(cover)}" media-type="${cover.mediaType}" """)
            append("""properties="cover-image"/>""")
        }
        for (i in 0 until count) {
            append("""<item id="c$i" href="${file(i)}" media-type="application/xhtml+xml"/>""")
        }
        append("</manifest><spine>")
        for (i in 0 until count) append("""<itemref idref="c$i"/>""")
        append("</spine></package>")
    }

    private fun nav(chapters: List<Chapter>): String = xhtml("Contents") {
        append("""<nav epub:type="toc"><ol>""")
        chapters.forEachIndexed { i, ch ->
            append("""<li><a href="${file(i)}">${esc(ch.title)}</a></li>""")
        }
        append("</ol></nav>")
    }

    private fun chapterXhtml(ch: Chapter): String = xhtml(ch.title) {
        append("<h1>${esc(ch.title)}</h1>")
        ch.paragraphs.forEach { append("<p>${esc(it)}</p>") }
    }

    private fun xhtml(title: String, body: StringBuilder.() -> Unit): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">""")
        append("<head><title>${esc(title)}</title></head><body>")
        body()
        append("</body></html>")
    }

    private fun esc(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> if (ch < ' ' && ch != '\t' && ch != '\n') append(' ') else append(ch)
            }
        }
    }

    private fun ZipOutputStream.entry(name: String, text: String) = entry(name, text.toByteArray(Charsets.UTF_8))

    private fun ZipOutputStream.entry(name: String, bytes: ByteArray) {
        putNextEntry(ZipEntry(name).apply { time = FIXED_TIME })
        write(bytes)
        closeEntry()
    }

    private fun ZipOutputStream.storedEntry(name: String, bytes: ByteArray) {
        val e = ZipEntry(name).apply {
            method = ZipEntry.STORED
            time = FIXED_TIME
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        putNextEntry(e)
        write(bytes)
        closeEntry()
    }

    /** Same bytes for the same crawl, so re-imports keep one content-addressed book. */
    private const val FIXED_TIME = 946_684_800_000L

    private const val CONTAINER = """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>"""
}
