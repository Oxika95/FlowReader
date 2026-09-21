package com.personal.flowreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.os.Build
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterMatchType
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.ReaderFont
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.data.UiScale
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.roundToInt

internal val ReaderPanelShape = FlowTokens.PanelShape
internal val ReaderPanelFeather = FlowTokens.Icon.M
/** Left inset of reading text (locus rail gutter); bars are centered in this width. */
internal val ReaderContentStartPadding = FlowTokens.ScreenGutter
/** End padding on the reading LazyColumn (right gutter). */
internal val ReaderListEndPadding = FlowTokens.ScreenGutter
/** Extra end padding on the paragraph text itself. */
internal val ReaderTextEndPadding = FlowTokens.Radius.None
/** Right inset of reading text — panels must land on the same edge as the text column. */
internal val ReaderContentEndPadding = ReaderListEndPadding + ReaderTextEndPadding
/** Top/bottom inset for Settings/TOC cards — same scale as the reading-column side gutters. */
internal val ReaderOverlayVerticalPad = ReaderContentStartPadding

/** Soft page-colored halo outside the border so panels separate from reading text. */
private fun DrawScope.drawReaderPanelFeather(
    color: Color,
    corner: Dp,
    feather: Dp,
) {
    val featherPx = feather.toPx()
    if (featherPx <= 0f) return
    val baseCorner = corner.toPx()
    val steps = 14
    for (i in steps downTo 1) {
        val frac = i / steps.toFloat()
        val expand = featherPx * frac
        val alpha = (1f - frac) * (1f - frac) * 0.78f
        if (alpha < 0.01f) continue
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-expand, -expand),
            size = Size(size.width + expand * 2f, size.height + expand * 2f),
            cornerRadius = CornerRadius(baseCorner + expand * 0.4f),
        )
    }
}

/**
 * Reader chrome surface: theme background, outline border, zero elevation,
 * feathered fade outside the box so cards read apart from page text.
 *
 * When [matchReaderWidth] is true, the solid border aligns with the reading
 * column; the feather extends into the side margins beyond that border.
 */
@Composable
internal fun ReaderPanelSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ReaderPanelShape,
    feather: Dp = ReaderPanelFeather,
    /** Align solid card width with the ereader text column. */
    matchReaderWidth: Boolean = false,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val outline = MaterialTheme.colorScheme.outlineVariant
    val onBg = MaterialTheme.colorScheme.onBackground
    val cardInset = if (matchReaderWidth) {
        Modifier.padding(
            start = ReaderContentStartPadding,
            end = ReaderContentEndPadding,
            top = feather,
            bottom = feather,
        )
    } else {
        Modifier.padding(feather)
    }
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .then(cardInset)
                .then(if (matchReaderWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .then(
                    if (feather > FlowTokens.Radius.None) {
                        Modifier.drawBehind {
                            drawReaderPanelFeather(bg, FlowTokens.PanelRadius, feather)
                        }
                    } else {
                        Modifier
                    },
                ),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = bg,
                contentColor = onBg,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = FlowTokens.Radius.None),
            border = BorderStroke(FlowTokens.Stroke.Hairline, outline),
        ) {
            content()
        }
    }
}

@Composable
internal fun FloatingPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = FlowTokens.Space.M,
        vertical = FlowTokens.Space.M,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    ReaderPanelSurface(modifier = modifier, matchReaderWidth = true) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

@Composable
internal fun TitleBannerCard(
    visible: Boolean,
    title: String,
    chapter: String,
    progress: Float,
    storedPath: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onToc: () -> Unit,
) {
    val cover by rememberReaderCover(storedPath, maxEdge = 512)
    val cardBg = MaterialTheme.colorScheme.background
    val canBlur = Build.VERSION.SDK_INT >= 31
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        ReaderPanelSurface(modifier = Modifier.fillMaxWidth(), matchReaderWidth = true) {
            Box(modifier = Modifier.fillMaxWidth()) {
                BannerCoverUnderlay(
                    cover = cover,
                    blur = canBlur,
                    cardBg = cardBg,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(bottom = FlowTokens.Comp.ProgressBar)
                        // Keep cover clear of back / TOC hit targets.
                        .padding(horizontal = BannerCoverSideInset),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.S)
                        .padding(bottom = FlowTokens.Comp.ProgressBar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(FlowTokens.Icon.Hero),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(FlowTokens.Icon.M),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = FlowTokens.Space.XS),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val textShadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f,
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            chapter,
                            style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Match back control width so title stays centered on the card.
                    IconButton(
                        onClick = onToc,
                        modifier = Modifier.size(FlowTokens.Icon.Hero),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FlowTokens.Comp.ProgressBar)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = FlowTokens.Radius.L,
                                bottomEnd = FlowTokens.Radius.L,
                            ),
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                )
            }
        }
    }
}

