package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Chapter
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginChapterRef
import com.personal.flowreader.plugin.api.PluginJson
import java.io.File
import org.json.JSONObject
import java.security.MessageDigest

/**
 * One plugin story: the plugin's data from disk ([title] is the plugin's title) plus the user's
 * settings from [PluginStoryEntity], and the chapters [startIndex]..[loadedThrough] opened in memory.
 */
data class PluginReadSession(
    val bookId: String,
    val pluginId: String,
    val workId: String,
    val workUrl: String,
    val title: String,
    val author: String,
    val toc: List<PluginChapterRef>,
    val startIndex: Int = 0,
    var loadedThrough: Int = -1,
    var chapters: List<Chapter> = emptyList(),
    /** Chapters kept ahead of the reading position (and behind it when [cleanup] is on). */
    val cacheLevel: Int = PluginCachePolicy.DEFAULT_LEVEL,
    /** Delete chapters more than [cacheLevel] behind the reading position. */
    val cleanup: Boolean = false,
    /** Download all: every chapter is kept and fetched. */
    val keepAll: Boolean = false,
    /** New-chapter notifications; null = default (on while the story is on a `notifyDefault` list). */
    val notify: Boolean? = null,
    /** User's name for the story; blank = [title]. */
    val titleOverride: String = "",
    /** Chapters added by update checks since the story was last opened in the reader. */
    val newChapters: Int = 0,
) {
    val displayTitle: String get() = titleOverride.ifBlank { title }

    val cachePolicy: PluginCachePolicy
        get() = PluginCachePolicy(cacheLevel, cleanup, keepAll)
}

