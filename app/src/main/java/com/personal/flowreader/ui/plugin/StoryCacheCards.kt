package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowPrimaryButton
import com.personal.flowreader.ui.design.controls.FlowSliderRow
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.theme.FlowType
import kotlin.math.roundToInt

/** "Download all N chapters?" (tap Download on the story media card). */
@Composable
internal fun DownloadAllCard(
    visible: Boolean,
    story: PluginStory?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowConfirmCard(
        visible = visible && story != null,
        title = "Download all ${story?.chapterCount ?: 0} chapters?",
        message = "Every chapter is saved for offline reading and kept even when cleanup is on. " +
            "Large stories take a while; tap Cancel download on the card to stop.",
        confirmLabel = "Download",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * Partial download (long-press Download): downloads the saved position and the cache level of
 * chapters after it. Cache level and cleanup save as they change.
 */
@Composable
internal fun PartialDownloadCard(
    visible: Boolean,
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onCacheLevel: (String) -> Unit,
    onCleanup: (Boolean) -> Unit,
    onDownload: () -> Unit,
) {
    val story = ui.story
    FlowFullscreenCard(
        visible = visible && story != null,
        onDismiss = onDismiss,
        title = "Partial download",
        variant = FlowCardVariant.Compact,
        footer = { FlowActionRow { FlowTextAction("Close", onDismiss) } },
    ) {
        if (story == null) return@FlowFullscreenCard
        val downloading = ui.downloadProgress != null && ui.downloadBookId == story.bookId
        val title = story.toc.getOrNull(story.chapterIndex)?.title.orEmpty()
        FlowHint(
            "From chapter ${story.chapterIndex + 1}" + (if (title.isNotBlank()) " — $title" else "") +
                " (saved position). Hold the cache bar on the card to change it.",
        )
        OutlinedFieldWithInfo(
            value = ui.cacheLevelDraft,
            onValueChange = onCacheLevel,
            enabled = !downloading,
            label = "Cache level",
            info = "How many chapters after your reading position to keep downloaded. Reading " +
                "and listening download ahead by this many as you go.",
        )
        FlowToggleRow(
            title = "Clean up old chapters",
            subtitle = "Delete chapters more than ${story.cacheLevel} behind your position. " +
                "Download all chapters are kept.",
            checked = story.cleanup,
            onCheckedChange = onCleanup,
            enabled = !downloading,
        )
        FlowPrimaryButton(
            "Download next ${story.cacheLevel}",
            onDownload,
            enabled = !downloading && !ui.busy && story.chapterCount > 0,
        )
        if (downloading) ui.downloadProgress?.let { (done, total) -> FlowHint("Downloading $done / $total") }
        ui.error?.let { FlowHint(it, error = true) }
    }
}

/**
 * Reading position slider (long-press the cache bar). Save replaces the saved position with the
 * start of the chosen chapter, after a confirm when a position is already saved.
 */
@Composable
internal fun PositionSliderCard(
    visible: Boolean,
    story: PluginStory?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (chapterIndex: Int) -> Unit,
) {
    val count = story?.chapterCount ?: 0
    var draft by remember(visible, story?.bookId, story?.chapterIndex) {
        mutableIntStateOf(story?.chapterIndex ?: 0)
    }
    var confirm by remember(visible) { mutableStateOf(false) }
    FlowFullscreenCard(
        visible = visible && story != null,
        onDismiss = onDismiss,
        title = "Reading position",
        variant = FlowCardVariant.Compact,
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction(
                    "Save",
                    { if (story?.hasSavedPosition == true) confirm = true else onSave(draft) },
                    enabled = !busy && count > 0,
                )
            }
        },
    ) {
        if (story == null) return@FlowFullscreenCard
        if (count > 1) {
            FlowSliderRow(
                label = "Chapter",
                value = draft.toFloat(),
                onValueChange = { draft = it.roundToInt().coerceIn(0, count - 1) },
                valueRange = 0f..(count - 1).toFloat(),
                valueLabel = "${draft + 1} / $count",
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FlowIconButton(
                    icon = Icons.Filled.Remove,
                    contentDescription = "Previous chapter",
                    onClick = { draft = (draft - 1).coerceAtLeast(0) },
                    enabled = draft > 0,
                )
                Text(
                    story.toc.getOrNull(draft)?.title.orEmpty().ifBlank { "Chapter ${draft + 1}" },
                    style = FlowType.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FlowIconButton(
                    icon = Icons.Filled.Add,
                    contentDescription = "Next chapter",
                    onClick = { draft = (draft + 1).coerceAtMost(count - 1) },
                    enabled = draft < count - 1,
                )
            }
        } else {
            FlowHint("This story has one chapter.")
        }
        FlowHint("Saved position: chapter ${story.chapterIndex + 1}. Saving downloads ahead from the new position.")
    }
    FlowConfirmCard(
        visible = visible && confirm && story != null,
        title = "Replace saved position?",
        message = "Your saved position (chapter ${(story?.chapterIndex ?: 0) + 1}) will be replaced " +
            "with the start of chapter ${draft + 1}.",
        confirmLabel = "Replace",
        onConfirm = {
            confirm = false
            onSave(draft)
        },
        onDismiss = { confirm = false },
    )
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
