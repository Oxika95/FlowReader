package com.personal.flowreader.ui.library

import androidx.compose.runtime.Composable
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowTextAction

/** Ask whether to follow Next links, then show crawl progress with Stop (keeps pages fetched). */
@Composable
internal fun WebCrawlCards(
    prompt: WebImportRequest?,
    progress: CrawlProgress?,
    onAnswer: (crawl: Boolean) -> Unit,
    onDismissPrompt: () -> Unit,
    onStop: () -> Unit,
) {
    FlowConfirmCard(
        visible = prompt != null,
        title = "Crawl chapters?",
        message = "Follow Next from this page, up to ${prompt?.rule?.crawlLimit ?: 0} pages, into one book. " +
            "Or import just this page.",
        confirmLabel = "Crawl",
        onConfirm = { onAnswer(true) },
        onDismiss = onDismissPrompt,
        extraActions = { FlowTextAction("This page", { onAnswer(false) }) },
    )
    FlowConfirmCard(
        visible = progress != null,
        title = if (progress?.stopping == true) "Stopping…" else "Crawling…",
        message = progress?.let {
            buildString {
                append("Page ${it.pages} of up to ${it.limit}")
                if (it.lastTitle.isNotBlank()) append("\n").append(it.lastTitle)
            }
        }.orEmpty(),
        confirmLabel = "Stop",
        onConfirm = onStop,
        onDismiss = {},
        dismissLabel = "",
    )
}
