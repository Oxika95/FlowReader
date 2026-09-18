package com.personal.flowreader.data

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

object EpubIngest {
    fun read(file: File): BookDoc {
        ZipFile(file).use { zip ->
            val opfPath = findOpf(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opf = zip.entryText(opfPath)
            val opfDoc = Jsoup.parse(opf, "", Parser.xmlParser())
            val title = opfDoc.selectFirst("metadata > dc|title, metadata title, dc|title")
                ?.text()
                ?.ifBlank { file.nameWithoutExtension }
                ?: file.nameWithoutExtension

            val hrefById = HashMap<String, String>()
            opfDoc.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) hrefById[id] = href
            }

            val chapters = ArrayList<Chapter>()
            val itemrefs = opfDoc.select("spine > itemref")
            val hrefs = if (itemrefs.isEmpty()) {
                hrefById.values.toList()
            } else {
                itemrefs.mapNotNull { hrefById[it.attr("idref")] }
            }

            hrefs.forEachIndexed { index, href ->
                val path = resolve(opfDir, href)
                val html = zip.entryTextOrNull(path) ?: return@forEachIndexed
                val doc = Jsoup.parse(html)
                val heading = doc.selectFirst("h1, h2, title")?.text()?.ifBlank { null }
                val blocks = extractBlocks(doc.body()?.html() ?: html, "c${index}")
                if (blocks.isNotEmpty()) {
                    chapters += Chapter(heading ?: "Chapter ${index + 1}", blocks)
                }
            }

            if (chapters.isEmpty()) {
                throw IllegalArgumentException("No readable text in EPUB")
            }
            return BookDoc(title, chapters)
        }
    }

    internal fun extractBlocks(html: String, prefix: String): List<Block> {
        val body = Jsoup.parseBodyFragment(html).body()
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

    private fun findOpf(zip: ZipFile): String {
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
            ?: throw IllegalArgumentException("EPUB missing OPF")
    }

    private fun resolve(dir: String, href: String): String {
        val raw = href.substringBefore('#')
        if (dir.isBlank()) return raw
        return "$dir/$raw".replace("\\", "/").replace("//", "/")
    }

    private fun ZipFile.entryText(name: String): String {
        val entry = getEntry(name) ?: getEntry(name.trimStart('/'))
            ?: throw IllegalArgumentException("Missing $name")
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }

    private fun ZipFile.entryTextOrNull(name: String): String? {
        val entry = getEntry(name) ?: getEntry(name.trimStart('/')) ?: return null
        return getInputStream(entry).bufferedReader().use { it.readText() }
    }
}

object TxtIngest {
    fun read(file: File): BookDoc {
        val text = file.readText()
        return readText(file.nameWithoutExtension, text)
    }

    fun readText(title: String, text: String): BookDoc {
        val paras = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .map { it.trim().replace("\n", " ") }
            .filter { it.isNotEmpty() }
        val blocks = paras.mapIndexed { i, p -> Block("t$i", BlockKind.Paragraph, p) }
        val chapter = Chapter(title, blocks.ifEmpty { listOf(Block("t0", BlockKind.Paragraph, text.trim())) })
        return BookDoc(title, listOf(chapter))
    }
}
