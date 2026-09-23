package com.personal.flowreader.library.plugin.royalroad


import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.produceState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.LibrarySourcePlugin
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.ui.common.BookHeroPrimaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButtonRow
import com.personal.flowreader.ui.common.BookHeroSplashButtons
import com.personal.flowreader.ui.common.BookHeroSplashShell
import com.personal.flowreader.ui.common.FlowSlotTab
import com.personal.flowreader.ui.common.FlowSlotTabBar
import com.personal.flowreader.ui.common.FlowSlotTabLabel
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.library.LibraryBooksPane
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.chrome.ReaderPanelFeather
import com.personal.flowreader.ui.theme.FlowTokens


@Composable
internal fun DownloadOptionsOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onPane: (RoyalRoadDownloadPane) -> Unit,
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
        if (!visible || ui.downloadPane != RoyalRoadDownloadPane.Menu) {
            if (settingsExpanded) {
                onResolvePartialStart()
            }
            settingsExpanded = false
        }
    }
    ReaderModalScaffold(
        visible = visible && story != null,
        contentPadding = PaddingValues(
            horizontal = FlowTokens.Space.M,
            vertical = FlowTokens.Space.S,
        ),
        onDismiss = onDismiss,
        feather = ReaderPanelFeather,
        scrimAlpha = FlowTokens.ScrimHero,
    ) {
        if (story == null) return@ReaderModalScaffold
        when (ui.downloadPane) {
            RoyalRoadDownloadPane.Menu -> {
                Text(
                    "Download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = FlowTokens.Space.S,
                        vertical = FlowTokens.Space.S,
                    ),
                )
                Text(
                    "Streaming keeps a small window around your reading position. " +
                        "Pin chapters here for offline reading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = FlowTokens.Space.S,
                        vertical = FlowTokens.Space.XS,
                    ),
                )
                Button(
                    onClick = onDownloadAll,
                    enabled = !ui.busy && story.chapterCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.XS),
                ) {
                    Text("Download all")
                }
                PartialDownloadSplitButton(
                    enabled = !ui.busy && story.chapterCount > 0,
                    settingsSelected = settingsExpanded,
                    onPartial = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = false
                        onPane(RoyalRoadDownloadPane.Partial)
                    },
                    onSettings = {
                        if (settingsExpanded) onResolvePartialStart()
                        settingsExpanded = !settingsExpanded
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.XS),
                )
                AnimatedVisibility(
                    visible = settingsExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    PartialCacheSettingsPanel(
                        story = story,
                        ui = ui,
                        enabled = !ui.busy,
                        onPartialStartDraft = onPartialStartDraft,
                        onCacheLevel = onCacheLevel,
                        onBeginPartial = onBeginPartial,
                    )
                }
                TextButton(onClick = onDismiss, enabled = !ui.busy) {
                    Text("Cancel")
                }
            }
            RoyalRoadDownloadPane.Partial -> {
                Text(
                    "Partial download",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = FlowTokens.Space.S,
                        vertical = FlowTokens.Space.S,
                    ),
                )
                Text(
                    "Pins and downloads from the configured start chapter through the end, " +
                        "or a chapter count cap if set.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = FlowTokens.Space.S,
                        vertical = FlowTokens.Space.XS,
                    ),
                )
                val startTitle = story.toc.getOrNull(ui.partialStartIndex)?.title.orEmpty()
                Text(
                    "Starting at chapter ${ui.partialStartIndex + 1}" +
                        if (startTitle.isNotBlank()) " — $startTitle" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        horizontal = FlowTokens.Space.S,
                        vertical = FlowTokens.Space.XS,
                    ),
                )
                OutlinedTextField(
                    value = ui.partialCountDraft,
                    onValueChange = onPartialCount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.XS),
                    label = { Text("Chapter count (blank = through end)") },
                    supportingText = {
                        Text("Leave blank to download from the start chapter to the latest chapter.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Button(
                    onClick = onDownloadPartial,
                    enabled = !ui.busy && story.chapterCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.XS),
                ) {
                    Text("Download range")
                }
                TextButton(
                    onClick = { onPane(RoyalRoadDownloadPane.Menu) },
                    enabled = !ui.busy,
                ) {
                    Text("Back")
                }
            }
        }
        ui.downloadProgress?.let { (done, total) ->
            Text(
                "Working $done / $total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = FlowTokens.Space.S,
                    vertical = FlowTokens.Space.XS,
                ),
            )
        }
        ui.error?.takeIf { ui.showDownload }?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(
                    horizontal = FlowTokens.Space.S,
                    vertical = FlowTokens.Space.XS,
                ),
            )
        }
    }
}

@Composable
internal fun PartialDownloadSplitButton(
    enabled: Boolean,
    settingsSelected: Boolean,
    onPartial: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(50)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = modifier
                .height(FlowTokens.Comp.ButtonPrimary)
                .border(FlowTokens.Stroke.Hairline, outline, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Partial download",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onPartial)
                    .padding(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.M),
                maxLines = 1,
            )
            VerticalDivider(
                modifier = Modifier.height(FlowTokens.Icon.L),
                color = outline,
            )
            Icon(
                Icons.Filled.Settings,
                contentDescription = "Cache settings",
                tint = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
internal fun PartialCacheSettingsPanel(
    story: RoyalRoadStorySplash,
    ui: RoyalRoadUi,
    enabled: Boolean,
    onPartialStartDraft: (String) -> Unit,
    onCacheLevel: (String) -> Unit,
    onBeginPartial: () -> Unit,
) {
    val draftNum = ui.partialStartDraft.toIntOrNull()
    val startTitle = if (draftNum != null && draftNum >= 1) {
        story.toc.getOrNull(draftNum - 1)?.title.orEmpty()
    } else {
        ""
    }
    val startLabel = if (startTitle.isNotBlank()) {
        "Start at chapter: $startTitle"
    } else {
        "Start at chapter:"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.XS)
            .border(
                FlowTokens.Stroke.Hairline,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(FlowTokens.Radius.M),
            )
            .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
    ) {
        Text(
            "Cache settings",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
            info = "How many chapters to keep ahead of and behind your reading position. Begin partial download also uses this count from the start chapter.",
        )
        Button(
            onClick = onBeginPartial,
            enabled = enabled && story.chapterCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Begin partial download")
        }
    }
}

@Composable
internal fun OutlinedFieldWithInfo(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    info: String,
) {
    var showInfo by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            label = {
                Text(
                    label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Surface(
            onClick = { showInfo = true },
            enabled = true,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(FlowTokens.Stroke.Hairline, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // One-off badge nudge; keep raw offset rather than forcing CoverBlur.
                .offset(x = FlowTokens.Space.Hair, y = (-6).dp)
                .size(FlowTokens.Icon.L),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = "About $label",
                    modifier = Modifier.size(FlowTokens.Icon.S),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            text = {
                Text(info, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            },
        )
    }
}

/** Continuous chapter bar: gray behind, desaturated accent ahead, saturated accent at locus. */
