package com.personal.flowreader.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.data.QueueItemEntity
import com.personal.flowreader.ui.design.card.FlowEmptyState
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.settings.EditableRuleList
import com.personal.flowreader.ui.settings.RuleListText
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Library Queue tab: hold-to-edit list; multi-chapter rows expand to their chapters. */
@Composable
internal fun QueTab(
    entries: List<QueueItemEntity>,
    busy: Boolean,
    bottomInset: Dp,
    onOpen: (QueueItemEntity, chapter: Int?) -> Unit,
    onReorder: (List<String>) -> Unit,
    onRemove: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (entries.isEmpty() && !busy) {
            FlowEmptyState("Queue is empty.\nPaste from the clipboard or share text to Flow Reader.")
            return@Box
        }
        val chapters = rememberQueueChapters(entries)
        var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(flowDisplayListPadding(bottomExtra = bottomInset)),
        ) {
            EditableRuleList(
                rules = entries,
                idOf = { it.queId },
                nameOf = { it.title },
                text = RuleListText(
                    title = "Queue",
                    empty = "",
                    hint = "Plays top to bottom as one document. Hold an item to reorder or remove.",
                    editingHint = "Drag the handle to change the order. The Queue plays top to bottom.",
                    noun = "item",
                ),
                onAdd = null,
                onEdit = { onOpen(it, null) },
                onReorder = onReorder,
                onDelete = onRemove,
                trailing = { entry ->
                    if (entry.done) {
                        Text(
                            "Done",
                            style = FlowType.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = FlowTokens.Space.XS),
                        )
                    }
                    if (chapters[entry.queId].orEmpty().isNotEmpty()) {
                        val open = entry.queId in expanded
                        FlowIconButton(
                            icon = if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (open) "Hide chapters" else "Show chapters",
                            onClick = { expanded = if (open) expanded - entry.queId else expanded + entry.queId },
                        )
                    }
                },
                below = { entry ->
                    if (entry.queId in expanded) {
                        QueueChapterList(chapters[entry.queId].orEmpty()) { chapter -> onOpen(entry, chapter) }
                    }
                },
            ) { entry, rowModifier ->
                Text(
                    entry.title,
                    style = FlowType.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (entry.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                    modifier = rowModifier.padding(start = FlowTokens.Space.XS),
                )
            }
        }
    }
}
