package com.personal.flowreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterMatchType
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.ReaderFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.data.UiScale
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.max
import kotlin.math.min
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
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
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
            border = BorderStroke(FlowTokens.Stroke.Hairline, borderColor),
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
    /** Soft halo outside the border; None keeps a hard edge aligned with reading text. */
    feather: Dp = ReaderPanelFeather,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReaderPanelSurface(
        modifier = modifier,
        matchReaderWidth = true,
        feather = feather,
    ) {
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
    onSettings: () -> Unit,
) {
    val cover by rememberBookCover(storedPath, maxEdge = 512)
    val cardBg = MaterialTheme.colorScheme.background
    val canBlur = Build.VERSION.SDK_INT >= 31
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        ReaderPanelSurface(
            modifier = Modifier.fillMaxWidth(),
            matchReaderWidth = true,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                BannerCoverUnderlay(
                    cover = cover,
                    blur = canBlur,
                    cardBg = cardBg,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(bottom = FlowTokens.Comp.ProgressBar)
                        // Keep cover clear of back / settings hit targets.
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
                        onClick = onSettings,
                        modifier = Modifier.size(FlowTokens.Icon.Hero),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
    onToc: () -> Unit,
    onScrollLock: () -> Unit,
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
                        onClick = onToc,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
                    }
                    IconButton(
                        onClick = onScrollLock,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock scroll to TTS")
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
 * Floating unlock control that sits in the same bottom-end slot as the media card's
 * scroll-lock button while chrome is hidden in scroll-lock mode.
 */
@Composable
internal fun ScrollLockUnlockButton(
    visible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playSize = FlowTokens.Comp.Fab
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(playSize),
            contentAlignment = Alignment.Center,
        ) {
            // Match MediaControlCard: reader gutter + panel content pad to the icon slot.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = ReaderContentStartPadding + FlowTokens.Space.S)
                    .size(FlowTokens.Icon.Hero)
                    .pointerInput(onUnlock) {
                        detectTapGestures(onDoubleTap = { onUnlock() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(FlowTokens.Comp.ButtonSecondary)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                        ),
                )
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = "Double-tap to unlock scroll",
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
    justifyText: Boolean,
    orientation: ReaderOrientation,
    showChapterHeadingsInBody: Boolean,
    keepScreenAwake: Boolean,
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
    sentenceGapMs: Int,
    highlightSyncMs: Int,
    filtersGlobal: List<FilterRule>,
    filtersGroups: List<FilterRule>,
    filtersLocal: List<FilterRule>,
    filterScopes: List<FilterScope> = FilterScope.entries,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onUiScale: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onJustifyText: (Boolean) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onShowChapterHeadingsInBody: (Boolean) -> Unit,
    onKeepScreenAwake: (Boolean) -> Unit,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPrefetchCount: (Int) -> Unit,
    onDoubleTapPlay: (Boolean) -> Unit,
    onAutoScrollWithTts: (Boolean) -> Unit,
    onKeepAliveUnderlay: (Boolean) -> Unit,
    onSentenceGapMs: (Int) -> Unit,
    onHighlightSyncMs: (Int) -> Unit,
    onAddFilter: (FilterScope) -> Unit,
    onEditFilter: (FilterScope, FilterRule) -> Unit,
    onSetFilterEnabled: (FilterScope, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val ruleEditor = remember { com.personal.flowreader.ui.settings.DomainRuleEditorState() }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (!visible) ruleEditor.request = null
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

        val primaryTabs = listOf("Layout", "Audio", "Filters", "Import")
        PrimaryTabRow(selectedTabIndex = tab) {
            primaryTabs.forEachIndexed { index, label ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(label) },
                )
            }
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
                        justifyText = justifyText,
                        orientation = orientation,
                        showChapterHeadingsInBody = showChapterHeadingsInBody,
                        keepScreenAwake = keepScreenAwake,
                        onTheme = onTheme,
                        onAccentHue = onAccentHue,
                        onUiScale = onUiScale,
                        onFontScale = onFontScale,
                        onFontFamily = onFontFamily,
                        onLineSpacing = onLineSpacing,
                        onJustifyText = onJustifyText,
                        onOrientation = onOrientation,
                        onShowChapterHeadingsInBody = onShowChapterHeadingsInBody,
                        onKeepScreenAwake = onKeepScreenAwake,
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
                        sentenceGapMs = sentenceGapMs,
                        highlightSyncMs = highlightSyncMs,
                        onEngine = onEngine,
                        onVoice = onVoice,
                        onSpeed = onSpeed,
                        onPitch = onPitch,
                        onPrefetchCount = onPrefetchCount,
                        onDoubleTapPlay = onDoubleTapPlay,
                        onAutoScrollWithTts = onAutoScrollWithTts,
                        onKeepAliveUnderlay = onKeepAliveUnderlay,
                        onSentenceGapMs = onSentenceGapMs,
                        onHighlightSyncMs = onHighlightSyncMs,
                    )
                }
                2 -> {
                    FiltersSettingsTab(
                        filtersGlobal = filtersGlobal,
                        filtersGroups = filtersGroups,
                        filtersLocal = filtersLocal,
                        scopes = filterScopes,
                        onAdd = onAddFilter,
                        onEdit = onEditFilter,
                        onSetEnabled = onSetFilterEnabled,
                    )
                }
                else -> {
                    SharingSettingsHost(ruleEditor)
                }
            }
        }
    }

    if (visible) {
        com.personal.flowreader.ui.settings.DomainRuleEditorHost(ruleEditor)
    }
}

