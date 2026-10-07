package com.personal.flowreader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object EpubIngest {
    /** Whole book in memory (tests, tools). The reader loads chapters via [EpubChapterSource]. */
    fun read(file: File): BookDoc {
        val source = EpubChapterSource.open(file)
        val chapters = (0 until source.chapterCount).map { source.load(it) }.filter { it.blocks.isNotEmpty() }
        if (chapters.isEmpty()) {
            throw IllegalArgumentException("No readable text in EPUB")
        }
        return BookDoc(source.title, chapters)
    }

    /** Title from the OPF only (no chapter parsing). */
    fun readTitle(file: File): String = ZipFile(file).use { zip ->
        val opf = Jsoup.parse(entryText(zip, findOpf(zip)), "", Parser.xmlParser())
        titleOf(opf) ?: file.nameWithoutExtension
    }

    internal fun titleOf(opf: Document): String? =
        opf.selectFirst("metadata > dc|title, metadata title, dc|title")?.text()?.ifBlank { null }

    internal fun extractBlocks(html: String, prefix: String): List<Block> =
        extractBlocks(Jsoup.parseBodyFragment(html).body(), prefix)

    internal fun extractBlocks(body: Element, prefix: String): List<Block> {
        val nodes = body.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")
        val blocks = ArrayList<Block>()
        nodes.forEachIndexed { i, el ->
            val text = el.text().trim()
            if (text.isEmpty()) return@forEachIndexed
            val kind = when (el.tagName().lowercase()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> BlockKind.Heading
                "blockquote" -> BlockKind.Quote
                else -> BlockKind.Paragraph
            }
            blocks += Block("$prefix-$i", kind, text)
        }
        if (blocks.isEmpty()) {
            val text = body.text().trim()
            if (text.isNotEmpty()) {
                text.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }
                    .forEachIndexed { i, p ->
                        blocks += Block("$prefix-p$i", BlockKind.Paragraph, p)
                    }
            }
        }
        return blocks
    }

    internal fun findOpf(zip: ZipFile): String {
        val container = entryTextOrNull(zip, "META-INF/container.xml")
        if (container != null) {
            val fullPath = Jsoup.parse(container, "", Parser.xmlParser())
                .selectFirst("rootfile")
                ?.attr("full-path")
            if (!fullPath.isNullOrBlank()) return fullPath
        }
        return zip.entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
            ?: throw IllegalArgumentException("EPUB missing OPF")
    }

    /** Zip path of [href] relative to [dir]; drops the fragment, decodes `%xx`, folds `.`/`..`. */
    internal fun resolve(dir: String, href: String): String {
        val raw = runCatching { URLDecoder.decode(href.substringBefore('#').replace("+", "%2B"), "UTF-8") }
            .getOrDefault(href.substringBefore('#'))
        val joined = if (dir.isBlank() || raw.startsWith("/")) raw else "$dir/$raw"
        val out = ArrayList<String>()
        for (part in joined.replace("\\", "/").split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
                else -> out += part
            }
        }
        return out.joinToString("/")
    }

    internal fun entry(zip: ZipFile, name: String): ZipEntry? =
        zip.getEntry(name) ?: zip.getEntry(name.trimStart('/'))

    internal fun entryText(zip: ZipFile, name: String): String =
        entryTextOrNull(zip, name) ?: throw IllegalArgumentException("Missing $name")

    internal fun entryTextOrNull(zip: ZipFile, name: String): String? {
        val entry = entry(zip, name) ?: return null
        if (entry.size > MAX_ENTRY_BYTES || entry.compressedSize > MAX_ENTRY_BYTES) {
            return null
        }
        return zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    /** Cap per-entry reads so a malformed EPUB cannot OOM the process. */
    private const val MAX_ENTRY_BYTES = 16L * 1024L * 1024L
}

object TxtIngest {
    fun readText(title: String, text: String): BookDoc {
        val blocks = paragraphs(text, "t")
        val chapter = Chapter(title, blocks.ifEmpty { listOf(Block("t0", BlockKind.Paragraph, text.trim())) })
        return BookDoc(title, listOf(chapter))
    }

    /** Blank-line separated paragraphs, single newlines folded to spaces. */
    internal fun paragraphs(text: String, prefix: String): List<Block> =
        text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .map { it.trim().replace("\n", " ") }
            .filter { it.isNotEmpty() }
            .mapIndexed { i, p -> Block("$prefix$i", BlockKind.Paragraph, p) }
}
