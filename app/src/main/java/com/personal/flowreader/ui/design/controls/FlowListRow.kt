package com.personal.flowreader.ui.design.controls

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Info icon that shows or hides explanatory text; filled while [expanded]. */
@Composable
fun FlowInfoButton(expanded: Boolean, onClick: () -> Unit, contentDescription: String) {
    FlowIconButton(
        icon = if (expanded) Icons.Filled.Info else Icons.Outlined.Info,
        contentDescription = contentDescription,
        onClick = onClick,
        iconSize = FlowTokens.Icon.M,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Compact one- or two-line item: [title] with [meta] beside it, optional [subtitle] line, then
 * [actions] on the right. Long [info] text stays behind an info icon until tapped.
 */
@Composable
fun FlowListRow(
    title: String,
    modifier: Modifier = Modifier,
    meta: String = "",
    subtitle: String = "",
    subtitleError: Boolean = false,
    info: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    var infoOpen by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        title,
                        style = FlowType.rowTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            style = FlowType.hint,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = FlowTokens.Space.S),
                        )
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = FlowType.hint,
                        color = if (subtitleError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (info.isNotBlank()) {
                FlowInfoButton(infoOpen, { infoOpen = !infoOpen }, "About $title")
            }
            actions()
        }
        FlowCollapsibleBox(infoOpen && info.isNotBlank()) {
            FlowHint(info, Modifier.padding(bottom = FlowTokens.Space.S))
        }
    }
}

/** Section heading with trailing icon actions on the same line; [info] works as in [FlowListRow]. */
@Composable
fun FlowSectionRow(
    title: String,
    modifier: Modifier = Modifier,
    info: String = "",
    actions: @Composable RowScope.() -> Unit = {},
) {
    var infoOpen by rememberSaveable(title) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = FlowType.sectionTitle, modifier = Modifier.weight(1f))
            if (info.isNotBlank()) {
                FlowInfoButton(infoOpen, { infoOpen = !infoOpen }, "About $title")
            }
            actions()
        }
        FlowCollapsibleBox(infoOpen && info.isNotBlank()) {
            FlowHint(info, Modifier.padding(bottom = FlowTokens.Space.S))
        }
    }
}
