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
) {
    fun nextIndex(): Int? {
        val next = loadedThrough + 1
        return next.takeIf { it in toc.indices }
    }
}

object RoyalRoadSessionStore {
    fun dir(root: File, fictionId: String): File = File(root, fictionId)

    fun writeMeta(dir: File, session: RoyalRoadReadSession) {
        dir.mkdirs()
        File(dir, "meta.txt").writeText(
            buildString {
                appendLine("v1")
                appendLine("bookId=${session.bookId}")
                appendLine("fictionId=${session.fictionId}")
                appendLine("fictionUrl=${session.fictionUrl}")
                appendLine("title=${escape(session.title)}")
                appendLine("author=${escape(session.author)}")
                appendLine("startIndex=${session.startIndex}")
                appendLine("loadedThrough=${session.loadedThrough}")
            },
        )
        File(dir, "toc.txt").writeText(
            session.toc.joinToString("\n") { "${escape(it.title)}\t${it.url}" },
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
            ChapterLink(title = unescape(line.substring(0, i)), url = line.substring(i + 1))
        }
        if (toc.isEmpty()) return null
        val start = fields["startIndex"]?.toIntOrNull() ?: 0
        val loaded = fields["loadedThrough"]?.toIntOrNull() ?: start
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
        )
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}
