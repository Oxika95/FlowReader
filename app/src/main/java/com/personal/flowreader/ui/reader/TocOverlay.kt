package com.personal.flowreader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.card.FlowCardHeight
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import kotlinx.coroutines.flow.first

/** Table of contents: a Fill-height fullscreen card with the current chapter centered. */
@Composable
internal fun TocOverlay(
    visible: Boolean,
    chapters: List<String>,
    chapterIndex: Int,
    onChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val safeIndex = if (chapters.isEmpty()) 0 else chapterIndex.coerceIn(0, chapters.lastIndex)

    LaunchedEffect(visible, safeIndex, chapters.size) {
        if (!visible || chapters.isEmpty()) return@LaunchedEffect
        // Wait until the list has a real viewport so centering math is valid.
        snapshotFlow { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset }
            .first { it > 0 }
        listState.scrollToItem(safeIndex)
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == safeIndex }
            ?: return@LaunchedEffect
        val viewport = listState.layoutInfo
        val viewportCenter = (viewport.viewportStartOffset + viewport.viewportEndOffset) / 2
        val itemCenter = item.offset + item.size / 2
        listState.scrollBy((itemCenter - viewportCenter).toFloat())
    }

    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Contents",
        height = FlowCardHeight.Fill,
        scrollable = false,
        bodyPadding = PaddingValues(bottom = FlowTokens.Space.S),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(chapters, key = { index, _ -> index }) { index, name ->
                val current = index == safeIndex
                Text(
                    name,
                    style = FlowType.body,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { selected = current }
                        .clickable { onChapter(index) }
                        .padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.L),
                )
                if (index < chapters.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = FlowTokens.Space.M),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
