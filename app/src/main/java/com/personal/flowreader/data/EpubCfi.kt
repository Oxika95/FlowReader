package com.personal.flowreader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/**
 * Maps an EPUB CFI (epub.js style) onto [EpubIngest.extractBlocks] indices and back.
 * One mapper for the Audiobookshelf plugin and the local EPUB reader.
 */
object EpubCfi {
    data class Locus(
        val spineIndex: Int,
        val blockIndex: Int = 0,
        val charOffset: Int = 0,
        val anchorText: String = "",
        val fraction: Float = 0f,
    )

    /**
     * Builds `epubcfi(/6/{2*spine+2}!…)` for the block [EpubIngest.extractBlocks] produced from
     * the spine item's HTML. Falls back to the start of the spine item when the block is missing.
     */
    fun write(file: File, locus: Locus): String {
        val source = EpubChapterSource.open(file)
        val spineStep = (locus.spineIndex + 1) * 2
        val path = source.href(locus.spineIndex)
        if (path.isBlank()) return "epubcfi(/6/$spineStep!)"
        val html = ZipFile(file).use { zip -> EpubIngest.entryTextOrNull(zip, path) }.orEmpty()
        val body = Jsoup.parse(html, "", Parser.xmlParser()).body() ?: Jsoup.parseBodyFragment(html).body()
        val nodes = body.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")
        val el = nodes.getOrNull(locus.blockIndex)
        if (el == null) return "epubcfi(/6/$spineStep!)"
        val steps = elementPath(body, el)
        val textLen = el.text().length
        val offset = locus.charOffset.coerceIn(0, textLen)
        // Last step is the target element; append a text-node offset epub.js can parse.
        return "epubcfi(/6/$spineStep!${steps.joinToString("")}:$offset)"
    }

    /**
     * Parses an epub.js CFI onto a locus in [file]. Unknown intra-document steps open the start
     * of that spine item. A missing or invalid CFI returns null.
     */
    fun read(file: File, cfi: String): Locus? {
        val trimmed = cfi.trim()
        if (!trimmed.startsWith("epubcfi(") || !trimmed.endsWith(")")) return null
        val inner = trimmed.removePrefix("epubcfi(").removeSuffix(")")
        val bang = inner.indexOf('!')
        val head = if (bang >= 0) inner.substring(0, bang) else inner
        val tail = if (bang >= 0) inner.substring(bang + 1) else ""
        val spineOdd = Regex("/6/(\\d+)").find(head)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        if (spineOdd % 2 != 0 || spineOdd < 2) return null
        val spineIndex = spineOdd / 2 - 1
        val source = EpubChapterSource.open(file)
        if (spineIndex !in 0 until source.chapterCount) return null
        if (tail.isBlank()) return Locus(spineIndex)
        val path = source.href(spineIndex)
        val html = ZipFile(file).use { zip -> EpubIngest.entryTextOrNull(zip, path) }.orEmpty()
        val body = Jsoup.parse(html, "", Parser.xmlParser()).body() ?: Jsoup.parseBodyFragment(html).body()
        val nodes = body.select("h1, h2, h3, h4, h5, h6, p, li, blockquote, pre")
        val resolved = resolveElement(body, tail)
        if (resolved == null) return Locus(spineIndex)
        val blockIndex = nodes.indexOfFirst { it === resolved.element || it.hasDescendant(resolved.element) }
            .takeIf { it >= 0 }
            ?: nodes.indexOfFirst { resolved.element.hasDescendant(it) }.takeIf { it >= 0 }
            ?: return Locus(spineIndex)
        val block = nodes[blockIndex]
        val text = block.text()
        val charOffset = resolved.offset.coerceIn(0, text.length)
        val anchor = text.drop(charOffset).take(64)
        return Locus(spineIndex, blockIndex, charOffset, anchor)
    }

    private data class Resolved(val element: Element, val offset: Int)

    private fun elementPath(root: Element, target: Element): List<String> {
        val chain = ArrayList<Element>()
        var cur: Element? = target
        while (cur != null && cur !== root) {
            chain.add(0, cur)
            cur = cur.parent()
        }
        return chain.map { el ->
            val parent = el.parent() ?: return@map "/2"
            val index = parent.childNodes().filterIsInstance<Element>().indexOf(el)
            "/${(index + 1) * 2}"
        }
    }

    private fun Element.hasDescendant(other: Element): Boolean =
        other !== this && other.parents().any { it === this }

    private fun resolveElement(root: Element, tail: String): Resolved? {
        var node: Element = root
        val parts = Regex("/(\\d+)(?::(\\d+))?").findAll(tail).toList()
        if (parts.isEmpty()) return null
        for ((i, match) in parts.withIndex()) {
            val step = match.groupValues[1].toIntOrNull() ?: return null
            val offset = match.groupValues[2].toIntOrNull()
            val last = i == parts.lastIndex
            if (step % 2 != 0) return null
            val childIndex = step / 2 - 1
            val children = node.children()
            val next = children.getOrNull(childIndex) ?: return null
            if (last) return Resolved(next, offset ?: 0)
            node = next
        }
        return Resolved(node, 0)
    }
}
