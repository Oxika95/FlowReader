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
internal fun StorySplashOverlay(
    visible: Boolean,
    ui: RoyalRoadUi,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onRefreshToc: () -> Unit,
    onDelete: () -> Unit,
    onBookmark: (RoyalRoadListKind) -> Unit,
) {
    val story = ui.story
    val context = LocalContext.current
    val book = story?.let { s -> ui.books.find { it.bookId == s.bookId } }
    val cover by rememberBookCover(book, maxEdge = 768)
    BookHeroSplashShell(
        visible = visible && story != null,
        onDismiss = onDismiss,
        art = cover,
        coverBandPadding = PaddingValues(
            start = FlowTokens.Space.L,
            end = FlowTokens.Space.S,
            top = FlowTokens.Space.XS,
        ),
        overlayExtras = overlayExtras@{
            val s = story ?: return@overlayExtras
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = FlowTokens.Space.M, end = FlowTokens.Space.M),
                verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RoyalRoadListKind.entries.forEach { kind ->
                    SplashCircleIconButton(
                        icon = kind.splashIcon,
                        contentDescription = kind.label,
                        selected = kind in s.listedIn,
                        enabled = !ui.busy,
                        onClick = { onBookmark(kind) },
                    )
                }
                SplashCircleIconButton(
                    icon = Icons.Filled.Share,
                    contentDescription = "Share",
                    selected = false,
                    enabled = s.fictionUrl.isNotBlank(),
                    onClick = {
                        val send = android.content.Intent(
                            android.content.Intent.ACTION_SEND,
                        ).apply {
                            type = "text/plain"
                            putExtra(
                                android.content.Intent.EXTRA_SUBJECT,
                                s.title,
                            )
                            putExtra(
                                android.content.Intent.EXTRA_TEXT,
                                s.fictionUrl,
                            )
                        }
                        context.startActivity(
                            android.content.Intent.createChooser(
                                send,
                                "Share story",
                            ),
                        )
                    },
                )
            }
        },
        coverBand = coverBand@{ maxHeight ->
            if (story == null) return@coverBand
            val onCoverMuted = FlowTokens.CoverMutedWhite
            val synopsisMaxHeight = (maxHeight * 0.42f).coerceAtLeast(FlowTokens.Comp.ButtonSecondary)
            Column(Modifier.fillMaxWidth()) {
                Text(
                    story.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        lineHeight = MaterialTheme.typography.titleLarge.fontSize *
                            FlowTokens.SplashTitleLineHeight,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (story.author.isNotBlank()) {
                    Text(
                        story.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = FlowTokens.Space.Hair),
                    )
                }
            }
            val stats = listOfNotNull(
                story.status.takeIf { it.isNotBlank() },
                story.ratingLabel.takeIf { it.isNotBlank() }?.let { "★ $it" },
                story.views?.let { "${formatCount(it)} views" },
                "${story.chapterCount} chapters",
            ).joinToString(" · ")
            if (stats.isNotEmpty()) {
                Text(
                    stats,
                    style = MaterialTheme.typography.bodySmall,
                    color = onCoverMuted,
                    modifier = Modifier.padding(top = FlowTokens.Space.XS, end = FlowTokens.Space.S),
                )
            }
            if (story.tags.isNotEmpty()) {
                Text(
                    story.tags.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = FlowTokens.Space.XS, end = FlowTokens.Space.S),
                )
            }
            if (story.synopsis.isNotBlank()) {
                Text(
                    story.synopsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = onCoverMuted,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 20,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = synopsisMaxHeight)
                        .padding(
                            top = FlowTokens.Space.XS,
                            end = FlowTokens.Space.S,
                            bottom = FlowTokens.Space.XS,
                        ),
                )
            }
            ChapterCacheStrip(
                chapterCount = story.chapterCount,
                chapterIndex = ui.partialStartIndex,
                cachedIndices = story.cachedIndices,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FlowTokens.Space.S)
                    .height(FlowTokens.Space.M),
            )
        },
    ) {
        if (story == null) return@BookHeroSplashShell
        val dl = ui.downloadProgress
        Text(
            if (dl != null) {
                "Downloading ${dl.first} / ${dl.second}"
            } else {
                "Cached ${story.downloadedCount} / ${story.chapterCount} chapters" +
                    " · cache level ${maxOf(story.keepBehind, story.prefetchAhead)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = FlowTokens.Space.S),
        )
        BookHeroSplashButtons {
            BookHeroSecondaryButtonRow {
                BookHeroSecondaryButton(
                    onClick = onDownload,
                    enabled = !ui.busy && story.chapterCount > 0,
                    label = "Download",
                )
                BookHeroSecondaryButton(
                    onClick = onRefreshToc,
                    enabled = !ui.busy,
                    label = "Refresh",
                )
                BookHeroSecondaryButton(
                    onClick = onDelete,
                    enabled = !ui.busy,
                    label = "Delete",
                )
            }
            BookHeroPrimaryButton(
                onClick = onRead,
                enabled = !ui.busy && story.chapterCount > 0,
                label = "Read",
            )
        }
        ui.error?.let { err ->
            Text(
                err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = FlowTokens.Space.S),
            )
        }
    }
}

@Composable
internal fun SplashCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = if (selected) accent else Color.Black.copy(alpha = 0.72f)
    val tint = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White
    val ring = if (selected) accent else Color.White.copy(alpha = 0.55f)
    Box(
        modifier = Modifier
            .size(FlowTokens.Icon.Hero)
            .border(FlowTokens.Stroke.Hairline, ring, CircleShape)
            .background(bg, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.size(FlowTokens.Icon.M),
        )
    }
}

private val RoyalRoadListKind.splashIcon: ImageVector
    get() = when (this) {
        RoyalRoadListKind.Follow -> Icons.Filled.Add
        RoyalRoadListKind.Favorite -> Icons.Filled.Favorite
        RoyalRoadListKind.ReadLater -> Icons.Filled.Schedule
    }


@Composable
internal fun ChapterCacheStrip(
    chapterCount: Int,
    chapterIndex: Int,
    cachedIndices: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val ahead = accent.copy(alpha = 0.40f)
    val behind = CacheBehindGray
    val track = Color.White.copy(alpha = 0.14f)
    Box(
        modifier = modifier.drawBehind {
            if (chapterCount <= 0) return@drawBehind
            val barH = size.height * 0.55f
            val barTop = (size.height - barH) / 2f
            val locus = chapterIndex.coerceIn(0, chapterCount - 1)
            val slot = size.width / chapterCount

            fun colorFor(i: Int): Color {
                if (i !in cachedIndices) return track
                return if (i < locus) behind else ahead
            }

            // Merge adjacent same-color runs into continuous segments (no gaps/dots).
            var runStart = 0
            var runColor = colorFor(0)
            for (i in 1..chapterCount) {
                val next = if (i < chapterCount) colorFor(i) else null
                if (next != runColor) {
                    drawRect(
                        color = runColor,
                        topLeft = Offset(runStart * slot, barTop),
                        size = Size((i - runStart) * slot, barH),
                    )
                    if (next != null) {
                        runStart = i
                        runColor = next
                    }
                }
            }

            val markW = max(slot, FlowTokens.Comp.ProgressBar.toPx())
            val markH = size.height
            val markX = (locus * slot + (slot - markW) / 2f).coerceIn(0f, size.width - markW)
            drawRect(
                color = accent,
                topLeft = Offset(markX, 0f),
                size = Size(markW, markH),
            )
        },
    )
}

private val CacheBehindGray = FlowTokens.NeutralCacheGray

internal fun formatCount(value: Long): String = when {
    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
    else -> value.toString()
}
