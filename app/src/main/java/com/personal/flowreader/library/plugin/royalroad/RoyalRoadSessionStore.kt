package com.personal.flowreader.library.plugin.royalroad

import com.personal.flowreader.data.Chapter
import java.io.File

/** On-disk ToC + cached chapter bodies for an in-progress Royal Road serial. */
data class RoyalRoadReadSession(
    val bookId: String,
    val fictionId: String,
    val fictionUrl: String,
    val title: String,
    val author: String,
    val toc: List<ChapterLink>,
    val startIndex: Int,
    var loadedThrough: Int,
    var chapters: List<Chapter>,
    val prefetchAhead: Int = RoyalRoadCachePolicy.DEFAULT_AHEAD,
    val keepBehind: Int = RoyalRoadCachePolicy.DEFAULT_BEHIND,
    /** Inclusive chapter-index ranges that survive stream prune. */
    val pinnedRanges: List<IntRange> = emptyList(),
) {
    fun nextIndex(): Int? {
        val next = loadedThrough + 1
        return next.takeIf { it in toc.indices }
    }

    val cachePolicy: RoyalRoadCachePolicy
        get() = RoyalRoadCachePolicy(
            prefetchAhead = prefetchAhead,
            keepBehind = keepBehind,
            pinnedRanges = pinnedRanges,
        )
}

/** Stream / offline retention policy for chapter bodies on disk. */
data class RoyalRoadCachePolicy(
    val prefetchAhead: Int = DEFAULT_AHEAD,
    val keepBehind: Int = DEFAULT_BEHIND,
    val pinnedRanges: List<IntRange> = emptyList(),
) {
    companion object {
        const val DEFAULT_AHEAD = 1
        const val DEFAULT_BEHIND = 1
    }
}

/** Fiction-page metadata for the story splash overlay (bodies not required). */
data class RoyalRoadSplashMeta(
    val synopsis: String = "",
    val tags: List<String> = emptyList(),
    val views: Long? = null,
    val ratingLabel: String = "",
    val status: String = "",
    val coverUrl: String = "",
)

data class RoyalRoadLibraryMeta(
    val author: String,
    val chapterCount: Int,
    val downloadedCount: Int,
)

object RoyalRoadSessionStore {
    fun dir(root: File, fictionId: String): File = File(root, fictionId)

