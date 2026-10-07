package com.personal.flowreader.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
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
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt

/**
 * Filter rules of one scope. Tap edits, the switch enables. Holding a rule enters edit mode:
 * drag handles reorder (saved on drop), checkboxes select, Delete removes the selection after
 * a confirmation. Done or Back leaves edit mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FilterRuleList(
    rules: List<FilterRule>,
    onAdd: () -> Unit,
    onEdit: (FilterRule) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    val sorted = remember(rules) { rules.sortedBy { it.order } }
    // Read from drag callbacks that are created once per row and outlive recompositions.
    val currentSorted by rememberUpdatedState(sorted)
    val currentOnReorder by rememberUpdatedState(onReorder)
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<Set<String>?>(null) }
    val working = remember { mutableStateListOf<FilterRule>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeights = remember { mutableStateMapOf<String, Int>() }

    fun enterEdit(firstSelected: String) {
        working.clear()
        working.addAll(sorted)
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

    LaunchedEffect(sorted) {
        if (!editing) return@LaunchedEffect
        if (sorted.isEmpty()) {
            exitEdit()
            return@LaunchedEffect
        }
        if (draggingId == null) {
            working.clear()
            working.addAll(sorted)
        }
        val ids = sorted.mapTo(HashSet()) { it.id }
        selected = selected.filterTo(HashSet()) { it in ids }
    }

    BackHandler(enabled = editing && pendingDelete == null) { exitEdit() }

    FilterListHeader(
        editing = editing,
        selectedCount = selected.size,
        allSelected = editing && selected.size == working.size,
        onAdd = onAdd,
        onToggleAll = {
            selected = if (selected.size == working.size) emptySet() else working.mapTo(HashSet()) { it.id }
        },
        onDelete = { pendingDelete = selected },
        onDone = ::exitEdit,
    )

    if (sorted.isEmpty()) {
        Text(
            "No filters yet. Add a rule to replace text in the reader and TTS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = FlowTokens.Space.S),
        )
    }

    val shown = if (editing) working else sorted
    shown.forEach { rule ->
        key(rule.id) {
            val dragging = editing && draggingId == rule.id
            Box(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (dragging) FlowLayer.ContentLifted.z else FlowLayer.Content.z)
                    .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                    .background(
                        if (dragging) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
                    )
                    .onSizeChanged { rowHeights[rule.id] = it.height },
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { if (editing) toggle(rule.id) else onEdit(rule) },
                                onLongClick = { if (editing) toggle(rule.id) else enterEdit(rule.id) },
                            )
                            .padding(vertical = FlowTokens.Space.M),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (editing) {
                            DragHandle(
                                ruleId = rule.id,
                                working = working,
                                rowHeights = rowHeights,
                                idOf = { it.id },
                                draggingId = { id ->
                                    if (id == null && draggingId != null) {
                                        val order = working.map { it.id }
                                        if (order != currentSorted.map { it.id }) currentOnReorder(order)
                                    }
                                    draggingId = id
                                },
                                onDragOffset = { dragOffset = it },
                            )
                        }
                        FilterRuleText(rule, Modifier.weight(1f).padding(end = FlowTokens.Space.S))
                        if (editing) {
                            Checkbox(checked = rule.id in selected, onCheckedChange = { toggle(rule.id) })
                        } else {
                            Switch(checked = rule.enabled, onCheckedChange = { onSetEnabled(rule.id, it) })
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlowTokens.Alpha.Divider),
                    )
                }
            }
        }
    }

    if (sorted.isNotEmpty()) {
        val enabledCount = sorted.count { it.enabled }
        Text(
            "Enabled $enabledCount of ${sorted.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = FlowTokens.Space.M),
        )
        FlowHint(
            if (editing) "Drag the handle to change the order. Rules run top to bottom."
            else "Rules run top to bottom. Hold a rule to reorder or delete.",
            Modifier.padding(top = FlowTokens.Space.XS),
        )
    }

    pendingDelete?.let { ids ->
        val names = sorted.filter { it.id in ids }.map { ruleTitle(it) }
        FlowConfirmCard(
            visible = true,
            title = if (ids.size == 1) "Delete this filter?" else "Delete ${ids.size} filters?",
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

@Composable
private fun FilterListHeader(
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
            if (editing) "$selectedCount selected" else "Rules",
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

@Composable
private fun FilterRuleText(rule: FilterRule, modifier: Modifier) {
    val replacementLabel = rule.replacement.ifEmpty { "(empty)" }
    Column(modifier = modifier) {
        Text(
            ruleTitle(rule),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${rule.pattern} → $replacementLabel" + if (rule.ttsOnly) " · TTS only" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun ruleTitle(rule: FilterRule): String = rule.title.ifBlank { rule.pattern.ifBlank { "Untitled rule" } }
