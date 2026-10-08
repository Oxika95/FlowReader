package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowLabel

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.personal.flowreader.ui.theme.FlowTokens

@Composable
internal fun <T> DragHandle(
    ruleId: String,
    working: MutableList<T>,
    rowHeights: Map<String, Int>,
    idOf: (T) -> String,
    draggingId: (String?) -> Unit,
    onDragOffset: (Float) -> Unit,
) {
    Icon(
        Icons.Filled.DragHandle,
        contentDescription = "Drag to reorder",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(end = FlowTokens.Space.S)
            .pointerInput(ruleId) {
                // Accumulated here: the pointerInput block outlives recompositions, so a
                // composition parameter read inside onDrag would stay at its first value.
                var offset = 0f
                detectDragGestures(
                    onDragStart = {
                        offset = 0f
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
                        offset += amount.y
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
    matches: Boolean? = null,
) {
    FlowLabel("URL match")
    val border = when (matches) {
        true -> FlowTokens.MatchGreen
        false -> MaterialTheme.colorScheme.error
        null -> null
    }
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
        colors = if (border == null) {
            OutlinedTextFieldDefaults.colors()
        } else {
            OutlinedTextFieldDefaults.colors(focusedBorderColor = border, unfocusedBorderColor = border)
        },
        modifier = Modifier.fillMaxWidth(),
    )
    if (matches == false) {
        Text(
            "Doesn't match the Test URL, so shared links like it won't use this rule.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
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
