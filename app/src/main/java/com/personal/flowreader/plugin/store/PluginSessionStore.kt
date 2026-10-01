package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Chapter
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginJson
import java.io.File
import org.json.JSONObject
import java.security.MessageDigest

/** On-disk ToC + cached chapter bodies for one plugin story, plus the in-memory stream window. */
data class PluginReadSession(
    val bookId: String,
    val pluginId: String,
    val workId: String,
    val workUrl: String,
    val title: String,
    val author: String,
    val toc: List<PluginChapterRef>,
    val startIndex: Int,
    var loadedThrough: Int,
    var chapters: List<Chapter>,
    val prefetchAhead: Int = PluginCachePolicy.DEFAULT_AHEAD,
    val keepBehind: Int = PluginCachePolicy.DEFAULT_BEHIND,
    /** Inclusive chapter-index ranges that survive stream prune. */
    val pinnedRanges: List<IntRange> = emptyList(),
) {
    fun nextIndex(): Int? {
        val next = loadedThrough + 1
        return next.takeIf { it in toc.indices }
    }

    val cachePolicy: PluginCachePolicy
        get() = PluginCachePolicy(prefetchAhead, keepBehind, pinnedRanges)
}

/** Stream / offline retention policy for chapter bodies on disk. */
data class PluginCachePolicy(
    val prefetchAhead: Int = DEFAULT_AHEAD,
    val keepBehind: Int = DEFAULT_BEHIND,
    val pinnedRanges: List<IntRange> = emptyList(),
) {
    companion object {
        const val DEFAULT_AHEAD = 1
        const val DEFAULT_BEHIND = 1
    }
}

/** Work-page metadata for the story media card (chapter bodies not required). */
data class PluginSplashMeta(
    val synopsis: String = "",
    val tags: List<String> = emptyList(),
    val views: Long? = null,
    val rating: String = "",
    val status: String = "",
    val cover: String = "",
    /** apiVersion 2 card slots (`card.json`); null for v1 plugins. */
    val card: PluginCard? = null,
)

data class PluginLibraryMeta(
    val author: String,
    val chapterCount: Int,
    val downloadedCount: Int,
)

/** Plain-text file format for `{dataDir}/{workDir}/meta.txt`, `toc.txt`, `splash.txt`, `c/{i}.txt`. */
object PluginSessionStore {
    private val SAFE_DIR = Regex("^[A-Za-z0-9._-]{1,80}$")

    /** Filesystem-safe folder for a work id (ids with other characters are hashed). */
    fun workDirName(workId: String): String {
        if (SAFE_DIR.matches(workId) && !workId.startsWith("_") && !workId.startsWith(".")) return workId
        val digest = MessageDigest.getInstance("SHA-1").digest(workId.toByteArray())
        return "w_" + digest.joinToString("") { "%02x".format(it) }
    }

    fun dir(root: File, workId: String): File = File(root, workDirName(workId))