    fun writeMeta(dir: File, session: RoyalRoadReadSession) {
        dir.mkdirs()
        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("v1")
                appendLine("bookId=${session.bookId}")
                appendLine("fictionId=${session.fictionId}")
                appendLine("fictionUrl=${escape(session.fictionUrl)}")
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

    fun writeSplash(dir: File, splash: RoyalRoadSplashMeta) {
        dir.mkdirs()
        File(dir, "splash.txt").writeText(
            buildString {
                appendLine("v1")
                appendLine("synopsis=${escape(splash.synopsis)}")
                appendLine("tags=${escape(splash.tags.joinToString("|"))}")
                appendLine("views=${splash.views?.toString().orEmpty()}")
                appendLine("ratingLabel=${escape(splash.ratingLabel)}")
                appendLine("status=${escape(splash.status)}")
                appendLine("coverUrl=${escape(splash.coverUrl)}")
            },
        )
    }

    fun readSplash(dir: File): RoyalRoadSplashMeta? {
        val file = File(dir, "splash.txt")
        if (!file.exists()) return null
        val fields = file.readLines()
            .filter { it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to unescape(line.substring(i + 1))
            }
        return RoyalRoadSplashMeta(
            synopsis = fields["synopsis"].orEmpty(),
            tags = fields["tags"].orEmpty().split('|').map { it.trim() }.filter { it.isNotEmpty() },
            views = fields["views"]?.toLongOrNull(),
            ratingLabel = fields["ratingLabel"].orEmpty(),
            status = fields["status"].orEmpty(),
            coverUrl = fields["coverUrl"].orEmpty(),
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
        val title = raw.substring(0, split).trim()
        val body = raw.substring(split + 2).trim()
        return title to body
    }

    fun hasChapter(dir: File, index: Int): Boolean = File(dir, "c/$index.txt").exists()

    fun cachedChapterCount(dir: File, tocSize: Int): Int {
        if (tocSize <= 0) return 0
        var n = 0
        for (i in 0 until tocSize) {
            if (hasChapter(dir, i)) n++
        }
        return n
    }

    /** Absolute ToC indices that currently have a body on disk. */
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

    /** Delete chapter bodies whose indices are not in [desired]. Returns how many files removed. */
    fun pruneChaptersOutside(dir: File, tocSize: Int, desired: Set<Int>): Int {
        if (tocSize <= 0) return 0
        var removed = 0
        for (i in 0 until tocSize) {
            if (i in desired) continue
            if (hasChapter(dir, i)) {
                deleteChapter(dir, i)
                removed++
            }
        }
        // Also scrub orphan files beyond current ToC length.
        val folder = File(dir, "c")
        if (folder.isDirectory) {
            folder.listFiles()?.forEach { file ->
                val idx = file.nameWithoutExtension.toIntOrNull() ?: return@forEach
                if (idx < 0 || idx >= tocSize || idx !in desired) {
                    if (file.delete()) removed++
                }
            }
        }
        return removed
    }

    fun deleteSession(root: File, fictionId: String) {
        dir(root, fictionId).deleteRecursively()
    }

    fun read(root: File, fictionId: String): RoyalRoadReadSession? {
        val dir = dir(root, fictionId)
        val meta = File(dir, "meta.txt")
        val tocFile = File(dir, "toc.txt")
        if (!meta.exists() || !tocFile.exists()) return null
        val fields = meta.readLines()
            .filter { it.contains('=') }
            .associate { line ->
                val i = line.indexOf('=')
                line.substring(0, i) to unescape(line.substring(i + 1))
            }
        val toc = tocFile.readLines().mapNotNull { line ->
            val i = line.indexOf('\t')
            if (i < 0) return@mapNotNull null
            ChapterLink(title = unescape(line.substring(0, i)), url = unescape(line.substring(i + 1)))
        }
        if (toc.isEmpty()) return null
        val start = fields["startIndex"]?.toIntOrNull() ?: 0
        val loaded = fields["loadedThrough"]?.toIntOrNull() ?: -1
        return RoyalRoadReadSession(
            bookId = fields["bookId"] ?: RoyalRoadHtml.bookIdFor(fictionId),
            fictionId = fields["fictionId"] ?: fictionId,
            fictionUrl = fields["fictionUrl"].orEmpty(),
            title = fields["title"] ?: "Royal Road",
            author = fields["author"].orEmpty(),
            toc = toc,
            startIndex = start,
            loadedThrough = loaded,
            chapters = emptyList(),
            prefetchAhead = fields["prefetchAhead"]?.toIntOrNull()
                ?: RoyalRoadCachePolicy.DEFAULT_AHEAD,
            keepBehind = fields["keepBehind"]?.toIntOrNull()
                ?: RoyalRoadCachePolicy.DEFAULT_BEHIND,
            pinnedRanges = decodeRanges(fields["pinnedRanges"].orEmpty()),
        )
    }

    fun libraryMeta(root: File, fictionId: String): RoyalRoadLibraryMeta? {
        val session = read(root, fictionId) ?: return null
        val dir = dir(root, fictionId)
        return RoyalRoadLibraryMeta(
            author = session.author,
            chapterCount = session.toc.size,
            downloadedCount = cachedChapterCount(dir, session.toc.size),
        )
    }

    /** Window around [locus] plus pinned ranges, clipped to ToC. */
    fun desiredChapterIndices(
        locus: Int,
        tocSize: Int,
        policy: RoyalRoadCachePolicy,
    ): Set<Int> {
        if (tocSize <= 0) return emptySet()
        val L = locus.coerceIn(0, tocSize - 1)
        val windowStart = (L - policy.keepBehind.coerceAtLeast(0)).coerceAtLeast(0)
        val windowEnd = (L + policy.prefetchAhead.coerceAtLeast(0)).coerceAtMost(tocSize - 1)
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
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges
            .filter { !it.isEmpty() && it.first <= it.last }
            .sortedBy { it.first }
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
            raw.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { token ->
                    val parts = token.split('-', limit = 2)
                    val start = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                    val end = parts.getOrNull(1)?.toIntOrNull() ?: start
                    if (end < start) null else start..end
                },
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