@Composable
private fun SharingSettingsHost(ruleEditor: com.personal.flowreader.ui.settings.DomainRuleEditorState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.personal.flowreader.FlowApp
    val lifecycleOwner = LocalLifecycleOwner.current
    var prefs by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.personal.flowreader.share.SharePrefs())
    }
    var rules by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(emptyList<com.personal.flowreader.share.ShareDomainRule>())
    }
    var overlayOk by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context),
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        prefs = withContext(Dispatchers.IO) { app.settings.shareOnce() }
        rules = withContext(Dispatchers.IO) { app.settings.shareDomainRulesOnce() }
        overlayOk = com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
    }
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            overlayOk = com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
        }
    }
    com.personal.flowreader.ui.settings.SharingSettingsTab(
        prefs = prefs,
        rules = rules,
        overlayAllowed = overlayOk,
        plugins = app.plugins.available.map {
            com.personal.flowreader.ui.settings.SharePluginOption(it.id, it.title)
        },
        editorState = ruleEditor,
        onShowQue = { enabled ->
            prefs = prefs.copy(showQueInShareSheet = enabled)
            app.appScope.launch {
                app.settings.setShowQueInShareSheet(enabled)
                com.personal.flowreader.share.ShareQueAliasController.setEnabled(app, enabled)
            }
        },
        onManualOverride = { enabled ->
            val mode = if (enabled) {
                com.personal.flowreader.share.ShareAskMode.Ask
            } else {
                com.personal.flowreader.share.ShareAskMode.Auto
            }
            prefs = prefs.copy(askMode = mode)
            app.appScope.launch { app.settings.setShareAskMode(mode) }
            if (enabled &&
                !com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
            ) {
                context.startActivity(
                    com.personal.flowreader.share.ShareOverlayPermission.settingsIntent(context),
                )
            }
            overlayOk = com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
        },
        onSaveRules = { next ->
            rules = next
            app.appScope.launch { app.settings.setShareDomainRules(next) }
        },
    )
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
    ThemeSettingsPane(
        themeMode = themeMode,
        accentHue = accentHue,
        onTheme = onTheme,
        onAccentHue = onAccentHue,
    )
    Spacer(Modifier.height(FlowTokens.Space.M))
    UiScaleSettingsPane(
        uiScale = uiScale,
        onUiScale = onUiScale,
    )
}

