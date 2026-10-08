package com.personal.flowreader.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

/** EPUB chapters straight from the zip: one spine entry parsed per [load]. */
class EpubChapterSource private constructor(
    private val file: File,
    override val title: String,
    private val paths: List<String>,
    private val sizes: LongArray,
    private val navTitles: Map<Int, String>,
) : ChapterSource {
    override val chapterCount: Int get() = paths.size

    override fun chapterTitle(index: Int): String =
        navTitles[index] ?: "Chapter ${index + 1}"

    override fun href(index: Int): String = paths.getOrElse(index) { "" }

    override fun indexOfHref(href: String): Int = paths.indexOf(href)

    override fun weight(index: Int): Long = sizes.getOrElse(index) { 1L }

    override fun tocEntries(): List<Pair<Int, String>> =
        if (navTitles.isNotEmpty()) {
            navTitles.entries.sortedBy { it.key }.map { it.key to it.value }
        } else {
            paths.indices.map { it to chapterTitle(it) }
        }

    override fun load(index: Int): Chapter {
        val path = paths.getOrNull(index) ?: return Chapter(chapterTitle(index), emptyList())
        val html = ZipFile(file).use { zip -> EpubIngest.entryTextOrNull(zip, path) }
            ?: return Chapter(chapterTitle(index), emptyList())
        val doc = Jsoup.parse(html)
        val heading = doc.selectFirst("h1, h2, title")?.text()?.ifBlank { null }
        val blocks = EpubIngest.extractBlocks(doc.body() ?: doc, "c$index")
        return Chapter(navTitles[index] ?: heading ?: chapterTitle(index), blocks)
    }

    companion object {
        fun open(file: File): EpubChapterSource = ZipFile(file).use { zip ->
            val opfPath = EpubIngest.findOpf(zip)
            val opfDir = opfPath.substringBeforeLast('/', "")
            val opfDoc = Jsoup.parse(EpubIngest.entryText(zip, opfPath), "", Parser.xmlParser())
            val title = EpubIngest.titleOf(opfDoc) ?: file.nameWithoutExtension

            val items = HashMap<String, ManifestItem>()
            opfDoc.select("manifest > item").forEach { item ->
                val id = item.attr("id")
                val href = item.attr("href")
                if (id.isNotBlank() && href.isNotBlank()) {
                    items[id] = ManifestItem(
                        path = EpubIngest.resolve(opfDir, href),
                        mediaType = item.attr("media-type"),
                        properties = item.attr("properties"),
                    )
                }
            }
            val itemrefs = opfDoc.select("spine > itemref")
            val paths = if (itemrefs.isEmpty()) {
                items.values.map { it.path }
            } else {
                itemrefs.mapNotNull { items[it.attr("idref")]?.path }
            }
            val sizes = LongArray(paths.size) { i ->
                EpubIngest.entry(zip, paths[i])?.size?.takeIf { it > 0 } ?: 1L
            }
            val tocId = opfDoc.selectFirst("spine")?.attr("toc").orEmpty()
            val navTitles = runCatching { readNav(zip, items, tocId, paths) }.getOrDefault(emptyMap())
            EpubChapterSource(file, title, paths, sizes, navTitles)
        }

        private data class ManifestItem(val path: String, val mediaType: String, val properties: String)

        /** EPUB 3 nav document, else EPUB 2 NCX; first label per spine entry wins. */
        private fun readNav(
            zip: ZipFile,
            items: Map<String, ManifestItem>,
            tocId: String,
            paths: List<String>,
        ): Map<Int, String> {
            val spineIndex = paths.withIndex().associate { (i, p) -> p to i }
            val out = LinkedHashMap<Int, String>()
            fun add(baseDir: String, href: String, label: String) {
                val text = label.trim()
                if (href.isBlank() || text.isEmpty()) return
                val index = spineIndex[EpubIngest.resolve(baseDir, href)] ?: return
                out.putIfAbsent(index, text)
            }
            val nav = items.values.firstOrNull { "nav" in it.properties.split(' ') }
            if (nav != null) {
                val html = EpubIngest.entryTextOrNull(zip, nav.path)
                if (html != null) {
                    val doc: Document = Jsoup.parse(html)
                    val toc = doc.select("nav").firstOrNull { it.attr("epub:type") == "toc" }
                        ?: doc.selectFirst("nav")
                    val dir = nav.path.substringBeforeLast('/', "")
                    toc?.select("a[href]")?.forEach { add(dir, it.attr("href"), it.text()) }
                }
            }
            if (out.isNotEmpty()) return out
            val ncx = items[tocId] ?: items.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
                ?: return out
            val xml = EpubIngest.entryTextOrNull(zip, ncx.path) ?: return out
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            val dir = ncx.path.substringBeforeLast('/', "")
            doc.select("navPoint").forEach { point ->
                val label = point.selectFirst("navLabel > text")?.text().orEmpty()
                val src = point.selectFirst("content")?.attr("src").orEmpty()
                add(dir, src, label)
            }
            return out
        }
    }
}
