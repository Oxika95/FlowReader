package com.personal.flowreader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.settings.EditableRuleList
import com.personal.flowreader.ui.settings.RuleListText
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/**
 * Contents of the Queue stream: Queue items with their chapters nested, live with the Queue.
 * Holding an item enters the Library Queue's edit mode (items only): drag to reorder, select,
 * delete. [currentRow] is the ToC row under the locus; [currentQueId] the item being read.
 */
@Composable
internal fun QueueTocOverlay(
    visible: Boolean,
    items: List<QueueTocItem>,
    currentRow: Int,
    currentQueId: String,
    onRow: (Int) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scroll = rememberScrollState()
    var viewport by remember { mutableStateOf<Rect?>(null) }
    var current by remember { mutableStateOf<Rect?>(null) }
    var centered by remember(visible) { mutableStateOf(false) }

    LaunchedEffect(visible, viewport, current) {
        val v = viewport ?: return@LaunchedEffect
        val c = current ?: return@LaunchedEffect
        if (!visible || centered) return@LaunchedEffect
        scroll.scrollBy(c.center.y - v.center.y)
        centered = true
    }

    val markCurrent: (Boolean) -> Modifier = { isCurrent ->
        if (isCurrent) Modifier.onGloballyPositioned { current = it.boundsInRoot() } else Modifier
    }

    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Contents",
        scrollable = false,
        bodyPadding = PaddingValues(start = FlowTokens.Space.L, end = FlowTokens.Space.L, bottom = FlowTokens.Space.S),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .onGloballyPositioned { viewport = it.boundsInRoot() }
                .verticalScroll(scroll),
        ) {
            EditableRuleList(
                rules = items,
                idOf = { it.queId },
                nameOf = { it.title },
                text = RuleListText(
                    title = "Queue",
                    empty = "Queue is empty.",
                    hint = "Hold an item to reorder or remove.",
                    editingHint = "Drag the handle to change the order. The Queue plays top to bottom.",
                    noun = "item",
                ),
                onAdd = null,
                onEdit = { onRow(it.row) },
                onReorder = onReorder,
                onDelete = onDelete,
                deleteNote = { ids ->
                    if (currentQueId in ids) "Includes the item you're reading; reading moves to the next item." else null
                },
                trailing = { item ->
                    if (item.done) {
                        Text(
                            "Done",
                            style = FlowType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FlowTokens.Space.XS),
                        )
                    }
                },
                below = { item ->
                    item.chapters.forEach { (row, label) ->
                        val isCurrent = row == currentRow
                        Text(
                            label,
                            style = FlowType.label,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(markCurrent(isCurrent))
                                .semantics { selected = isCurrent }
                                .clickable { onRow(row) }
                                .padding(start = FlowTokens.Space.XL, top = FlowTokens.Space.S, bottom = FlowTokens.Space.S),
                        )
                    }
                },
            ) { item, rowModifier ->
                val isCurrent = item.row == currentRow
                Text(
                    item.title,
                    style = FlowType.body,
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = when {
                        isCurrent -> MaterialTheme.colorScheme.primary
                        item.done -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> Color.Unspecified
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = rowModifier
                        .then(markCurrent(isCurrent))
                        .semantics { selected = isCurrent }
                        .padding(start = FlowTokens.Space.XS),
                )
            }
        }
    }
}
