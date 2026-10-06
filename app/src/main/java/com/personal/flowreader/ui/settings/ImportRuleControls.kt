package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowLabel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import com.personal.flowreader.ui.theme.FlowTokens

@Composable
internal fun RuleListHeader(
    title: String,
    emptyHint: String,
    rulesEmpty: Boolean,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onAdd) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                modifier = Modifier.size(FlowTokens.Icon.M),
            )
            Spacer(Modifier.width(FlowTokens.Space.XS))
            Text("Add")
        }
    }
    if (rulesEmpty) {
        Text(
            emptyHint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    content()
}


@Composable
internal fun <T> DragHandle(
    ruleId: String,
    working: MutableList<T>,
    rowHeights: Map<String, Int>,
    idOf: (T) -> String,
    draggingId: (String?) -> Unit,
    dragOffset: Float,
    onDragOffset: (Float) -> Unit,
) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = FlowTokens.Space.S)
            .pointerInput(ruleId) {
                detectDragGestures(
                    onDragStart = {
                        draggingId(ruleId)
                        onDragOffset(0f)
                    },
                    onDragEnd = {
                        draggingId(null)
                        onDragOffset(0f)
                    },
                    onDragCancel = {
                        draggingId(null)
                        onDragOffset(0f)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        var offset = dragOffset + amount.y
                        onDragOffset(offset)
                        val index = working.indexOfFirst { idOf(it) == ruleId }
                        if (index < 0) return@detectDragGestures
                        val next = working.getOrNull(index + 1)
                        val prev = working.getOrNull(index - 1)
                        val nextH = next?.let { rowHeights[idOf(it)] } ?: 0
                        val prevH = prev?.let { rowHeights[idOf(it)] } ?: 0
                        if (next != null && offset > nextH / 2f) {
                            working.add(index + 1, working.removeAt(index))
                            offset -= nextH
                            onDragOffset(offset)
                        } else if (prev != null && offset < -prevH / 2f) {
                            working.add(index - 1, working.removeAt(index))
                            offset += prevH
                            onDragOffset(offset)
                        }
                    },
                )
            },
    )
}


@Composable
internal fun MatchFields(
    matchText: String,
    onMatchText: (String) -> Unit,
    matchIsRegex: Boolean,
    onMatchIsRegex: (Boolean) -> Unit,
    allowWildcard: Boolean,
    onAllowWildcard: (Boolean) -> Unit,
) {
    FlowLabel("URL match")
    OutlinedTextField(
        value = matchText,
        onValueChange = onMatchText,
        singleLine = true,
        placeholder = {
            Text(
                if (matchIsRegex) {
                    "royalroad\\.com/fiction/\\d+/[^/]+/chapter.*"
                } else {
                    "*.example.com/fiction/1/story"
                },
            )
        },
        shape = FlowTokens.Shape.Field,
        modifier = Modifier.fillMaxWidth(),
    )
    if (!matchIsRegex) {
        FlowToggleRow(
            title = "Allow Wildcards",
            subtitle = "Use * to represent one or more unknown characters.",
            checked = allowWildcard,
            onCheckedChange = onAllowWildcard,
        )
    }
    FlowToggleRow(
        title = "Enable RegEx",
        subtitle = "Use Regular Expression to match against the URL.",
        checked = matchIsRegex,
        onCheckedChange = onMatchIsRegex,
    )
}