/** Horizontal inset so the cover band ends before the side icon buttons (matches primary control). */
private val BannerCoverSideInset = FlowTokens.Comp.ButtonPrimary

@Composable
private fun rememberReaderCover(storedPath: String, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = null, storedPath, maxEdge) {
        value = if (storedPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                // Prefer sidecar cover (plugin books), then EPUB embedded cover.
                val file = File(storedPath)
                val sidecar = file.parentFile?.let { dir ->
                    listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
                        .map { File(dir, it) }
                        .firstOrNull { it.exists() && it.length() > 0L }
                }
                when {
                    sidecar != null -> {
                        android.graphics.BitmapFactory.decodeFile(sidecar.absolutePath)
                            ?.let { bmp ->
                                val scaled = if (maxOf(bmp.width, bmp.height) > maxEdge) {
                                    val scale = maxEdge.toFloat() / maxOf(bmp.width, bmp.height)
                                    android.graphics.Bitmap.createScaledBitmap(
                                        bmp,
                                        (bmp.width * scale).toInt().coerceAtLeast(1),
                                        (bmp.height * scale).toInt().coerceAtLeast(1),
                                        true,
                                    ).also { if (it !== bmp) bmp.recycle() }
                                } else {
                                    bmp
                                }
                                scaled.asImageBitmap()
                            }
                    }
                    else -> EpubCover.loadBitmap(file, maxEdge)?.asImageBitmap()
                }
            }
        }
    }

@Composable
private fun BannerCoverUnderlay(
    cover: ImageBitmap?,
    blur: Boolean,
    cardBg: Color,
    modifier: Modifier = Modifier,
) {
    if (cover == null) return
    Box(modifier) {
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
        )
        // Soft side fades into the card; light center wash for title contrast.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to cardBg,
                            0.18f to cardBg.copy(alpha = 0.35f),
                            0.50f to cardBg.copy(alpha = 0.45f),
                            0.82f to cardBg.copy(alpha = 0.35f),
                            1f to cardBg,
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun MediaControlCard(
    visible: Boolean,
    playing: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
) {
    val playSize = FlowTokens.Comp.Fab
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        // Outer box sizes to the tall play control; the card itself stays short.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FloatingPanel(
                contentPadding = PaddingValues(
                    horizontal = FlowTokens.Space.S,
                    vertical = FlowTokens.Radius.None,
                ),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence")
                        }
                        // Horizontal slot for the overlapping play control; does not set card height.
                        Spacer(Modifier.width(playSize))
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next sentence")
                        }
                    }
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(
                            horizontal = FlowTokens.Space.XS,
                            vertical = FlowTokens.Space.XS,
                        ),
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    if (playing) onPause() else onPlay()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(playSize),
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(FlowTokens.Icon.XL),
                )
            }
        }
    }
}

/**
 * Scrim + centered scrolling card shared by the Settings and TOC modals: tap the scrim to
 * dismiss, taps inside the card are swallowed, and the card never outgrows the viewport.
 *
 * When [fillMaxCardHeight] is true the panel stretches to the available height (full-screen
 * splash). When [centerContent] is also true, short content is centered vertically in that panel.
 * Set [contentScrollable] to false when the caller manages its own scroll (e.g. pinned header).
 */
