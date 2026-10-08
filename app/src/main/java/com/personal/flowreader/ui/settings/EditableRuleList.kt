package com.personal.flowreader.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt

/** Copy for one [EditableRuleList]. */
internal data class RuleListText(
    val title: String,
    val empty: String,
    val hint: String,
    val editingHint: String = "Drag the handle to change the order. Rules run top to bottom.",
    /** Noun for the delete confirmation, e.g. "filter" → "Delete 2 filters?". */
    val noun: String = "rule",
)

/**
 * Ordered rules. Tap edits, [trailing] holds per-row controls. Holding a rule enters edit mode:
 * drag handles reorder (saved on drop), checkboxes select, Delete removes the selection after a
 * confirmation. Done or Back leaves edit mode. [locked] rows stay after the others, can't be
 * moved, selected or deleted, and are still tappable to edit. Without [onAdd] the header only
 * shows in edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun <T> EditableRuleList(
    rules: List<T>,
    idOf: (T) -> String,
    nameOf: (T) -> String,
    text: RuleListText,
    onAdd: (() -> Unit)?,
    onEdit: (T) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
    locked: (T) -> Boolean = { false },
    footer: String? = null,
    trailing: @Composable RowScope.(T) -> Unit = {},
    content: @Composable (T, Modifier) -> Unit,
) {
    val movable = remember(rules) { rules.filterNot(locked) }
    val pinned = remember(rules) { rules.filter(locked) }
    // Read from drag callbacks that are created once per row and outlive recompositions.
    val currentMovable by rememberUpdatedState(movable)
    val currentOnReorder by rememberUpdatedState(onReorder)
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    val working = remember { mutableStateListOf<T>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    fun enterEdit(firstSelected: String) {
        working.clear()
        working.addAll(movable)
        selected = setOf(firstSelected)
        editing = true
    }

    fun exitEdit() {
        editing = false
        selected = emptySet()
        draggingId = null
        dragOffset = 0f
    }

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    LaunchedEffect(movable) {
        if (!editing) return@LaunchedEffect
        if (movable.isEmpty()) {
            exitEdit()
            return@LaunchedEffect
        }
        if (draggingId == null) {
            working.clear()
            working.addAll(movable)
        }
        val ids = movable.mapTo(HashSet(), idOf)
        selected = selected.filterTo(HashSet()) { it in ids }
    }

    BackHandler(enabled = editing && pendingDelete == null) { exitEdit() }

    if (editing || onAdd != null) {
        RuleListHeader(
            title = text.title,
            editing = editing,
            selectedCount = selected.size,
            allSelected = editing && selected.size == working.size,
            onAdd = onAdd ?: {},
            onToggleAll = {
                selected = if (selected.size == working.size) emptySet() else working.mapTo(HashSet(), idOf)
            },
            onDelete = { pendingDelete = selected },
            onDone = ::exitEdit,
        )
    }

    if (rules.isEmpty()) {
        Text(
            text.empty,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = FlowTokens.Space.S),
        )
    }

    val shown = if (editing) working else movable
    shown.forEach { rule ->
        val id = idOf(rule)
        key(id) {
            val dragging = editing && draggingId == id
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) FlowLayer.ContentLifted.z else FlowLayer.Content.z)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                    .background(
                        if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    )
                    .onSizeChanged { rowHeights[id] = it.height },
            ) {
                RuleRow(
                    onClick = { if (editing) toggle(id) else onEdit(rule) },
                    onLongClick = { if (editing) toggle(id) else enterEdit(id) },
                ) {
                    if (editing) {
                        DragHandle(
                            ruleId = id,
                            working = working,
                            rowHeights = rowHeights,
                            idOf = idOf,
                            draggingId = { next ->
                                if (next == null && draggingId != null) {
                                    val order = working.map(idOf)
                                    if (order != currentMovable.map(idOf)) currentOnReorder(order)
                                }
                                draggingId = next
                            },
                            onDragOffset = { dragOffset = it },
                        )
                    }
                    content(rule, Modifier.weight(1f).padding(end = FlowTokens.Space.S))
                    if (editing) {
                        Checkbox(checked = id in selected, onCheckedChange = { toggle(id) })
                    } else {
                        trailing(rule)
                    }
                }
            }
        }
    }

    pinned.forEach { rule ->
        key(idOf(rule)) {
            RuleRow(
                onClick = { if (!editing) onEdit(rule) },
                onLongClick = null,
            ) {
                content(rule, Modifier.weight(1f).padding(end = FlowTokens.Space.S))
                if (!editing) trailing(rule)
            }
        }
    }

    if (rules.isNotEmpty()) {
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = FlowTokens.Space.M),
            )
        }
        FlowHint(
            if (editing) text.editingHint else text.hint,
            Modifier.padding(top = if (footer == null) FlowTokens.Space.M else FlowTokens.Space.XS),
        )
    }

    pendingDelete?.let { ids ->
        val names = movable.filter { idOf(it) in ids }.map(nameOf)
        FlowConfirmCard(
            visible = true,
            title = if (ids.size == 1) "Delete this ${text.noun}?" else "Delete ${ids.size} ${text.noun}s?",
            message = names.joinToString("\n") { "• $it" } + "\n\nThis can't be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDelete(ids)
                exitEdit()
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RuleRow(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    content: @Composable RowScope.() -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = FlowTokens.Space.M),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlowTokens.Alpha.Divider),
        )
    }
}

@Composable
private fun RuleListHeader(
    title: String,
    editing: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onAdd: () -> Unit,
    onToggleAll: () -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (editing) "$selectedCount selected" else title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (editing) {
            FlowTextAction(if (allSelected) "None" else "All", onToggleAll)
            FlowTextAction("Delete", onDelete, enabled = selectedCount > 0, destructive = true)
            FlowTextAction("Done", onDone)
        } else {
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(FlowTokens.Icon.M))
                Spacer(Modifier.width(FlowTokens.Space.XS))
                Text("Add")
            }
        }
    }
}
