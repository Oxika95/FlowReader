package com.personal.flowreader.library.plugin.royalroad

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.EpubIngest
import java.net.URI
import java.net.URLEncoder
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Royal Road markup helpers.
 *
 * Selectors follow WebToEpub (`chapter-inner` parent, CSS `display:none` watermarks,
 * `cn…` junk classes) and QuickNovel (fiction list, `window.chapters`, author notes).
 */
object RoyalRoadHtml {
    const val ORIGIN = "https://www.royalroad.com"

    private val HOSTS = setOf("royalroad.com", "www.royalroad.com", "royalroadl.com", "www.royalroadl.com")
    private val FICTION_ID = Regex("""/fiction/(\d+)""")
    private val CHAPTER_ID = Regex("""/chapter/(\d+)""")
    private val FICTION_PREFIX = Regex("""(https?://[^/]+/fiction/\d+/[^/]+)""")
    private val USER_ID = Regex("""window\.royalroad\.userId\s*=\s*(\d+)""")
    private val HIDDEN_CSS = Regex(
        """([.#][A-Za-z][\w-]*)\s*\{[^}]*display\s*:\s*none""",
        RegexOption.IGNORE_CASE,
    )
    private val CN_CLASS = Regex("""^cn[A-Z][A-Za-z0-9]{41}$""")
    private val JSON_UNICODE = Regex("""\\u([0-9a-fA-F]{4})""")

    val browseOrders: List<Pair<String, String>> = listOf(
        "best-rated" to "Best Rated",
        "active-popular" to "Ongoing",
        "complete" to "Complete",
        "weekly-popular" to "Popular this week",
        "latest-updates" to "Latest Updates",
        "new-releases" to "New Releases",
        "trending" to "Trending",
        "rising-stars" to "Rising Stars",
    )

    fun isRoyalRoadUrl(raw: String): Boolean {
        val host = parseUri(raw)?.host?.lowercase() ?: return false
        return host in HOSTS
    }

    fun isChapterUrl(raw: String): Boolean {
        val path = parseUri(raw)?.path ?: return false
        return path.contains("/chapter/")
    }

    fun fictionId(url: String): String? =
        FICTION_ID.find(parseUri(url)?.path ?: url)?.groupValues?.get(1)

    fun chapterId(url: String): String? =
        CHAPTER_ID.find(parseUri(url)?.path ?: url)?.groupValues?.get(1)

    fun fictionUrlFrom(url: String): String? =
        FICTION_PREFIX.find(url.trim())?.groupValues?.get(1)

    fun bookIdFor(fictionId: String): String = "rr:$fictionId"

    fun isPluginBookId(bookId: String): Boolean = bookId.startsWith("rr:")

    fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query.trim(), Charsets.UTF_8.name()).replace("+", "%20")
        return "$ORIGIN/fictions/search?title=$q"
    }

    fun browseUrl(order: String, page: Int): String {
        val slug = order.ifBlank { "best-rated" }
        val p = page.coerceAtLeast(1)
        return "$ORIGIN/fictions/$slug?page=$p"
    }

    fun followsUrl(page: Int = 1): String = "$ORIGIN/fictions/follows?page=${page.coerceAtLeast(1)}"

    fun loginUrl(): String = "$ORIGIN/account/login"

    fun firstChapterUrl(pageHtml: String, pageUrl: String): String? =
        windowChapters(pageHtml, pageUrl).firstOrNull()?.url ?: legacyFirstChapter(pageHtml, pageUrl)

    fun chapterTitle(pageHtml: String): String? {
        val doc = Jsoup.parse(pageHtml)
        return doc.selectFirst("h1.font-white, div.fic-header h1, h1")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun chapterInnerHtml(pageHtml: String): String? {
        val doc = Jsoup.parse(pageHtml)
        val inner = doc.selectFirst("div.chapter-inner")
            ?: doc.selectFirst("div.chapter-content")
            ?: return null
        stripWatermarks(inner, pageHtml)
        stripJunkClasses(inner)
        stripNav(inner)
        val notes = linkedAuthorNotes(doc, inner)
        notes.forEach { stripWatermarks(it, pageHtml); stripJunkClasses(it) }
        val html = buildString {
            append(inner.html())
            notes.forEach { note ->
                append("<hr/>")
                note.selectFirst(".caption-subject")?.text()?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append("<h3>").append(it).append("</h3>")
                }
                append(note.html())
            }
        }
        return html.takeIf { it.isNotBlank() }
    }

    fun blocksFromChapterHtml(innerHtml: String): List<Block> {
        val blocks = EpubIngest.extractBlocks(innerHtml, "rr")
        if (blocks.isNotEmpty()) return blocks
        val text = Jsoup.parseBodyFragment(innerHtml).body().text().trim()
        if (text.isEmpty()) return emptyList()
        return EpubIngest.extractBlocks("<p>${text}</p>", "rr")
    }

    fun blocksToPlainText(blocks: List<Block>): String =
        blocks.joinToString("\n\n") { it.text }.trim()

    fun parseFictionList(pageHtml: String, pageUrl: String): List<FictionListItem> {
        val doc = Jsoup.parse(pageHtml, pageUrl)
        return doc.select("div.fiction-list-item").mapNotNull { item ->
            val a = item.selectFirst("h2.fiction-title a") ?: item.selectFirst("a[href*=/fiction/]")
            val href = a?.attr("abs:href")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = a.text().trim().ifBlank { return@mapNotNull null }
            val author = item.selectFirst("span.author a, h4 a, span.author")
                ?.text()
                ?.removePrefix("by")
                ?.trim()
                .orEmpty()
            val latest = item.select("div.stats span").firstOrNull { it.text().contains("Chapter", ignoreCase = true) }
                ?.text()
                ?.trim()
                .orEmpty()
            val cover = item.selectFirst("img[data-type=cover], figure img, img.img-responsive")
                ?.attr("abs:src")
                ?.takeIf { it.isNotBlank() && !it.contains("nocover", ignoreCase = true) }
                .orEmpty()
            FictionListItem(
                title = title,
                url = href,
                author = author,
                latestChapter = latest,
                coverUrl = cover,
            )
        }
    }

    fun parseFictionPage(pageHtml: String, pageUrl: String): FictionPage {
        val doc = Jsoup.parse(pageHtml, pageUrl)
        val canonical = doc.selectFirst("link[rel=canonical]")?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?: fictionUrlFrom(pageUrl)
            ?: pageUrl
        val title = doc.selectFirst("div.fic-header h1, h1.font-white, h1")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank { "Royal Road" }
        val author = doc.selectFirst("div.fic-header h4 a, h4.font-white a, h4.font-white > span > a")
            ?.text()
            ?.trim()
            .orEmpty()
        val synopsis = doc.selectFirst("div.description div.hidden-content, div.description")
            ?.let { el ->
                el.select("p").joinToString("\n\n") { it.text().trim() }.ifBlank { el.text().trim() }
            }
            .orEmpty()
        val chapters = windowChapters(pageHtml, canonical).ifEmpty {
            legacyChapterLinks(pageHtml, canonical)
        }
        val tags = doc.select("span.tags > a").map { it.text().trim() }.filter { it.isNotEmpty() }
        val status = doc.select("div.col-md-8 > div.margin-bottom-10 > span.label, span.label")
            .map { it.text().trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        val views = doc.select("ul.list-unstyled > li")
            .getOrNull(1)
            ?.text()
            ?.replace(",", "")
            ?.replace(".", "")
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
        val ratingAttr = doc.selectFirst("span.font-red-sunglo")?.attr("data-content")
            ?.takeIf { it.isNotBlank() }
        val ratingLabel = ratingAttr
            ?.substringBefore('/')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { "$it / 5" }
            .orEmpty()
        val coverUrl = doc.selectFirst(
            "div.fic-header img, .cover-art-container img, img.thumbnail",
        )?.attr("abs:src")?.takeIf { it.isNotBlank() }.orEmpty()
        return FictionPage(
            title = title,
            url = canonical,
            author = author,
            synopsis = synopsis,
            fictionId = fictionId(canonical).orEmpty(),
            chapters = chapters,
            tags = tags,
            views = views,
            ratingLabel = ratingLabel,
            status = status,
            coverUrl = coverUrl,
        )
    }

    fun loginToken(html: String): String? {
        val doc = Jsoup.parse(html)
        return doc.selectFirst("form.form-login-details input[name=__RequestVerificationToken]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("input[name=__RequestVerificationToken]")
                ?.attr("value")
                ?.takeIf { it.isNotBlank() }
    }

    /**
     * Bookmark form on a fiction page for [type] (`follow`, `favorite`, or `readlater`).
     * Returns null if the form is not present (already bookmarked / logged out markup).
     */
    fun bookmarkForm(pageHtml: String, pageUrl: String, type: String): FollowForm? {
        val wanted = type.trim().lowercase()
        if (wanted.isEmpty()) return null
        val doc = Jsoup.parse(pageHtml, pageUrl)
        val form = doc.select("form[action*=/fictions/setbookmark/]").firstOrNull { el ->
            el.selectFirst("input[name=type][value=$wanted]") != null
        } ?: return null
        val action = form.attr("abs:action").ifBlank {
            resolve(pageUrl, form.attr("action")).orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null
        val token = form.selectFirst("input[name=__RequestVerificationToken]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return FollowForm(actionUrl = action, token = token, type = wanted)
    }

    /** @deprecated Prefer [bookmarkForm] with type `follow`. */
    fun followForm(pageHtml: String, pageUrl: String): FollowForm? =
        bookmarkForm(pageHtml, pageUrl, "follow")


    fun isLoggedIn(html: String): Boolean {
        val id = USER_ID.find(html)?.groupValues?.get(1)
        if (id != null) return id != "0"
        return html.contains("/account/logout", ignoreCase = true)
    }

    fun looksLikeLoginPage(html: String): Boolean =
        html.contains("form-login-details") && !isLoggedIn(html)

    fun windowChapters(html: String, pageUrl: String): List<ChapterLink> {
        val json = extractJsonArrayAfter(html, "window.chapters") ?: return emptyList()
        return Regex("""\{([^{}]+)\}""")
            .findAll(json)
            .mapNotNull { match ->
                val obj = match.groupValues[1]
                val href = jsonString(obj, "url") ?: return@mapNotNull null
                val url = resolve(pageUrl, unescapeJson(href)) ?: return@mapNotNull null
                val title = jsonString(obj, "title")?.let { unescapeJson(it) }
                    ?.ifBlank { null }
                    ?: url.substringAfterLast('/').replace('-', ' ')
                ChapterLink(title = title, url = url)
            }
            .toList()
    }

    internal fun windowChaptersUrls(html: String, pageUrl: String): List<String> =
        windowChapters(html, pageUrl).map { it.url }

    internal fun unescapeJson(raw: String): String {
        val unicode = JSON_UNICODE.replace(raw) { match ->
            match.groupValues[1].toInt(16).toChar().toString()
        }
        return unicode
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun jsonString(obj: String, key: String): String? =
        Regex(""""$key"\s*:\s*"((?:\\.|[^"\\])*)"""").find(obj)?.groupValues?.get(1)

    private fun legacyFirstChapter(pageHtml: String, pageUrl: String): String? {
        val doc = Jsoup.parse(pageHtml, pageUrl)
        doc.select("tr[data-url]").firstOrNull()
            ?.attr("data-url")
            ?.takeIf { it.isNotBlank() }
            ?.let { return resolve(pageUrl, it) }
        doc.select("table#chapters a[href*=/chapter/], a.btn-primary[href*=/chapter/], a[href*=/chapter/]")
            .firstOrNull()
            ?.attr("abs:href")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return null
    }

    private fun legacyChapterLinks(pageHtml: String, pageUrl: String): List<ChapterLink> {
        val doc = Jsoup.parse(pageHtml, pageUrl)
        val fromTable = doc.select("table#chapters tr[data-url], table#chapters a[href*=/chapter/]")
        if (fromTable.isNotEmpty()) {
            return fromTable.mapNotNull { el ->
                val href = when {
                    el.hasAttr("data-url") -> resolve(pageUrl, el.attr("data-url"))
                    else -> el.attr("abs:href").takeIf { it.isNotBlank() }
                } ?: return@mapNotNull null
                val title = el.text().trim().ifBlank { href.substringAfterLast('/') }
                ChapterLink(title = title, url = href)
            }.distinctBy { it.url }
        }
        return emptyList()
    }

    private fun stripWatermarks(root: Element, pageHtml: String) {
        HIDDEN_CSS.findAll(pageHtml).map { it.groupValues[1] }.distinct().forEach { sel ->
            runCatching { root.select(sel).forEach { it.remove() } }
        }
        root.select("[style]").forEach { el ->
            if (el.attr("style").contains("display:none", ignoreCase = true) ||
                el.attr("style").contains("display: none", ignoreCase = true)
            ) {
                el.remove()
            }
        }
    }

    private fun stripJunkClasses(root: Element) {
        root.select("[class]").forEach { el ->
            el.classNames().filter { CN_CLASS.matches(it) }.forEach { el.removeClass(it) }
        }
    }

    private fun stripNav(root: Element) {
        root.select("a.btn, div.nav-buttons, div.chapter-nav").forEach { it.remove() }
    }

    private fun linkedAuthorNotes(doc: Document, inner: Element): List<Element> {
        val portlets = doc.select("div.author-note-portlet").filter { note ->
            note.parents().none { it === inner }
        }
        if (portlets.isNotEmpty()) return portlets
        return doc.select("div.author-note").filter { note ->
            note.parents().none { it === inner || it.hasClass("author-note-portlet") }
        }
    }

    private fun extractJsonArrayAfter(html: String, marker: String): String? {
        val i = html.indexOf(marker)
        if (i < 0) return null
        val start = html.indexOf('[', i)
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (j in start until html.length) {
            val c = html[j]
            when {
                escape -> escape = false
                inString && c == '\\' -> escape = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '[' -> depth++
                c == ']' -> {
                    depth--
                    if (depth == 0) return html.substring(start, j + 1)
                }
            }
        }
        return null
    }

    private fun parseUri(raw: String): URI? = runCatching { URI(raw.trim()) }.getOrNull()

    private fun resolve(base: String, href: String): String? {
        if (href.isBlank()) return null
        return runCatching { URI(base).resolve(href).toString() }.getOrNull()
    }
}

data class FictionListItem(
    val title: String,
    val url: String,
    val author: String = "",
    val latestChapter: String = "",
    val coverUrl: String = "",
)

data class FollowForm(
    val actionUrl: String,
    val token: String,
    val type: String = "follow",
)

data class FictionPage(
    val title: String,
    val url: String,
    val author: String = "",
    val synopsis: String = "",
    val fictionId: String = "",
    val chapters: List<ChapterLink>,
    val tags: List<String> = emptyList(),
    val views: Long? = null,
    val ratingLabel: String = "",
    val status: String = "",
    val coverUrl: String = "",
)

data class ChapterLink(
    val title: String,
    val url: String,
)
