package com.personal.flowreader.share

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

data class WebArticle(
    val title: String,
    val text: String,
    val url: String,
)

object WebPageIngest {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun fetchArticle(
        url: String,
        contentCss: String? = null,
        titleCss: String? = null,
        removeCss: String? = null,
    ): WebArticle {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
            )
            .get()
            .build()
        val html = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalArgumentException("Could not fetch page (${response.code})")
            }
            response.body?.string().orEmpty()
        }
        if (html.isBlank()) throw IllegalArgumentException("Empty page")
        return extractArticle(html, url, contentCss, titleCss, removeCss)
    }

    /** Parse already-fetched HTML (also used by unit tests / Test preview). */
    fun extractArticle(
        html: String,
        url: String,
        contentCss: String? = null,
        titleCss: String? = null,
        removeCss: String? = null,
    ): WebArticle {
        val doc = Jsoup.parse(html, url)
        doc.select("script, style, nav, footer, aside, noscript, iframe").remove()
        val titleFromCss = selectText(doc, titleCss)
        val title = titleFromCss
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: "Web page"
        val root = selectContent(doc, contentCss)
        if (!removeCss.isNullOrBlank()) {
            runCatching { root.select(removeCss).remove() }
        }
        val text = root.text().trim()
        if (text.isBlank()) throw IllegalArgumentException("No text content found on page")
        return WebArticle(title = title.trim(), text = text, url = url)
    }

    private fun selectContent(doc: Document, contentCss: String?): Element {
        if (!contentCss.isNullOrBlank()) {
            val hit = runCatching { doc.selectFirst(contentCss) }.getOrNull()
            if (hit != null) return hit
        }
        return doc.selectFirst("article")
            ?: doc.selectFirst("[role=main]")
            ?: doc.selectFirst("main")
            ?: doc.body()
            ?: doc
    }

    private fun selectText(doc: Document, css: String?): String? {
        if (css.isNullOrBlank()) return null
        val el = runCatching { doc.selectFirst(css) }.getOrNull() ?: return null
        return el.text().trim().takeIf { it.isNotBlank() }
    }
}