@Composable
internal fun ReaderModalScaffold(
    visible: Boolean,
    contentPadding: PaddingValues,
    onDismiss: () -> Unit,
    fillMaxCardHeight: Boolean = false,
    centerContent: Boolean = false,
    contentScrollable: Boolean = true,
    /** Soft halo drawn outside the card border; Radius.None keeps the hard-edged card. */
    feather: Dp = FlowTokens.Radius.None,
    scrimAlpha: Float = FlowTokens.ScrimStandard,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                // Status/navigation bars are hidden in the reader, so this is a no-op there and
                // keeps the card clear of the bars anywhere they are showing (e.g. the library).
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
            contentAlignment = Alignment.Center,
        ) {
            // Leave room for the halo so a feathered card still clears the screen edges.
            val maxCardHeight = maxHeight - (ReaderOverlayVerticalPad + feather) * 2
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
            ) {
                ReaderPanelSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxCardHeight)
                        .then(
                            if (fillMaxCardHeight) Modifier.fillMaxHeight()
                            else Modifier.wrapContentHeight(),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    matchReaderWidth = true,
                    feather = feather,
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (fillMaxCardHeight) Modifier.height(maxCardHeight)
                                else Modifier.heightIn(max = maxCardHeight),
                            )
                            .then(
                                if (contentScrollable) {
                                    Modifier.verticalScroll(
                                        scrollState,
                                        // Keep layout scrollable for measurement, but only accept
                                        // drag/fling when content actually overflows the card.
                                        enabled = scrollState.maxValue > 0,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(contentPadding),
                        verticalArrangement = if (centerContent) {
                            Arrangement.Center
                        } else {
                            Arrangement.Top
                        },
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
internal fun TocOverlay(
    visible: Boolean,
    chapters: List<String>,
    chapterIndex: Int,
    onChapter: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val safeIndex = if (chapters.isEmpty()) {
        0
    } else {
        chapterIndex.coerceIn(0, chapters.lastIndex)
    }

    LaunchedEffect(visible, safeIndex, chapters.size) {
        if (!visible || chapters.isEmpty()) return@LaunchedEffect
        // Wait until the list has a real viewport so centering math is valid.
        snapshotFlow { listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset }
            .first { it > 0 }
        listState.scrollToItem(safeIndex)
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == safeIndex }
            ?: return@LaunchedEffect
        val viewport = listState.layoutInfo
        val viewportCenter =
            (viewport.viewportStartOffset + viewport.viewportEndOffset) / 2
        val itemCenter = item.offset + item.size / 2
        listState.scrollBy((itemCenter - viewportCenter).toFloat())
    }

    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(
            horizontal = FlowTokens.ModalOuterPadding,
            vertical = FlowTokens.ModalOuterPadding,
        ),
        onDismiss = onDismiss,
        fillMaxCardHeight = true,
        contentScrollable = false,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Contents",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = FlowTokens.ModalTitleStart),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close contents")
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            itemsIndexed(chapters, key = { index, _ -> index }) { index, name ->
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (index == safeIndex) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    color = if (index == safeIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapter(index) }
                        .padding(
                            horizontal = FlowTokens.Space.L,
                            vertical = FlowTokens.Space.L,
                        ),
                )
                if (index < chapters.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = FlowTokens.Space.M),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsOverlay(
    visible: Boolean,
    themeMode: ThemeMode,
    accentHue: Float,
    uiScale: Float,
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    orientation: ReaderOrientation,
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float,
    pitch: Float,
    prefetchCount: Int,
    doubleTapPlay: Boolean,
    autoScrollWithTts: Boolean,
    keepAliveUnderlay: Boolean,
    continuousPcmPlayback: Boolean,
    sentenceGapMs: Int,
    filtersGlobal: List<FilterRule>,
    filtersGroups: List<FilterRule>,
    filtersLocal: List<FilterRule>,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onUiScale: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPrefetchCount: (Int) -> Unit,
    onDoubleTapPlay: (Boolean) -> Unit,
    onAutoScrollWithTts: (Boolean) -> Unit,
    onKeepAliveUnderlay: (Boolean) -> Unit,
    onContinuousPcmPlayback: (Boolean) -> Unit,
    onSentenceGapMs: (Int) -> Unit,
    onAddFilter: (FilterScope) -> Unit,
    onEditFilter: (FilterScope, FilterRule) -> Unit,
    onSetFilterEnabled: (FilterScope, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = FlowTokens.ModalHeaderStart,
                    end = FlowTokens.ModalHeaderEnd,
                    top = FlowTokens.ModalHeaderTop,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = FlowTokens.ModalTitleStart),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close settings")
            }
        }

        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("Layout") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("Audio") },
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { Text("Filters") },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FlowTokens.Pad.CardIn,
                    vertical = FlowTokens.Pad.CardIn,
                ),
        ) {
            when (tab) {
                0 -> {
                    LayoutSettingsTab(
                        themeMode = themeMode,
                        accentHue = accentHue,
                        uiScale = uiScale,
                        fontScale = fontScale,
                        fontFamily = fontFamily,
                        lineSpacing = lineSpacing,
                        orientation = orientation,
                        onTheme = onTheme,
                        onAccentHue = onAccentHue,
                        onUiScale = onUiScale,
                        onFontScale = onFontScale,
                        onFontFamily = onFontFamily,
                        onLineSpacing = onLineSpacing,
                        onOrientation = onOrientation,
                    )
                    SettingsLocationNote(
                        "Font, spacing, and orientation are only available while reading.",
                    )
                }
                1 -> {
                    AudioSettingsTab(
                        engineKey = engineKey,
                        voiceId = voiceId,
                        engines = engines,
                        voices = voices,
                        speed = speed,
                        pitch = pitch,
                        prefetchCount = prefetchCount,
                        doubleTapPlay = doubleTapPlay,
                        autoScrollWithTts = autoScrollWithTts,
                        keepAliveUnderlay = keepAliveUnderlay,
                        continuousPcmPlayback = continuousPcmPlayback,
                        sentenceGapMs = sentenceGapMs,
                        onEngine = onEngine,
                        onVoice = onVoice,
                        onSpeed = onSpeed,
                        onPitch = onPitch,
                        onPrefetchCount = onPrefetchCount,
                        onDoubleTapPlay = onDoubleTapPlay,
                        onAutoScrollWithTts = onAutoScrollWithTts,
                        onKeepAliveUnderlay = onKeepAliveUnderlay,
                        onContinuousPcmPlayback = onContinuousPcmPlayback,
                        onSentenceGapMs = onSentenceGapMs,
                    )
                    SettingsLocationNote(
                        "Speed, pitch, and playback options are only available while reading.",
                    )
                }
                else -> {
                    FiltersSettingsTab(
                        filtersGlobal = filtersGlobal,
                        filtersGroups = filtersGroups,
                        filtersLocal = filtersLocal,
                        onAdd = onAddFilter,
                        onEdit = onEditFilter,
                        onSetEnabled = onSetFilterEnabled,
                    )
                    SettingsLocationNote(
                        "Local filters are only available while reading.",
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppearanceSettings(
    themeMode: ThemeMode,
    accentHue: Float,
    uiScale: Float,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onUiScale: (Float) -> Unit,
) {
    var accentDragging by remember { mutableStateOf(false) }
    var localAccent by remember { mutableFloatStateOf(accentHue) }
    val shownAccent = if (accentDragging) localAccent else accentHue
    var scaleDragging by remember { mutableStateOf(false) }
    var localScale by remember { mutableFloatStateOf(uiScale) }
    val shownScale = if (scaleDragging) localScale else uiScale

    SettingsLabel("Theme")
    ChipRow {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = themeMode == mode,
                onClick = { onTheme(mode) },
                label = { Text(mode.label) },
            )
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("Accent color")
    val chromaColors = remember(themeMode) {
        List(13) { i ->
            accentPrimary(i * 30f, themeMode)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FlowTokens.Space.XS, bottom = FlowTokens.Space.Hair),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlowTokens.Comp.AccentTrack)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(chromaColors)),
        )
        Slider(
            value = shownAccent,
            onValueChange = {
                accentDragging = true
                localAccent = it
                onAccentHue(it)
            },
            onValueChangeFinished = {
                accentDragging = false
                onAccentHue(localAccent)
            },
            valueRange = AccentHue.MIN..AccentHue.MAX,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accentPrimary(shownAccent, themeMode),
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("UI scale")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Small", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = shownScale,
            onValueChange = {
                scaleDragging = true
                localScale = it
            },
            onValueChangeFinished = {
                scaleDragging = false
                onUiScale(localScale)
            },
            valueRange = UiScale.MIN..UiScale.MAX,
            steps = ((UiScale.MAX - UiScale.MIN) / UiScale.STEP).toInt() - 1,
            modifier = Modifier.weight(1f).padding(horizontal = FlowTokens.Space.S),
        )
        Text(
            "${(shownScale * 100f).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun LayoutSettingsTab(
    themeMode: ThemeMode,
    accentHue: Float,
    uiScale: Float,
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    orientation: ReaderOrientation,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onUiScale: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    AppearanceSettings(
        themeMode = themeMode,
        accentHue = accentHue,
        uiScale = uiScale,
        onTheme = onTheme,
        onAccentHue = onAccentHue,
        onUiScale = onUiScale,
    )

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("Font")
    ChipRow {
        ReaderFont.entries.forEach { font ->
            FilterChip(
                selected = fontFamily == font,
                onClick = { onFontFamily(font) },
                label = { Text(font.label) },
            )
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("Font size")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Aa", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = fontScale,
            onValueChange = onFontScale,
            valueRange = 0.85f..1.75f,
            modifier = Modifier.weight(1f).padding(horizontal = FlowTokens.Space.S),
        )
        Text("Aa", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    SettingsLabel("Spacing")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Tight", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = lineSpacing,
            onValueChange = onLineSpacing,
            valueRange = 0.85f..1.8f,
            modifier = Modifier.weight(1f).padding(horizontal = FlowTokens.Space.S),
        )
        Text("Loose", style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("Orientation")
    ChipRow {
        ReaderOrientation.entries.forEach { mode ->
            FilterChip(
                selected = orientation == mode,
                onClick = { onOrientation(mode) },
                label = { Text(mode.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSettingsTab(
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float = 1f,
    pitch: Float = 1f,
    prefetchCount: Int = 1,
    doubleTapPlay: Boolean = false,
    autoScrollWithTts: Boolean = false,
    keepAliveUnderlay: Boolean = false,
    continuousPcmPlayback: Boolean = false,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onKeepAliveUnderlay: (Boolean) -> Unit = {},
    onContinuousPcmPlayback: (Boolean) -> Unit = {},
    onSentenceGapMs: (Int) -> Unit = {},
    compact: Boolean = false,
) {
    var engineOpen by remember { mutableStateOf(false) }
    var voiceOpen by remember { mutableStateOf(false) }
    var speedDragging by remember { mutableStateOf(false) }
    var pitchDragging by remember { mutableStateOf(false) }
    var prefetchDragging by remember { mutableStateOf(false) }
    var gapDragging by remember { mutableStateOf(false) }
    var localSpeed by remember { mutableFloatStateOf(speed) }
    var localPitch by remember { mutableFloatStateOf(pitch) }
    var localPrefetch by remember { mutableFloatStateOf(prefetchCount.toFloat()) }
    var localGap by remember { mutableFloatStateOf(sentenceGapMs.toFloat()) }
    val shownSpeed = if (speedDragging) localSpeed else speed
    val shownPitch = if (pitchDragging) localPitch else pitch
    val shownPrefetch = if (prefetchDragging) localPrefetch.roundToInt() else prefetchCount
    val shownGap = if (gapDragging) {
        TtsPrefs.coerceSentenceGapMs(localGap.roundToInt())
    } else {
        sentenceGapMs
    }

    val engineLabel = engines.firstOrNull { it.key == engineKey }?.label ?: engineKey
    val voiceLabel = voices.firstOrNull { it.id == voiceId }?.label
        ?: voices.firstOrNull()?.label
        ?: "Default"

    SettingsLabel("TTS Engine")
    ExposedDropdownMenuBox(
        expanded = engineOpen,
        onExpandedChange = { engineOpen = it },
    ) {
        OutlinedTextField(
            value = engineLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(engineOpen) },
            shape = FlowTokens.PanelShape,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = engineOpen,
            onDismissRequest = { engineOpen = false },
        ) {
            engines.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onEngine(option.key)
                        engineOpen = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    SettingsLabel("Voice")
    ExposedDropdownMenuBox(
        expanded = voiceOpen,
        onExpandedChange = { voiceOpen = it },
    ) {
        OutlinedTextField(
            value = voiceLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceOpen) },
            shape = FlowTokens.PanelShape,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = voiceOpen,
            onDismissRequest = { voiceOpen = false },
        ) {
            voices.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onVoice(option.id)
                        voiceOpen = false
                    },
                )
            }
        }
    }

    if (!compact) {
        Spacer(Modifier.height(FlowTokens.Space.M))
        SettingsLabel("Speed")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shownSpeed,
            onValueChange = {
                speedDragging = true
                localSpeed = (it * 20f).roundToInt() / 20f
            },
            onValueChangeFinished = {
                onSpeed(localSpeed)
                speedDragging = false
            },
            valueRange = 0.5f..2.5f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${"%.2f".format(shownSpeed).trimEnd('0').trimEnd('.')}×",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    SettingsLabel("Pitch")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shownPitch,
            onValueChange = {
                pitchDragging = true
                localPitch = (it * 20f).roundToInt() / 20f
            },
            onValueChangeFinished = {
                onPitch(localPitch)
                pitchDragging = false
            },
            valueRange = 0.5f..2f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${"%.2f".format(shownPitch).trimEnd('0').trimEnd('.')}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    SettingsLabel("Pre-cache sentences")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = if (prefetchDragging) localPrefetch else prefetchCount.toFloat(),
            onValueChange = {
                prefetchDragging = true
                localPrefetch = it
            },
            onValueChangeFinished = {
                onPrefetchCount(localPrefetch.roundToInt())
                prefetchDragging = false
            },
            valueRange = TtsPrefs.MIN_PREFETCH.toFloat()..TtsPrefs.MAX_PREFETCH.toFloat(),
            steps = TtsPrefs.MAX_PREFETCH - TtsPrefs.MIN_PREFETCH - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$shownPrefetch",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    SettingsLabel("Pause between sentences")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = if (gapDragging) localGap else sentenceGapMs.toFloat(),
            onValueChange = {
                gapDragging = true
                localGap = it
            },
            onValueChangeFinished = {
                onSentenceGapMs(TtsPrefs.coerceSentenceGapMs(localGap.roundToInt()))
                gapDragging = false
            },
            valueRange = TtsPrefs.MIN_SENTENCE_GAP_MS.toFloat()..TtsPrefs.MAX_SENTENCE_GAP_MS.toFloat(),
            steps = (TtsPrefs.MAX_SENTENCE_GAP_MS - TtsPrefs.MIN_SENTENCE_GAP_MS) /
                TtsPrefs.SENTENCE_GAP_STEP_MS - 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${shownGap} ms",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.L))
    AudioToggleRow(
        title = "Auto-scroll with playback",
        subtitle = "Keep the spoken text centered until you scroll away",
        checked = autoScrollWithTts,
        onCheckedChange = onAutoScrollWithTts,
    )
    Spacer(Modifier.height(FlowTokens.Space.S))
        AudioToggleRow(
            title = "Double-tap starts playback",
            subtitle = "Unavailable on body text while selection is on — use play controls",
            checked = doubleTapPlay,
            onCheckedChange = onDoubleTapPlay,
        )
    Spacer(Modifier.height(FlowTokens.Space.S))
        AudioToggleRow(
            title = "Keep audio alive",
            subtitle = "Quiet underlay while playing (helps some car systems)",
            checked = keepAliveUnderlay,
            onCheckedChange = onKeepAliveUnderlay,
        )
    Spacer(Modifier.height(FlowTokens.Space.S))
        AudioToggleRow(
            title = "Continuous PCM playback",
            subtitle = "Single audio stream from sentence clips (Edge)",
            checked = continuousPcmPlayback,
            onCheckedChange = onContinuousPcmPlayback,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FiltersSettingsTab(
    filtersGlobal: List<FilterRule>,
    filtersGroups: List<FilterRule>,
    filtersLocal: List<FilterRule>,
    onAdd: (FilterScope) -> Unit,
    onEdit: (FilterScope, FilterRule) -> Unit,
    onSetEnabled: (FilterScope, String, Boolean) -> Unit,
    scopes: List<FilterScope> = FilterScope.entries,
) {
    var scopeTab by remember { mutableIntStateOf(0) }
    val visibleScopes = scopes.ifEmpty { FilterScope.entries }
    val scope = visibleScopes[scopeTab.coerceIn(0, visibleScopes.lastIndex)]
    val rules = when (scope) {
        FilterScope.Global -> filtersGlobal
        FilterScope.Local -> filtersLocal
        FilterScope.Groups -> filtersGroups
    }

    SecondaryTabRow(selectedTabIndex = scopeTab.coerceIn(0, visibleScopes.lastIndex)) {
        visibleScopes.forEachIndexed { index, entry ->
            Tab(
                selected = scopeTab == index,
                onClick = { scopeTab = index },
                text = { Text(entry.label) },
            )
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Rules",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onAdd(scope) }) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(FlowTokens.Icon.M))
            Spacer(Modifier.width(FlowTokens.Space.XS))
            Text("Add")
        }
    }

    if (rules.isEmpty()) {
        Text(
            "No filters yet. Add a rule to replace text in the reader and TTS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = FlowTokens.Space.S),
        )
    } else {
        rules.sortedBy { it.order }.forEach { rule ->
            FilterRuleRow(
                rule = rule,
                onToggle = { onSetEnabled(scope, rule.id, it) },
                onClick = { onEdit(scope, rule) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }

    val enabledCount = rules.count { it.enabled }
    Text(
        "Enabled $enabledCount of ${rules.size}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = FlowTokens.Space.M),
    )
}

@Composable
private fun FilterRuleRow(
    rule: FilterRule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val title = rule.title.ifBlank { rule.pattern.ifBlank { "Untitled rule" } }
    val replacementLabel = rule.replacement.ifEmpty { "(empty)" }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = FlowTokens.Space.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.S)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${rule.pattern} → $replacementLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterRuleEditorOverlay(
    visible: Boolean,
    scope: FilterScope,
    initial: FilterRule,
    sampleSeed: String,
    isNew: Boolean,
    previewApply: (sample: String, draft: FilterRule, mode: FilterPreviewMode) -> FilterApplyResult,
    onSave: (FilterRule) -> Unit,
    onDelete: (() -> Unit)?,
    onSpeak: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    var title by remember(initial.id, visible) { mutableStateOf(initial.title) }
    var matchType by remember(initial.id, visible) { mutableStateOf(initial.matchType) }
    var wholeWords by remember(initial.id, visible) { mutableStateOf(initial.wholeWords) }
    var ttsOnly by remember(initial.id, visible) { mutableStateOf(initial.ttsOnly) }
    var pattern by remember(initial.id, visible) { mutableStateOf(initial.pattern) }
    var replacement by remember(initial.id, visible) { mutableStateOf(initial.replacement) }
    var previewMode by remember(initial.id, visible) { mutableStateOf(FilterPreviewMode.ThisRule) }
    var typeOpen by remember { mutableStateOf(false) }

    val draft = initial.copy(
        title = title,
        matchType = matchType,
        wholeWords = wholeWords,
        ttsOnly = ttsOnly,
        pattern = pattern,
        replacement = replacement,
    )
    val patternError = TextFilters.validatePattern(draft)
    val preview = remember(sampleSeed, draft, previewMode, scope) {
        previewApply(sampleSeed, draft, previewMode)
    }
    val regexMode = matchType == FilterMatchType.RegEx
    val colors = MaterialTheme.colorScheme
    val sampleAnnotated = remember(preview, colors.secondary) {
        buildAnnotatedString {
            append(preview.text)
            for (range in preview.replacedRanges) {
                val start = range.first.coerceIn(0, preview.text.length)
                val end = (range.last + 1).coerceIn(start, preview.text.length)
                if (start < end) {
                    addStyle(SpanStyle(color = colors.secondary), start, end)
                }
            }
        }
    }

    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = FlowTokens.ModalHeaderStart,
                    end = FlowTokens.ModalHeaderEnd,
                    top = FlowTokens.ModalHeaderTop,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isNew) "New ${scope.label} Filter" else "Edit ${scope.label} Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = FlowTokens.ModalTitleStart),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FlowTokens.Pad.CardIn,
                    vertical = FlowTokens.Space.S,
                ),
        ) {
            SettingsLabel("Title (optional)")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = FlowTokens.PanelShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FlowTokens.Space.M))
            SettingsLabel("Type")
            ExposedDropdownMenuBox(
                expanded = typeOpen,
                onExpandedChange = { typeOpen = it },
            ) {
                OutlinedTextField(
                    value = matchType.label,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeOpen) },
                    shape = FlowTokens.PanelShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = typeOpen,
                    onDismissRequest = { typeOpen = false },
                ) {
                    FilterMatchType.entries.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.label) },
                            onClick = {
                                matchType = type
                                typeOpen = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(FlowTokens.Space.S))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FlowTokens.Space.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
                    Text("Whole words only", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (regexMode) "Not used for RegEx" else "Match complete words",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = wholeWords && !regexMode,
                    onCheckedChange = { wholeWords = it },
                    enabled = !regexMode,
                )
            }

            Spacer(Modifier.height(FlowTokens.Space.XS))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = FlowTokens.Space.XS),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
                    Text("TTS only", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Apply when speaking, not on screen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = ttsOnly,
                    onCheckedChange = { ttsOnly = it },
                )
            }

            Spacer(Modifier.height(FlowTokens.Space.S))
            SettingsLabel("Find")
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                singleLine = true,
                isError = patternError != null,
                supportingText = patternError?.let { { Text(it) } },
                shape = FlowTokens.PanelShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FlowTokens.Space.S))
            SettingsLabel("Replace with")
            OutlinedTextField(
                value = replacement,
                onValueChange = { replacement = it },
                singleLine = true,
                placeholder = { Text("(empty deletes matches)") },
                trailingIcon = {
                    IconButton(
                        onClick = { onSpeak(replacement) },
                        enabled = replacement.isNotBlank(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak replacement")
                    }
                },
                shape = FlowTokens.PanelShape,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FlowTokens.Space.L))
            SettingsLabel("Preview")
            ChipRow {
                FilterPreviewMode.entries.forEach { mode ->
                    FilterChip(
                        selected = previewMode == mode,
                        onClick = { previewMode = mode },
                        label = { Text(mode.label) },
                    )
                }
            }
            Spacer(Modifier.height(FlowTokens.Space.S))
            FilterSampleField(
                annotated = sampleAnnotated,
                plain = preview.text,
                onSpeak = onSpeak,
            )

            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(FlowTokens.Icon.M))
                        Spacer(Modifier.width(FlowTokens.Space.XS))
                        Text("Delete")
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { onSave(draft) },
                    enabled = pattern.isNotBlank() && patternError == null,
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSampleField(
    annotated: AnnotatedString,
    plain: String,
    onSpeak: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = FlowTokens.PanelShape
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextFieldDefaults.DecorationBox(
            value = plain.ifEmpty { " " },
            innerTextField = {
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 2,
                    maxLines = 4,
                )
            },
            enabled = true,
            singleLine = false,
            visualTransformation = VisualTransformation.None,
            interactionSource = interaction,
            isError = false,
            label = { Text("Sample") },
            trailingIcon = {
                IconButton(
                    onClick = { onSpeak(plain) },
                    enabled = plain.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak preview")
                }
            },
            colors = OutlinedTextFieldDefaults.colors(),
            contentPadding = OutlinedTextFieldDefaults.contentPadding(
                start = FlowTokens.Space.L,
                end = FlowTokens.Space.L,
                top = FlowTokens.Space.L,
                bottom = FlowTokens.Space.L,
            ),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interaction,
                    shape = shape,
                )
            },
        )
    }
}

@Composable
private fun AudioToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun SettingsLocationNote(text: String) {
    Spacer(Modifier.height(FlowTokens.Space.L))
    Text(
        "* $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = FlowTokens.Space.S),
    )
}

@Composable
internal fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        content = content,
    )
}
