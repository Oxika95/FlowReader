package com.personal.flowreader.ui.library

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowTextField

/** Ask whether (and how far) to follow Next links, then show crawl progress with Stop (keeps pages fetched). */
@Composable
internal fun WebCrawlCards(
    prompt: WebImportRequest?,
    progress: CrawlProgress?,
    onAnswer: (crawl: Boolean, limit: Int) -> Unit,
    onDismissPrompt: () -> Unit,
    onStop: () -> Unit,
) {
    CrawlPromptCard(prompt, onAnswer, onDismissPrompt)
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

@Composable
private fun CrawlPromptCard(
    prompt: WebImportRequest?,
    onAnswer: (crawl: Boolean, limit: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var count by remember(prompt) { mutableStateOf(prompt?.rule?.crawlLimit?.toString().orEmpty()) }
    val limit = CrawlCount.parse(count)
    FlowFullscreenCard(
        visible = prompt != null,
        onDismiss = onDismiss,
        title = "Crawl chapters?",
        variant = FlowCardVariant.Compact,
        footer = {
            FlowActionRow(
                start = { FlowTextAction("This page", { onAnswer(false, 0) }) },
            ) {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Crawl", { limit?.let { onAnswer(true, it) } }, enabled = limit != null)
            }
        },
    ) {
        FlowTextField(
            value = count,
            onValueChange = { v -> count = v.filter(Char::isDigit).take(4) },
            label = "Pages",
            isError = count.isNotEmpty() && limit == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { limit?.let { onAnswer(true, it) } }),
        )
        FlowHint("Follow Next from this page into one book, up to ${ParseRule.MAX_CRAWL_LIMIT} pages. This page imports only the shared page.")
    }
}

/** Crawl prompt page count: 1..[ParseRule.MAX_CRAWL_LIMIT], else null. */
internal object CrawlCount {
    fun parse(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in 1..ParseRule.MAX_CRAWL_LIMIT }
}
