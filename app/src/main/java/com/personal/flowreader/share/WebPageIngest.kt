package com.personal.flowreader.share

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Selector
import java.util.concurrent.TimeUnit

data class WebArticle(
    val title: String,
    val text: String,
    val url: String,
    val paragraphs: List<String> = emptyList(),
    /** The page's `<title>` / og:title, independent of the Title selector. */
    val pageTitle: String = title,
    val prevUrl: String? = null,
    val nextUrl: String? = null,
    val diagnostics: ParseDiagnostics? = null,
)

/** Custom parser fields; blank fields are unused. A null [body] keeps the built-in content heuristics. */
data class ParseSelectors(
    val title: String? = null,
    val body: String? = null,
    val prev: String? = null,
    val next: String? = null,
    val remove: String? = null,
) {
    companion object {
        fun of(
            title: String? = null,
            body: String? = null,
            prev: String? = null,
            next: String? = null,
            remove: String? = null,
        ) = ParseSelectors(
            title = title.clean(),
            body = body.clean(),
            prev = prev.clean(),
            next = next.clean(),
            remove = remove.clean(),
        )

        private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}

/** What each Custom field matched on one page (Test preview, picker). */
data class ParseDiagnostics(
    val custom: Boolean,
    val bodyMatches: Int = 0,
    val titleMatches: Int = 0,
    val prevUrl: String? = null,
    val nextUrl: String? = null,
    val prevSet: Boolean = false,
    val nextSet: Boolean = false,
    val removeApplied: Int = 0,
    val removeSkipped: List<String> = emptyList(),
) {
    fun summary(): String {
        if (!custom) return "Default heuristics"
        val parts = ArrayList<String>()
        parts += "Body $bodyMatches"
        parts += "Title $titleMatches"
        if (prevSet) parts += "Previous ${prevUrl?.let { "→ $it" } ?: "not found"}"
        if (nextSet) parts += "Next ${nextUrl?.let { "→ $it" } ?: "not found"}"
        val total = removeApplied + removeSkipped.size
        if (total > 0) {
            parts += buildString {
                append("Remove $removeApplied/$total")
                if (removeSkipped.isNotEmpty()) {
                    append(" (skipped ")
                    append(removeSkipped.joinToString(", ") { "\"$it\"" })
                    append(')')
                }
            }
        }
        return parts.joinToString(" · ")
    }
}

object WebPageIngest {
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalArgumentException("Could not fetch page (${response.code})")
            }
            response.body?.string().orEmpty()
        }
        if (html.isBlank()) throw IllegalArgumentException("Empty page")
        return html
    }

    /** [selectors] null = Default parser (heuristics); non-null = Custom fields. */
    fun fetchArticle(url: String, selectors: ParseSelectors? = null): WebArticle =
        extractArticle(fetchHtml(url), url, selectors)

    /** Parse already-fetched HTML (also used by unit tests / Test preview / crawl). */
    fun extractArticle(html: String, url: String, selectors: ParseSelectors? = null): WebArticle {
        val doc = Jsoup.parse(html, url)
        return if (selectors == null) extractDefault(doc, url) else extractCustom(doc, url, selectors)
    }

    private fun extractDefault(doc: Document, url: String): WebArticle {
        doc.select("script, style, nav, footer, aside, noscript, iframe").remove()
        val root = doc.selectFirst("article")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst("main")
            ?: doc.body()
            ?: doc
        // Paragraphs survive as blank-line breaks, so the saved text reflows and splits into chapters.
        val paragraphs = HtmlParagraphs.of(root)
        val text = paragraphs.joinToString("\n\n")
        if (text.isBlank()) throw IllegalArgumentException("No text content found on page")
        return WebArticle(
            title = fallbackTitle(doc),
            text = text,
            url = url,
            paragraphs = paragraphs,
            diagnostics = ParseDiagnostics(custom = false),
        )
    }

    private fun extractCustom(doc: Document, url: String, sel: ParseSelectors): WebArticle {
        doc.select("script, style, noscript, iframe").remove()

        val titleHits = sel.title?.let { selectOrNull(doc, it) }.orEmpty()
        val title = titleHits.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() }
            ?: fallbackTitle(doc)
        val prevUrl = sel.prev?.let { linkOf(doc, it, url) }
        val nextUrl = sel.next?.let { linkOf(doc, it, url) }

        val roots = if (sel.body == null) {
            listOf(doc.selectFirst("article") ?: doc.selectFirst("main") ?: doc.body() ?: doc)
        } else {
            val hits = try {
                doc.select(sel.body)
            } catch (e: Selector.SelectorParseException) {
                throw IllegalArgumentException("Body selector is invalid: ${e.message}")
            }
            if (hits.isEmpty()) throw IllegalArgumentException("Body selector matched nothing")
            outermost(hits)
        }

        var applied = 0
        val skipped = ArrayList<String>()
        sel.remove?.let { raw ->
            val rootSet = roots.toHashSet()
            for (entry in CssList.split(raw)) {
                // Matched against the whole page so entries can name ancestors outside the body.
                val hits = runCatching { doc.select(entry) }.getOrNull()
                if (hits == null) {
                    skipped += entry
                    continue
                }
                applied++
                hits.filter { el -> el !in rootSet && el.parents().any { it in rootSet } }.forEach(Element::remove)
            }
        }

        val paragraphs = roots.flatMap(HtmlParagraphs::of)
        val text = paragraphs.joinToString("\n\n")
        if (text.isBlank()) throw IllegalArgumentException("No text content found on page")
        return WebArticle(
            title = title.trim(),
            text = text,
            url = url,
            paragraphs = paragraphs,
            pageTitle = fallbackTitle(doc).trim(),
            prevUrl = prevUrl,
            nextUrl = nextUrl,
            diagnostics = ParseDiagnostics(
                custom = true,
                bodyMatches = roots.size,
                titleMatches = titleHits.size,
                prevUrl = prevUrl,
                nextUrl = nextUrl,
                prevSet = sel.prev != null,
                nextSet = sel.next != null,
                removeApplied = applied,
                removeSkipped = skipped,
            ),
        )
    }

    /** Absolute link of the first match: its own href, the closest link around it, or the first link inside. */
    internal fun linkOf(doc: Document, css: String, pageUrl: String): String? {
        val el = selectOrNull(doc, css)?.firstOrNull() ?: return null
        val link = el.takeIf { it.hasAttr("href") }
            ?: el.closest("a[href]")
            ?: el.selectFirst("a[href]")
            ?: return null
        val href = link.absUrl("href").takeIf { it.startsWith("http") } ?: return null
        return href.takeUnless { it.substringBefore('#') == pageUrl.substringBefore('#') }
    }

    private fun selectOrNull(doc: Document, css: String): List<Element>? =
        runCatching { doc.select(css).toList() }.getOrNull()

    private fun outermost(hits: List<Element>): List<Element> {
        val set = hits.toHashSet()
        return hits.filter { el -> el.parents().none { it in set } }
    }

    private fun fallbackTitle(doc: Document): String =
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: "Web page"
}

/**
 * Comma-separated selector lists, split on commas outside quotes and `(...)` (e.g. `:not(.a, .b)`).
 * An unclosed quote or parenthesis splits on every comma, so one broken entry can't swallow the rest.
 */
object CssList {
    fun split(raw: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var depth = 0
        var quote: Char? = null
        for (ch in raw) {
            when {
                quote != null -> { cur.append(ch); if (ch == quote) quote = null }
                ch == '"' || ch == '\'' -> { quote = ch; cur.append(ch) }
                ch == '(' -> { depth++; cur.append(ch) }
                ch == ')' -> { depth = (depth - 1).coerceAtLeast(0); cur.append(ch) }
                ch == ',' && depth == 0 -> { out += cur.toString(); cur.setLength(0) }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        val parts = if (depth != 0 || quote != null) raw.split(',') else out
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun join(entries: List<String>): String = entries.joinToString(", ")
}
