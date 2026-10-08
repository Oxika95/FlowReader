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
    /** Chapters kept ahead of the reading position (and behind it when [cleanup] is on). */
    val cacheLevel: Int = PluginCachePolicy.DEFAULT_LEVEL,
    /** Delete chapters more than [cacheLevel] behind the reading position. */
    val cleanup: Boolean = false,
    /** Inclusive chapter-index ranges (Download all) that cleanup never deletes. */
    val pinnedRanges: List<IntRange> = emptyList(),
    /** New-chapter notifications; null = default (on while the story is on a syncable list). */
    val notify: Boolean? = null,
) {
    fun nextIndex(): Int? {
        val next = loadedThrough + 1
        return next.takeIf { it in toc.indices }
    }

    val cachePolicy: PluginCachePolicy
        get() = PluginCachePolicy(cacheLevel, cleanup, pinnedRanges)
}

/** Stream / offline retention policy for chapter bodies on disk. */
data class PluginCachePolicy(
    val cacheLevel: Int = DEFAULT_LEVEL,
    val cleanup: Boolean = false,
    val pinnedRanges: List<IntRange> = emptyList(),
) {
    companion object {
        const val DEFAULT_LEVEL = 1
    }
}

/** Work-page metadata for the story media card (chapter bodies not required). */
data class PluginSplashMeta(
    val synopsis: String = "",
    val tags: List<String> = emptyList(),
    val cover: String = "",
    /** Card slots (`card.json`); null when the plugin fills none. */
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
                appendLine("cacheLevel=${session.cacheLevel.coerceAtLeast(0)}")
                appendLine("cleanup=${session.cleanup}")
                appendLine("pinnedRanges=${encodeRanges(session.pinnedRanges)}")
                session.notify?.let { appendLine("notify=$it") }
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
                appendLine("cover=${escape(splash.cover)}")
            },
        )
        writeCard(dir, splash.card)
    }

    /** Persist (or clear) the card slots next to the splash. */
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
            cover = fields["cover"].orEmpty(),
            card = readCard(dir),
        )
    }

    /** Reads meta + ToC; null when either file is missing or meta lacks its ids. */
    fun read(root: File, workId: String): PluginReadSession? {
        val dir = dir(root, workId)
        val meta = File(dir, "meta.txt")
        val tocFile = File(dir, "toc.txt")
        if (!meta.exists() || !tocFile.exists()) return null
        val fields = readFields(meta)
        val bookId = fields["bookId"] ?: return null
        val pluginId = fields["pluginId"] ?: return null
        val toc = tocFile.readLines().mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i < 0) return@mapNotNull null
            PluginChapterRef(title = unescape(line.substring(0, i)), url = unescape(line.substring(i + 1)))
        }
        if (toc.isEmpty()) return null
        return PluginReadSession(
            bookId = bookId,
            pluginId = pluginId,
            workId = fields["workId"] ?: workId,
            workUrl = fields["workUrl"].orEmpty(),
            title = fields["title"] ?: workId,
            author = fields["author"].orEmpty(),
            toc = toc,
            startIndex = fields["startIndex"]?.toIntOrNull() ?: 0,
            loadedThrough = fields["loadedThrough"]?.toIntOrNull() ?: -1,
            chapters = emptyList(),
            cacheLevel = fields["cacheLevel"]?.toIntOrNull() ?: PluginCachePolicy.DEFAULT_LEVEL,
            cleanup = fields["cleanup"]?.toBooleanStrictOrNull() ?: false,
            pinnedRanges = decodeRanges(fields["pinnedRanges"].orEmpty()),
            notify = fields["notify"]?.toBooleanStrictOrNull(),
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

    /** Delete the chapter bodies in [indices]. Returns how many files were removed. */
    fun deleteChapters(dir: File, indices: Collection<Int>): Int =
        indices.count { File(dir, "c/$it.txt").delete() }

    fun deleteSession(root: File, workId: String) {
        dir(root, workId).deleteRecursively()
    }

    /** Chapters to have on disk: [locus] through [PluginCachePolicy.cacheLevel] ahead, plus pins. */
    fun fetchIndices(locus: Int, tocSize: Int, policy: PluginCachePolicy): Set<Int> {
        if (tocSize <= 0) return emptySet()
        val l = locus.coerceIn(0, tocSize - 1)
        val end = (l + policy.cacheLevel.coerceAtLeast(0)).coerceAtMost(tocSize - 1)
        val desired = LinkedHashSet<Int>()
        for (i in l..end) desired += i
        for (range in policy.pinnedRanges) {
            val from = range.first.coerceAtLeast(0)
            val to = range.last.coerceAtMost(tocSize - 1)
            if (from > to) continue
            for (i in from..to) desired += i
        }
        return desired
    }

    /**
     * Cached chapters cleanup deletes: more than [PluginCachePolicy.cacheLevel] behind [locus]
     * and not pinned, or past the ToC. Nothing when cleanup is off (except past-ToC files).
     */
    fun pruneIndices(cached: Collection<Int>, locus: Int, tocSize: Int, policy: PluginCachePolicy): Set<Int> {
        val keepFrom = locus - policy.cacheLevel.coerceAtLeast(0)
        return cached.filterTo(LinkedHashSet()) { i ->
            when {
                i < 0 || i >= tocSize -> true
                !policy.cleanup -> false
                policy.pinnedRanges.any { i in it } -> false
                else -> i < keepFrom
            }
        }
    }

    /** Every chapter body file index in [dir], including any past the ToC. */
    fun chapterFileIndices(dir: File): Set<Int> =
        File(dir, "c").listFiles()?.mapNotNullTo(LinkedHashSet()) { it.nameWithoutExtension.toIntOrNull() }.orEmpty()

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