    fun writeMeta(dir: File, session: PluginReadSession) {
        dir.mkdirs()
        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("v2")
                appendLine("bookId=${session.bookId}")
                appendLine("pluginId=${session.pluginId}")
                appendLine("workId=${escape(session.workId)}")
                appendLine("workUrl=${escape(session.workUrl)}")
                appendLine("title=${escape(session.title)}")
                appendLine("author=${escape(session.author)}")
                appendLine("startIndex=${session.startIndex}")
                appendLine("loadedThrough=${session.loadedThrough}")
                appendLine("prefetchAhead=${session.prefetchAhead.coerceAtLeast(0)}")
                appendLine("keepBehind=${session.keepBehind.coerceAtLeast(0)}")
                appendLine("pinnedRanges=${encodeRanges(session.pinnedRanges)}")
            },
        )
        File(dir, "toc.txt").writeText(
            session.toc.joinToString("\n") { "${escape(it.title)}\t${escape(it.url)}" },
        )
    }

    fun writeSplash(dir: File, splash: PluginSplashMeta) {
        dir.mkdirs()
        File(dir, "splash.txt").writeText(
            buildString {
                appendLine("v2")
                appendLine("synopsis=${escape(splash.synopsis)}")
                appendLine("tags=${escape(splash.tags.joinToString("|"))}")
                appendLine("views=${splash.views?.toString().orEmpty()}")
                appendLine("rating=${escape(splash.rating)}")
                appendLine("status=${escape(splash.status)}")
                appendLine("cover=${escape(splash.cover)}")
            },
        )
        writeCard(dir, splash.card)
    }

    /** Persist (or clear) the apiVersion 2 card slots next to the splash. */
    fun writeCard(dir: File, card: PluginCard?) {
        val file = File(dir, "card.json")
        if (card == null || card.isEmpty) {
            file.delete()
            return
        }
        dir.mkdirs()
        file.writeText(PluginJson.cardToJson(card).toString())
    }

    fun readCard(dir: File): PluginCard? {
        val file = File(dir, "card.json")
        if (!file.exists()) return null
        return runCatching { PluginJson.card(JSONObject(file.readText())) }.getOrNull()
    }

    fun readSplash(dir: File): PluginSplashMeta? {
        val file = File(dir, "splash.txt")
        if (!file.exists()) return null
        val fields = readFields(file)
        return PluginSplashMeta(
            synopsis = fields["synopsis"].orEmpty(),
            tags = fields["tags"].orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() },
            views = fields["views"]?.toLongOrNull(),
            // v1 (built-in Royal Road) keys: ratingLabel / coverUrl.
            rating = fields["rating"] ?: fields["ratingLabel"].orEmpty(),
            status = fields["status"].orEmpty(),
            cover = fields["cover"] ?: fields["coverUrl"].orEmpty(),
            card = readCard(dir),
        )
    }

    /**
     * Reads meta + ToC. v1 files from the built-in Royal Road plugin use `fictionId` /
     * `fictionUrl`; [fallbackPluginId] and [fallbackBookId] fill fields v1 did not store.
     */
    fun read(root: File, workId: String, fallbackPluginId: String, fallbackBookId: String): PluginReadSession? {
        val dir = dir(root, workId)
        val meta = File(dir, "meta.txt")
        val tocFile = File(dir, "toc.txt")
        if (!meta.exists() || !tocFile.exists()) return null
        val fields = readFields(meta)
        val toc = tocFile.readLines().mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i < 0) return@mapNotNull null
            PluginChapterRef(title = unescape(line.substring(0, i)), url = unescape(line.substring(i + 1)))
        }
        if (toc.isEmpty()) return null
        return PluginReadSession(
            bookId = fields["bookId"] ?: fallbackBookId,
            pluginId = fields["pluginId"] ?: fallbackPluginId,
            workId = fields["workId"] ?: fields["fictionId"] ?: workId,
            workUrl = fields["workUrl"] ?: fields["fictionUrl"].orEmpty(),
            title = fields["title"] ?: workId,
            author = fields["author"].orEmpty(),
            toc = toc,
            startIndex = fields["startIndex"]?.toIntOrNull() ?: 0,
            loadedThrough = fields["loadedThrough"]?.toIntOrNull() ?: -1,
            chapters = emptyList(),
            prefetchAhead = fields["prefetchAhead"]?.toIntOrNull() ?: PluginCachePolicy.DEFAULT_AHEAD,
            keepBehind = fields["keepBehind"]?.toIntOrNull() ?: PluginCachePolicy.DEFAULT_BEHIND,
            pinnedRanges = decodeRanges(fields["pinnedRanges"].orEmpty()),
        )
    }

    fun writeChapter(dir: File, index: Int, title: String, text: String) {
        val folder = File(dir, "c").apply { mkdirs() }
        File(folder, "$index.txt").writeText("$title\n\n$text")
    }

    fun readChapterText(dir: File, index: Int): Pair<String, String>? {
        val file = File(dir, "c/$index.txt")
        if (!file.exists()) return null
        val raw = file.readText()
        val split = raw.indexOf("\n\n")
        if (split < 0) return file.nameWithoutExtension to raw.trim()
        return raw.substring(0, split).trim() to raw.substring(split + 2).trim()
    }

    fun hasChapter(dir: File, index: Int): Boolean = File(dir, "c/$index.txt").exists()

    fun cachedChapterCount(dir: File, tocSize: Int): Int = cachedChapterIndices(dir, tocSize).size

    fun cachedChapterIndices(dir: File, tocSize: Int): Set<Int> {
        if (tocSize <= 0) return emptySet()
        val out = LinkedHashSet<Int>()
        for (i in 0 until tocSize) {
            if (hasChapter(dir, i)) out += i
        }
        return out
    }

    fun deleteChapter(dir: File, index: Int) {
        File(dir, "c/$index.txt").delete()
    }

    /** Delete chapter bodies whose indices are not in [desired]. Returns how many files were removed. */
    fun pruneChaptersOutside(dir: File, tocSize: Int, desired: Set<Int>): Int {
        if (tocSize <= 0) return 0
        var removed = 0
        val folder = File(dir, "c")
        folder.listFiles()?.forEach { file ->
            val idx = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
            if (idx < 0 || idx >= tocSize || idx !in desired) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    fun deleteSession(root: File, workId: String) {
        dir(root, workId).deleteRecursively()
    }

    /** Window around [locus] plus pinned ranges, clipped to the ToC. */
    fun desiredChapterIndices(locus: Int, tocSize: Int, policy: PluginCachePolicy): Set<Int> {
        if (tocSize <= 0) return emptySet()
        val l = locus.coerceIn(0, tocSize - 1)
        val windowStart = (l - policy.keepBehind.coerceAtLeast(0)).coerceAtLeast(0)
        val windowEnd = (l + policy.prefetchAhead.coerceAtLeast(0)).coerceAtMost(tocSize - 1)
        val desired = LinkedHashSet<Int>()
        for (i in windowStart..windowEnd) desired += i
        for (range in policy.pinnedRanges) {
            val from = range.first.coerceAtLeast(0)
            val to = range.last.coerceAtMost(tocSize - 1)
            if (from > to) continue
            for (i in from..to) desired += i
        }
        return desired
    }

    fun mergePinnedRanges(ranges: List<IntRange>): List<IntRange> {
        val sorted = ranges.filter { !it.isEmpty() }.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var cur = sorted.first()
        for (r in sorted.drop(1)) {
            if (r.first <= cur.last + 1) {
                cur = cur.first..maxOf(cur.last, r.last)
            } else {
                out += cur
                cur = r
            }
        }
        out += cur
        return out
    }

    fun clipPinnedRanges(ranges: List<IntRange>, tocSize: Int): List<IntRange> {
        if (tocSize <= 0) return emptyList()
        return mergePinnedRanges(
            ranges.mapNotNull { range ->
                val from = range.first.coerceAtLeast(0)
                val to = range.last.coerceAtMost(tocSize - 1)
                if (from > to) null else from..to
            },
        )
    }

    fun encodeRanges(ranges: List<IntRange>): String =
        mergePinnedRanges(ranges).joinToString(",") { "${it.first}-${it.last}" }

    fun decodeRanges(raw: String): List<IntRange> {
        if (raw.isBlank()) return emptyList()
        return mergePinnedRanges(
            raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { token ->
                val parts = token.split('-', limit = 2)
                val start = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val end = parts.getOrNull(1)?.toIntOrNull() ?: start
                if (end < start) null else start..end
            },
        )
    }

    private fun readFields(file: File): Map<String, String> =
        file.readLines()
            .filter { it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to unescape(line.substring(i + 1))
            }

    internal fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    internal fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (value[i + 1]) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    '\\' -> out.append('\\')
                    else -> out.append(c).append(value[i + 1])
                }
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}