@Composable
private fun ThemeSettingsPane(
    themeMode: ThemeMode,
    accentHue: Float,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
) {
    var accentDragging by remember { mutableStateOf(false) }
    var localAccent by remember { mutableFloatStateOf(accentHue) }
    val shownAccent = if (accentDragging) localAccent else accentHue

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
}

@Composable
private fun UiScaleSettingsPane(
    uiScale: Float,
    onUiScale: (Float) -> Unit,
) {
    var scaleDragging by remember { mutableStateOf(false) }
    var localScale by remember { mutableFloatStateOf(uiScale) }
    val shownScale = if (scaleDragging) localScale else uiScale

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LayoutSettingsTab(
    themeMode: ThemeMode,
    accentHue: Float,
    uiScale: Float,
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    justifyText: Boolean,
    orientation: ReaderOrientation,
    showChapterHeadingsInBody: Boolean,
    keepScreenAwake: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onUiScale: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onJustifyText: (Boolean) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onShowChapterHeadingsInBody: (Boolean) -> Unit,
    onKeepScreenAwake: (Boolean) -> Unit,
) {
    var layoutTab by remember { mutableIntStateOf(0) }
    val layoutTabs = listOf("Theme", "UI", "Font")

    SettingsSubTabRow(
        selectedTabIndex = layoutTab,
        labels = layoutTabs,
        onTabSelected = { layoutTab = it },
    )

    Spacer(Modifier.height(FlowTokens.Space.M))
    when (layoutTab) {
        0 -> ThemeSettingsPane(
            themeMode = themeMode,
            accentHue = accentHue,
            onTheme = onTheme,
            onAccentHue = onAccentHue,
        )
        1 -> {
            UiScaleSettingsPane(
                uiScale = uiScale,
                onUiScale = onUiScale,
            )
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
            Spacer(Modifier.height(FlowTokens.Space.L))
            AudioToggleRow(
                title = "Chapter headings in body",
                subtitle = "Show each chapter title in the reading text",
                checked = showChapterHeadingsInBody,
                onCheckedChange = onShowChapterHeadingsInBody,
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            AudioToggleRow(
                title = "Keep screen awake",
                subtitle = "Prevent the display from sleeping while reading",
                checked = keepScreenAwake,
                onCheckedChange = onKeepScreenAwake,
            )
        }
        else -> FontSettingsTab(
            fontScale = fontScale,
            fontFamily = fontFamily,
            lineSpacing = lineSpacing,
            justifyText = justifyText,
            onFontScale = onFontScale,
            onFontFamily = onFontFamily,
            onLineSpacing = onLineSpacing,
            onJustifyText = onJustifyText,
        )
    }
}

@Composable
private fun FontSettingsTab(
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    justifyText: Boolean,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onJustifyText: (Boolean) -> Unit,
) {
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

    Spacer(Modifier.height(FlowTokens.Space.L))
    AudioToggleRow(
        title = "Justify text",
        subtitle = "Stretch each line of body text from edge to edge",
        checked = justifyText,
        onCheckedChange = onJustifyText,
    )
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
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onKeepAliveUnderlay: (Boolean) -> Unit = {},
    onSentenceGapMs: (Int) -> Unit = {},
    onHighlightSyncMs: (Int) -> Unit = {},
) {
    var audioTab by remember { mutableIntStateOf(0) }
    val audioTabs = listOf("Voice", "Playback")

    SettingsSubTabRow(
        selectedTabIndex = audioTab,
        labels = audioTabs,
        onTabSelected = { audioTab = it },
    )

    Spacer(Modifier.height(FlowTokens.Space.M))
    when (audioTab) {
        0 -> VoiceSettingsTab(
            engineKey = engineKey,
            voiceId = voiceId,
            engines = engines,
            voices = voices,
            speed = speed,
            pitch = pitch,
            prefetchCount = prefetchCount,
            onEngine = onEngine,
            onVoice = onVoice,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onPrefetchCount = onPrefetchCount,
        )
        else -> PlaybackSettingsTab(
            doubleTapPlay = doubleTapPlay,
            autoScrollWithTts = autoScrollWithTts,
            keepAliveUnderlay = keepAliveUnderlay,
            sentenceGapMs = sentenceGapMs,
            highlightSyncMs = highlightSyncMs,
            onDoubleTapPlay = onDoubleTapPlay,
            onAutoScrollWithTts = onAutoScrollWithTts,
            onKeepAliveUnderlay = onKeepAliveUnderlay,
            onSentenceGapMs = onSentenceGapMs,
            onHighlightSyncMs = onHighlightSyncMs,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSettingsTab(
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float = 1f,
    pitch: Float = 1f,
    prefetchCount: Int = 1,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
) {
    var engineOpen by remember { mutableStateOf(false) }
    var voiceOpen by remember { mutableStateOf(false) }
    var speedDragging by remember { mutableStateOf(false) }
    var pitchDragging by remember { mutableStateOf(false) }
    var prefetchDragging by remember { mutableStateOf(false) }
    var localSpeed by remember { mutableFloatStateOf(speed) }
    var localPitch by remember { mutableFloatStateOf(pitch) }
    var localPrefetch by remember { mutableFloatStateOf(prefetchCount.toFloat()) }
    val shownSpeed = if (speedDragging) localSpeed else speed
    val shownPitch = if (pitchDragging) localPitch else pitch
    val shownPrefetch = if (prefetchDragging) localPrefetch.roundToInt() else prefetchCount

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
}

@Composable
internal fun PlaybackSettingsTab(
    doubleTapPlay: Boolean = false,
    autoScrollWithTts: Boolean = false,
    keepAliveUnderlay: Boolean = false,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onKeepAliveUnderlay: (Boolean) -> Unit = {},
    onSentenceGapMs: (Int) -> Unit = {},
    onHighlightSyncMs: (Int) -> Unit = {},
) {
    var gapDragging by remember { mutableStateOf(false) }
    var localGap by remember { mutableFloatStateOf(sentenceGapMs.toFloat()) }
    val shownGap = if (gapDragging) {
        TtsPrefs.coerceSentenceGapMs(localGap.roundToInt())
    } else {
        sentenceGapMs
    }
    var syncDragging by remember { mutableStateOf(false) }
    var localSync by remember { mutableFloatStateOf(highlightSyncMs.toFloat()) }
    val shownSync = if (syncDragging) {
        TtsPrefs.coerceHighlightSyncMs(localSync.roundToInt())
    } else {
        highlightSyncMs
    }

    // Title + live value, then description, then slider (matches AudioToggleRow text hierarchy).
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Sentence offset",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatSentenceGapLabel(shownGap),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        when {
            shownGap < 0 -> "Negative = equal-power crossfade between sentences"
            shownGap > 0 -> "Positive = pause between sentences"
            else -> "Zero = butt-join (gapless)"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CenterOriginSlider(
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
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(FlowTokens.Space.L))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Word highlight sync",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatHighlightSyncLabel(shownSync),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        "Shift Edge word highlight earlier (−) or later (+)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CenterOriginSlider(
        value = if (syncDragging) localSync else highlightSyncMs.toFloat(),
        onValueChange = {
            syncDragging = true
            localSync = it
        },
        onValueChangeFinished = {
            onHighlightSyncMs(TtsPrefs.coerceHighlightSyncMs(localSync.roundToInt()))
            syncDragging = false
        },
        valueRange = TtsPrefs.MIN_HIGHLIGHT_SYNC_MS.toFloat()..
            TtsPrefs.MAX_HIGHLIGHT_SYNC_MS.toFloat(),
        steps = (TtsPrefs.MAX_HIGHLIGHT_SYNC_MS - TtsPrefs.MIN_HIGHLIGHT_SYNC_MS) /
            TtsPrefs.HIGHLIGHT_SYNC_STEP_MS - 1,
        modifier = Modifier.fillMaxWidth(),
    )

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

    SettingsSubTabRow(
        selectedTabIndex = scopeTab.coerceIn(0, visibleScopes.lastIndex),
        labels = visibleScopes.map { it.label },
        onTabSelected = { scopeTab = it },
    )

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
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

private fun formatSentenceGapLabel(ms: Int): String = when {
    ms > 0 -> "+$ms ms pause"
    ms < 0 -> "$ms ms fade"
    else -> "0 ms"
}

private fun formatHighlightSyncLabel(ms: Int): String = when {
    ms > 0 -> "+$ms ms"
    ms < 0 -> "$ms ms"
    else -> "0 ms"
}

/**
 * Slider whose active track grows from value 0 (visual center when the range is
 * symmetric) toward the thumb — left for negative, right for positive.
 *
 * Matches [SliderDefaults.Track] geometry (16.dp height, thumb gaps, inside
 * corners, step ticks) with a centered active range and − / + end labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CenterOriginSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = colors,
        track = { state ->
            CenterOriginSliderTrack(
                value = state.value,
                valueRange = state.valueRange,
                steps = steps,
                activeColor = colors.activeTrackColor,
                inactiveColor = colors.inactiveTrackColor,
                activeTickColor = colors.activeTickColor,
                inactiveTickColor = colors.inactiveTickColor,
            )
        },
    )
}

/** M3 SliderTokens: ActiveTrackHeight / ActiveHandleLeadingSpace / HandleWidth. */
private val CenterOriginTrackHeight = 16.dp
private val CenterOriginThumbWidth = 4.dp
private val CenterOriginThumbTrackGap = 6.dp
private val CenterOriginInsideCorner = 2.dp

@Composable
private fun CenterOriginSliderTrack(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    activeColor: Color,
    inactiveColor: Color,
    activeTickColor: Color,
    inactiveTickColor: Color,
    modifier: Modifier = Modifier,
) {
    val stopSize = SliderDefaults.TrackStopIndicatorSize
    val textMeasurer = rememberTextMeasurer()
    val trackPath = remember { Path() }
    Canvas(modifier = modifier.fillMaxWidth().height(CenterOriginTrackHeight)) {
        val trackH = size.height
        val outerCorner = trackH / 2f
        val insideCorner = CenterOriginInsideCorner.toPx()
        val gap = CenterOriginThumbWidth.toPx() / 2f + CenterOriginThumbTrackGap.toPx()
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val zeroX = ((0f - valueRange.start) / span).coerceIn(0f, 1f) * size.width
        val thumbX = ((value - valueRange.start) / span).coerceIn(0f, 1f) * size.width
        val thumbLeft = thumbX - gap
        val thumbRight = thumbX + gap
        val markerR = stopSize.toPx() / 2f

        fun drawSegment(
            left: Float,
            right: Float,
            color: Color,
            startCorner: Float,
            endCorner: Float,
        ) {
            val width = right - left
            if (width <= 0.5f) return
            val maxR = min(trackH / 2f, width / 2f)
            val startR = startCorner.coerceIn(0f, maxR)
            val endR = endCorner.coerceIn(0f, maxR)
            trackPath.rewind()
            trackPath.addRoundRect(
                RoundRect(
                    rect = Rect(left, 0f, right, trackH),
                    topLeft = CornerRadius(startR, startR),
                    topRight = CornerRadius(endR, endR),
                    bottomRight = CornerRadius(endR, endR),
                    bottomLeft = CornerRadius(startR, startR),
                ),
            )
            drawPath(trackPath, color)
        }

        // Same corner rules as SliderDefaults.Track: full round on outer ends,
        // insideCorner on grip-facing ends. Active nose is a full round centered
        // on zero so the center tick sits on that cap: ( • ==== ]|[
        // Draw inactive through the center first, then active on top so the nose
        // isn't covered (positive and negative must use the same order).
        val activeLeft: Float
        val activeRight: Float
        when {
            thumbX > zeroX + 0.5f -> {
                activeLeft = zeroX - outerCorner
                activeRight = thumbLeft
                drawSegment(0f, zeroX, inactiveColor, outerCorner, insideCorner)
                drawSegment(thumbRight, size.width, inactiveColor, insideCorner, outerCorner)
                drawSegment(activeLeft, activeRight, activeColor, outerCorner, insideCorner)
            }
            thumbX < zeroX - 0.5f -> {
                activeLeft = thumbRight
                activeRight = zeroX + outerCorner
                drawSegment(0f, thumbLeft, inactiveColor, outerCorner, insideCorner)
                drawSegment(zeroX, size.width, inactiveColor, insideCorner, outerCorner)
                drawSegment(activeLeft, activeRight, activeColor, insideCorner, outerCorner)
            }
            else -> {
                activeLeft = zeroX
                activeRight = zeroX
                drawSegment(0f, thumbLeft, inactiveColor, outerCorner, insideCorner)
                drawSegment(thumbRight, size.width, inactiveColor, insideCorner, outerCorner)
            }
        }

        fun isOnActive(x: Float): Boolean =
            x in activeLeft..activeRight && activeRight > activeLeft + 0.5f

        fun tickColor(x: Float): Color =
            if (isOnActive(x)) activeTickColor else inactiveTickColor

        fun inThumbGap(x: Float): Boolean = x in thumbLeft..thumbRight

        // M3 ticks lerp between the inset stop positions — not 0..width — so the
        // outermost slots sit exactly where stop indicators (our − / +) go.
        val tickStart = outerCorner
        val tickEnd = size.width - outerCorner
        fun tickX(index: Int, tickCount: Int): Float =
            tickStart + (tickEnd - tickStart) * (index.toFloat() / (tickCount - 1))

        // Step ticks; index 0 / last are replaced by − / + (same as M3 skipping
        // end ticks when stop indicators are drawn).
        if (steps > 0) {
            val tickCount = steps + 2
            for (i in 1 until tickCount - 1) {
                val x = tickX(i, tickCount)
                if (inThumbGap(x)) continue
                if (kotlin.math.abs(x - zeroX) < markerR * 2f) continue
                drawCircle(
                    color = tickColor(x),
                    radius = markerR,
                    center = Offset(x, center.y),
                )
            }
        }

        // End labels + center zero marker (track layer → under the thumb).
        // Hide when the thumb gap covers that slot — same as tick culling, and
        // matches M3 not drawing a stop when the inactive end segment is gone.
        fun drawEndLabel(label: String, centerX: Float, color: Color) {
            val layout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    centerX - layout.size.width / 2f,
                    center.y - layout.size.height / 2f,
                ),
            )
        }
        if (!inThumbGap(tickStart)) {
            drawEndLabel("−", tickStart, tickColor(tickStart))
        }
        // Center zero uses active-tick color so it reads on the active nose
        // (same contrast as ticks on the filled side of other sliders).
        if (!inThumbGap(zeroX)) {
            drawCircle(
                color = activeTickColor,
                radius = markerR,
                center = Offset(zeroX, center.y),
            )
        }
        if (!inThumbGap(tickEnd)) {
            drawEndLabel("+", tickEnd, tickColor(tickEnd))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSubTabRow(
    selectedTabIndex: Int,
    labels: List<String>,
    onTabSelected: (Int) -> Unit,
) {
    val height = FlowTokens.Comp.SubTabBar
    SecondaryTabRow(
        selectedTabIndex = selectedTabIndex,
        modifier = Modifier.height(height),
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                },
                modifier = Modifier.height(height),
            )
        }
    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        content()
    }
}
