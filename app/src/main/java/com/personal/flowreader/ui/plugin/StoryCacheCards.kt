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
import com.personal.flowreader.ui.design.controls.FlowChoiceChips
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowPrimaryButton
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.surface.FlowProgressBar
import com.personal.flowreader.ui.design.surface.FlowProgressTrack
import com.personal.flowreader.ui.theme.FlowType

/** Download policy mode on [StoryManageCard]; maps to persisted `keepAll`. */
private enum class ManageDownloadMode { All, Partial }

/**
 * Story download settings (Manage on the media card): Download All vs Partial mode,
 * mode-specific sub-settings, clear local downloads, and remove from library.
 * Download itself stays on the media card.
 */
@Composable
internal fun StoryManageCard(
    visible: Boolean,
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onCacheLevel: (String) -> Unit,
    onCleanup: (Boolean) -> Unit,
    onKeepAll: (Boolean) -> Unit,
    onClearDownloads: () -> Unit,
    onRemove: () -> Unit,
) {
    val story = ui.story
    var confirmClear by remember(visible, story?.bookId) { mutableStateOf(false) }
    var confirmRemove by remember(visible, story?.bookId) { mutableStateOf(false) }
    val downloading = story != null && ui.downloadProgress != null && ui.downloadBookId == story.bookId
    FlowFullscreenCard(
        visible = visible && story != null,
        onDismiss = onDismiss,
        title = "Manage",
        variant = FlowCardVariant.Compact,
        footer = { FlowActionRow { FlowTextAction("Close", onDismiss) } },
    ) {
        if (story == null) return@FlowFullscreenCard
        val editable = !downloading && !ui.busy
        val mode = if (story.keepAll) ManageDownloadMode.All else ManageDownloadMode.Partial
        val chapterTitle = story.toc.getOrNull(story.chapterIndex)?.title.orEmpty()
        FlowChoiceChips(
            options = ManageDownloadMode.entries,
            selected = mode,
            optionLabel = {
                when (it) {
                    ManageDownloadMode.All -> "Download All"
                    ManageDownloadMode.Partial -> "Partial Download"
                }
            },
            onSelect = { onKeepAll(it == ManageDownloadMode.All) },
            enabled = editable,
        )
        when (mode) {
            ManageDownloadMode.All -> FlowHint(
                "The Download button on the card fetches every chapter and keeps them on " +
                    "this device. Cleanup never deletes kept-all chapters.",
            )
            ManageDownloadMode.Partial -> {
                FlowHint(
                    "Downloads start from the last saved position" +
                        (if (chapterTitle.isNotBlank()) " ($chapterTitle)" else "") +
                        ". Tap the progress bar on the story card to change it.",
                )
                OutlinedFieldWithInfo(
                    value = ui.cacheLevelDraft,
                    onValueChange = onCacheLevel,
                    enabled = editable,
                    label = "Cache level",
                    info = "How many chapters to download ahead of the last saved position, " +
                        "and how many to keep behind it before clearing.",
                )
                FlowToggleRow(
                    title = "Clean up old chapters",
                    subtitle = "Delete chapters more than ${story.cacheLevel} behind your position.",
                    checked = story.cleanup,
                    onCheckedChange = onCleanup,
                    enabled = editable,
                )
            }
        }
        FlowHint(
            if (story.downloadedCount > 0) {
                "${story.downloadedCount} / ${story.chapterCount} chapters on this device"
            } else {
                "No chapters downloaded yet"
            },
        )
        FlowPrimaryButton(
            "Clear local downloads",
            { confirmClear = true },
            enabled = editable && story.downloadedCount > 0,
        )
        FlowPrimaryButton(
            "Remove from library",
            { confirmRemove = true },
            enabled = editable,
            destructive = true,
        )
        if (downloading) ui.downloadProgress?.let { (done, total) -> FlowHint("Downloading $done / $total") }
        ui.error?.let { FlowHint(it, error = true) }
    }
    FlowConfirmCard(
        visible = confirmClear && story != null,
        title = "Clear local downloads?",
        message = "Deletes downloaded chapter files for this story. The library entry, reading " +
            "position, and download settings stay. You can download again anytime.",
        confirmLabel = "Clear",
        onConfirm = {
            confirmClear = false
            onClearDownloads()
        },
        onDismiss = { confirmClear = false },
    )
    FlowConfirmCard(
        visible = confirmRemove && story != null,
        title = "Remove from library?",
        message = "Removes “${story?.title.orEmpty()}” from this device. Site lists are unchanged.",
        confirmLabel = "Remove",
        onConfirm = {
            confirmRemove = false
            onRemove()
        },
        onDismiss = { confirmRemove = false },
        destructive = true,
    )
}

/**
 * Reading position with a % progress tracker and −/+ chapter steps. Save replaces the saved
 * position with the start of the chosen chapter, after a confirm when a position is already saved.
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
            Text(
                "${draft + 1} / $count",
                style = FlowType.label,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowProgressBar(
                progress = draft.toFloat() / count,
                track = FlowProgressTrack.OnPage,
                roundBottom = false,
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
