package com.personal.flowreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.share.PickedSelector
import com.personal.flowreader.share.WebArticle
import com.personal.flowreader.ui.theme.FlowTokens
/** What the importer makes of the downloaded page with the current selections. */
@Composable
internal fun ParsePreview(result: Result<WebArticle>?, rawError: String?, modifier: Modifier) {
    val article = result?.getOrNull()
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        val status = when {
            rawError != null -> "Downloaded copy failed ($rawError)."
            result == null -> "Parsing…"
            article == null -> result.exceptionOrNull()?.message ?: "Parse failed"
            else -> null
        }
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (article == null && result != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        article ?: return@Column
        Text(article.title, style = MaterialTheme.typography.titleMedium)
        article.paragraphs.ifEmpty { listOf(article.text) }.forEach { p ->
            Text(p, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Selector under consideration (new pick, else the saved value) and the picked text or a warning. */
@Composable
internal fun PickInspector(
    active: PickField,
    saved: String,
    picked: PickedSelector?,
    pickedText: String,
    rawError: String?,
    rawLoading: Boolean,
) {
    val selector = picked?.selector ?: saved.ifBlank { null }
    val note: Pair<String, Boolean>? = when {
        rawError != null -> "Downloaded copy failed ($rawError)." to true
        picked != null && !rawLoading && (!picked.inRaw || picked.rawMatches == 0) ->
            "Not in fetched HTML, won't import. Try Wider." to true
        picked != null && pickedText.isNotBlank() -> pickedText to false
        else -> null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
    ) {
        Text(
            selector ?: "${active.label} not set. Tap an element on the page.",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (selector != null) FontFamily.Monospace else null,
            color = if (selector != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        note?.let { (text, error) ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
