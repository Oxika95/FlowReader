package com.personal.flowreader.share

import kotlinx.coroutines.delay

enum class CrawlStop { NoNext, Repeated, Limit, Stopped, Failed }

data class CrawlResult(
    val pages: List<WebArticle>,
    val stop: CrawlStop,
    /** Why a later page failed; the pages before it are kept. */
    val error: String? = null,
)

/**
 * Follows the Next link page by page. The first page must parse (its error is thrown); a later
 * failure ends the crawl with the pages fetched so far.
 */
class WebCrawl(
    private val selectors: ParseSelectors,
    private val limit: Int,
    private val fetchHtml: suspend (String) -> String,
    private val delayMs: Long = 400,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun run(
        startUrl: String,
        stopRequested: () -> Boolean = { false },
        onPage: (count: Int, page: WebArticle) -> Unit = { _, _ -> },
    ): CrawlResult {
        val pages = ArrayList<WebArticle>()
        val seen = HashSet<String>()
        var url = startUrl
        while (true) {
            seen += key(url)
            val page = try {
                WebPageIngest.extractArticle(fetchHtml(url), url, selectors)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (pages.isEmpty()) throw e
                return CrawlResult(pages, CrawlStop.Failed, e.message ?: "Could not fetch $url")
            }
            pages += page
            onPage(pages.size, page)
            val next = page.nextUrl ?: return CrawlResult(pages, CrawlStop.NoNext)
            if (key(next) in seen) return CrawlResult(pages, CrawlStop.Repeated)
            if (pages.size >= limit) return CrawlResult(pages, CrawlStop.Limit)
            if (stopRequested()) return CrawlResult(pages, CrawlStop.Stopped)
            pause(delayMs)
            if (stopRequested()) return CrawlResult(pages, CrawlStop.Stopped)
            url = next
        }
    }

    private fun key(url: String) = url.substringBefore('#').trimEnd('/')

    companion object {
        /** Book title for a crawl: the first page's site title, else its chapter title. */
        fun bookTitle(pages: List<WebArticle>): String {
            val first = pages.first()
            return first.pageTitle.takeIf { it.isNotBlank() && it != "Web page" } ?: first.title
        }

        fun toEpub(pages: List<WebArticle>, sourceUrl: String, cover: EpubWriter.Cover? = null): ByteArray =
            EpubWriter.write(
                title = bookTitle(pages),
                chapters = pages.mapIndexed { i, p ->
                    EpubWriter.Chapter(p.title.ifBlank { "Chapter ${i + 1}" }, p.paragraphs)
                },
                sourceUrl = sourceUrl,
                cover = cover,
            )
    }
}