/** Stream / offline retention policy for chapter bodies on disk. */
data class PluginCachePolicy(
    val cacheLevel: Int = DEFAULT_LEVEL,
    val cleanup: Boolean = false,
    val keepAll: Boolean = false,
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

/**
 * Plain-text files under `{dataDir}/{workDir}/`: `meta.txt` and `toc.txt` (plugin data, written
 * only when a ToC is stored), `splash.txt`, `card.json`, and `c/{chapterKey(url)}.txt` bodies.
 */
object PluginSessionStore {
    private val SAFE_DIR = Regex("^[A-Za-z0-9._-]{1,80}$")

    /** Filesystem-safe folder for a work id (ids with other characters are hashed). */
    fun workDirName(workId: String): String {
        if (SAFE_DIR.matches(workId) && !workId.startsWith("_") && !workId.startsWith(".")) return workId
        return "w_" + sha1(workId)
    }

    fun dir(root: File, workId: String): File = File(root, workDirName(workId))

    /** File name (without `.txt`) of the body of the chapter at [url]. */
    fun chapterKey(url: String): String = sha1(url)

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Write `meta.txt` and `toc.txt` (the plugin's data only; user settings live in the plugin DB). */
    fun writeMeta(dir: File, session: PluginReadSession) {
        dir.mkdirs()
        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("v3")
                appendLine("bookId=${session.bookId}")
                appendLine("pluginId=${session.pluginId}")
                appendLine("workId=${escape(session.workId)}")
                appendLine("workUrl=${escape(session.workUrl)}")
                appendLine("title=${escape(session.title)}")
                appendLine("author=${escape(session.author)}")
            },
        )
        File(dir, "toc.txt").writeText(
            session.toc.joinToString("\n") {
                "${escape(it.title)}\t${escape(it.url)}" + if (it.locked) "\tlocked" else ""
            },
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

    /** Reads meta + ToC (user settings at their defaults); null when either file is missing or meta lacks its ids. */
    fun read(root: File, workId: String): PluginReadSession? {
        val dir = dir(root, workId)
        val meta = File(dir, "meta.txt")
        val tocFile = File(dir, "toc.txt")
        if (!meta.exists() || !tocFile.exists()) return null
        val fields = readFields(meta)
        val bookId = fields["bookId"] ?: return null
        val pluginId = fields["pluginId"] ?: return null
        val toc = tocFile.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            PluginChapterRef(
                title = unescape(parts[0]),
                url = unescape(parts[1]),
                locked = parts.getOrNull(2) == "locked",
            )
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
        )
    }

    private fun chapterFile(dir: File, url: String): File = File(dir, "c/${chapterKey(url)}.txt")

    fun writeChapter(dir: File, url: String, title: String, text: String) {
        File(dir, "c").mkdirs()
        chapterFile(dir, url).writeText("$title\n\n$text")
    }

    fun readChapterText(dir: File, url: String): Pair<String, String>? {
        val file = chapterFile(dir, url)
        if (!file.exists()) return null
        val raw = file.readText()
        val split = raw.indexOf("\n\n")
        if (split < 0) return "" to raw.trim()
        return raw.substring(0, split).trim() to raw.substring(split + 2).trim()
    }

    fun hasChapter(dir: File, url: String): Boolean = chapterFile(dir, url).exists()

    fun cachedChapterCount(dir: File, toc: List<PluginChapterRef>): Int = cachedChapterIndices(dir, toc).size

    /** ToC indices whose body is on disk. */
    fun cachedChapterIndices(dir: File, toc: List<PluginChapterRef>): Set<Int> {
        val stored = storedKeys(dir)
        if (stored.isEmpty()) return emptySet()
        return toc.indices.filterTo(LinkedHashSet()) { chapterKey(toc[it].url) in stored }
    }

    private fun storedKeys(dir: File): Set<String> =
        File(dir, "c").listFiles()?.mapTo(HashSet()) { it.nameWithoutExtension }.orEmpty()

    /** Delete the bodies of [indices] in [toc] and any body whose chapter left [toc]. Returns how many files were removed. */
    fun deleteChapters(dir: File, toc: List<PluginChapterRef>, indices: Collection<Int>): Int {
        val keep = toc.indices.filterNot { it in indices }.mapTo(HashSet()) { chapterKey(toc[it].url) }
        return File(dir, "c").listFiles()?.count { it.nameWithoutExtension !in keep && it.delete() } ?: 0
    }

    fun deleteSession(root: File, workId: String) {
        dir(root, workId).deleteRecursively()
    }

    /** Chapters to have on disk: [locus] through [PluginCachePolicy.cacheLevel] ahead, or all of them with keep-all. */
    fun fetchIndices(locus: Int, tocSize: Int, policy: PluginCachePolicy): Set<Int> {
        if (tocSize <= 0) return emptySet()
        val l = locus.coerceIn(0, tocSize - 1)
        val end = (l + policy.cacheLevel.coerceAtLeast(0)).coerceAtMost(tocSize - 1)
        val desired = LinkedHashSet<Int>()
        for (i in l..end) desired += i
        if (policy.keepAll) for (i in 0 until tocSize) desired += i
        return desired
    }

    /**
     * Cached chapters cleanup deletes: more than [PluginCachePolicy.cacheLevel] behind [locus].
     * Nothing when cleanup is off or keep-all is on.
     */
    fun pruneIndices(cached: Collection<Int>, locus: Int, policy: PluginCachePolicy): Set<Int> {
        if (!policy.cleanup || policy.keepAll) return emptySet()
        val keepFrom = locus - policy.cacheLevel.coerceAtLeast(0)
        return cached.filterTo(LinkedHashSet()) { it < keepFrom }
    }

    /** Saved chapter of a progress row in [toc]: the stored chapter URL, else the index (rows without one). */
    fun savedChapter(toc: List<PluginChapterRef>, chapterHref: String, chapterIndex: Int): Int {
        val byHref = if (chapterHref.isBlank()) -1 else toc.indexOfFirst { it.url == chapterHref }
        val i = if (byHref >= 0) byHref else chapterIndex
        return i.coerceIn(0, (toc.size - 1).coerceAtLeast(0))
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
