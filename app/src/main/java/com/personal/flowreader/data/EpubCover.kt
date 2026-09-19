package com.personal.flowreader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Extract and cache EPUB cover images next to the book file. */
object EpubCover {
    private val CACHED_NAMES = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp", "cover.gif")

    /**
     * Returns a downscaled cover bitmap for media notifications, or null if none.
     * Caches extracted bytes as `cover.*` beside [epub].
     */
    fun loadBitmap(epub: File, maxEdge: Int = 512): Bitmap? {
        if (!epub.exists() || !epub.extension.equals("epub", ignoreCase = true)) return null
        val cached = ensureCached(epub) ?: return null
        return decodeScaled(cached, maxEdge)
    }

    fun ensureCached(epub: File): File? {
        val dir = epub.parentFile ?: return null
        CACHED_NAMES.map { File(dir, it) }.firstOrNull { it.exists() && it.length() > 0L }?.let {
            return it
        }
        val extracted = extractBytes(epub) ?: return null
        val ext = extensionFor(extracted)
        val out = File(dir, "cover.$ext")
        return try {
            out.writeBytes(extracted)
            out
        } catch (_: Throwable) {
            null
        }
    }

    /** Drop cached cover files so a refreshed EPUB can re-extract. */
    fun invalidate(epub: File) {
        val dir = epub.parentFile ?: return
        CACHED_NAMES.forEach { name ->
            File(dir, name).delete()
        }
    }

    fun extractBytes(epub: File): ByteArray? {
        return try {
            ZipFile(epub).use { zip ->
                val path = findCoverPath(zip) ?: return null
                val entry = zip.getEntry(path) ?: zip.getEntry(path.trimStart('/')) ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun findCoverPath(zip: ZipFile): String? {
        val opfPath = findOpf(zip) ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opf = zip.entryTextOrNull(opfPath) ?: return null
        val doc = Jsoup.parse(opf, "", Parser.xmlParser())

        val items = doc.select("manifest > item")
        val hrefById = HashMap<String, String>()
        var coverHref: String? = null
        items.forEach { item ->
            val id = item.attr("id")
            val href = item.attr("href")
            if (id.isNotBlank() && href.isNotBlank()) hrefById[id] = href
            val props = item.attr("properties")
            if (props.split(Regex("\\s+")).any { it.equals("cover-image", ignoreCase = true) }) {
                coverHref = href
            }
        }

        if (coverHref == null) {
            val coverId = doc.selectFirst("metadata > meta[name=cover]")?.attr("content")
                ?.ifBlank { null }
            if (!coverId.isNullOrBlank()) {
                coverHref = hrefById[coverId]
            }
        }

        if (coverHref == null) {
            coverHref = hrefById.entries.firstOrNull { (id, href) ->
                id.contains("cover", ignoreCase = true) ||
                    href.substringAfterLast('/').contains("cover", ignoreCase = true)
            }?.value
        }

        val href = coverHref?.substringBefore('#')?.ifBlank { null } ?: return null
        return resolve(opfDir, href)
    }

    private fun findOpf(zip: ZipFile): String? {
        val container = zip.entryTextOrNull("META-INF/container.xml")
        if (container != null) {
            val fullPath = Jsoup.parse(container, "", Parser.xmlParser())
                .selectFirst("rootfile")
                ?.attr("full-path")
            if (!fullPath.isNullOrBlank()) return fullPath
        }
        return zip.entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun resolve(dir: String, href: String): String {
        val raw = href.substringBefore('#')
        if (dir.isBlank()) return raw
        return "$dir/$raw".replace("\\", "/").replace("//", "/")
    }

    private fun ZipFile.entryTextOrNull(name: String): String? {
        val entry = getEntry(name) ?: getEntry(name.trimStart('/')) ?: return null
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun extensionFor(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        ) {
            return "jpg"
        }
        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()
        ) {
            return "png"
        }
        if (bytes.size >= 4 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte()
        ) {
            return "webp"
        }
        if (bytes.size >= 3 &&
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()
        ) {
            return "gif"
        }
        return "jpg"
    }

    private fun decodeScaled(file: File, maxEdge: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            var sample = 1
            while (w / sample > maxEdge || h / sample > maxEdge) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (_: Throwable) {
            null
        }
    }
}
