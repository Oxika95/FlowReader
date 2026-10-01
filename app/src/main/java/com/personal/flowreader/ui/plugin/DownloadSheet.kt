package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowCollapsibleBox
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowPrimaryButton
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Download all / partial range / cache window settings for the open story. Stacks over the media card. */
@Composable
internal fun DownloadSheet(
    visible: Boolean,
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onPane: (PluginDownloadPane) -> Unit,
    onDownloadAll: () -> Unit,
    onPartialStartDraft: (String) -> Unit,
    onResolvePartialStart: () -> Unit,
    onPartialCount: (String) -> Unit,
    onDownloadPartial: () -> Unit,
    onCacheLevel: (String) -> Unit,
    onBeginPartial: () -> Unit,
) {
    val story = ui.story
    var settingsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(visible, ui.downloadPane) {
        if (!visible || ui.downloadPane != PluginDownloadPane.Menu) {
            if (settingsExpanded) onResolvePartialStart()
            settingsExpanded = false
        }
    }
    val partial = ui.downloadPane == PluginDownloadPane.Partial
    FlowFullscreenCard(
        visible = visible && story != null,
        onDismiss = onDismiss,
        title = if (partial) "Partial download" else "Download",
        onBack = if (partial) ({ onPane(PluginDownloadPane.Menu) }) else null,
        footer = {
            FlowActionRow {
                FlowTextAction(
                    if (partial) "Back" else "Cancel",
                    { if (partial) onPane(PluginDownloadPane.Menu) else onDismiss() },
                    enabled = !ui.busy,
                )
            }
        },
    ) {
        if (story == null) return@FlowFullscreenCard
        val canDownload = !ui.busy && story.chapterCount > 0
        when (ui.downloadPane) {
            PluginDownloadPane.Menu -> {
                FlowHint(
                    "Streaming keeps a small window around your reading position. " +
                        "Pin chapters here for offline reading.",
                )
                FlowPrimaryButton("Download all", onDownloadAll, enabled = canDownload)
                PartialDownloadSplitButton(
                    enabled = canDownload,
                    settingsSelected = settingsExpanded,
                    onPartial = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = false
                        onPane(PluginDownloadPane.Partial)
                    },
                    onSettings = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = !settingsExpanded
                    },
                )
                FlowCollapsibleBox(visible = settingsExpanded) {
                    PartialCacheSettingsPanel(
                        story = story,
                        ui = ui,
                        enabled = !ui.busy,
                        onPartialStartDraft = onPartialStartDraft,
                        onCacheLevel = onCacheLevel,
                        onBeginPartial = onBeginPartial,
                    )
                }
            }
            PluginDownloadPane.Partial -> {
                FlowHint(
                    "Pins and downloads from the configured start chapter through the end, " +
                        "or a chapter count cap if set.",
                )
                val startTitle = story.toc.getOrNull(ui.partialStartIndex)?.title.orEmpty()
                FlowHint(
                    "Starting at chapter ${ui.partialStartIndex + 1}" +
                        if (startTitle.isNotBlank()) " — $startTitle" else "",
                )
                FlowTextField(
                    value = ui.partialCountDraft,
                    onValueChange = onPartialCount,
                    label = "Chapter count (blank = through end)",
                    supportingText = "Leave blank to download from the start chapter to the latest chapter.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                FlowPrimaryButton("Download range", onDownloadPartial, enabled = canDownload)
            }
        }
        ui.downloadProgress?.let { (done, total) -> FlowHint("Working $done / $total") }
        ui.error?.let { FlowHint(it, error = true) }
    }
}

/** Outlined pill: "Partial download" on the left, cache settings toggle on the right. */
@Composable
private fun PartialDownloadSplitButton(
    enabled: Boolean,
    settingsSelected: Boolean,
    onPartial: () -> Unit,
    onSettings: () -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = FlowTokens.Alpha.Disabled)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlowTokens.Comp.ButtonPrimary)
                .border(FlowTokens.Stroke.Hairline, outline, FlowTokens.Shape.Pill),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Partial download",
                style = FlowType.action,
                color = if (enabled) MaterialTheme.colorScheme.primary else disabled,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onPartial)
                    .padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.M),
                maxLines = 1,
            )
            VerticalDivider(modifier = Modifier.height(FlowTokens.Icon.L), color = outline)
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Cache settings",
                tint = when {
                    !enabled -> disabled
                    settingsSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clickable(enabled = enabled, onClick = onSettings)
                    .padding(horizontal = FlowTokens.Space.M, vertical = FlowTokens.Space.S)
                    .size(FlowTokens.Icon.M),
            )
        }
    }
}

@Composable
private fun PartialCacheSettingsPanel(
    story: PluginStory,
    ui: PluginTabUi,
    enabled: Boolean,
    onPartialStartDraft: (String) -> Unit,
    onCacheLevel: (String) -> Unit,
    onBeginPartial: () -> Unit,
) {
    val draftNum = ui.partialStartDraft.toIntOrNull()
    val startTitle = if (draftNum != null && draftNum >= 1) story.toc.getOrNull(draftNum - 1)?.title.orEmpty() else ""
    val startLabel = if (startTitle.isNotBlank()) "Start at chapter: $startTitle" else "Start at chapter:"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(FlowTokens.Stroke.Hairline, MaterialTheme.colorScheme.outlineVariant, FlowTokens.Shape.Field)
            .padding(FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
    ) {
        Text("Cache settings", style = FlowType.subsectionTitle)
        OutlinedFieldWithInfo(
            value = ui.partialStartDraft,
            onValueChange = onPartialStartDraft,
            enabled = enabled && story.chapterCount > 0,
            label = startLabel,
            info = "Chapter where a partial download begins.",
        )
        OutlinedFieldWithInfo(
            value = ui.cacheLevelDraft,
            onValueChange = onCacheLevel,
            enabled = enabled,
            label = "Cache level",
            info = "How many chapters to keep ahead of and behind your reading position. " +
                "Begin partial download also uses this count from the start chapter.",
        )
        FlowPrimaryButton("Begin partial download", onBeginPartial, enabled = enabled && story.chapterCount > 0)
    }
}

/** Number field with an info button that opens a Compact card explaining it. */
@Composable
internal fun OutlinedFieldWithInfo(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    info: String,
    keyboardType: KeyboardType = KeyboardType.Number,
) {
    var showInfo by remember { mutableStateOf(false) }
    FlowTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = {
            IconButton(onClick = { showInfo = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "About ${label.substringBefore(':')}")
            }
        },
    )
    FlowConfirmCard(
        visible = showInfo,
        title = label.substringBefore(':'),
        message = info,
        confirmLabel = "OK",
        onConfirm = { showInfo = false },
        onDismiss = { showInfo = false },
        dismissLabel = "",
    )
}
