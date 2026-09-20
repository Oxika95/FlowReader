package com.personal.flowreader.ui.reader

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.withContext
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.roundToInt

internal val ReaderPanelShape = RoundedCornerShape(16.dp)
internal val ReaderPanelFeather = 20.dp
/** Left inset of reading text (locus rail gutter); bars are centered in this width. */
internal val ReaderContentStartPadding = 16.dp
/** End padding on the reading LazyColumn (right gutter). */
internal val ReaderListEndPadding = 16.dp
/** Extra end padding on the paragraph text itself. */
internal val ReaderTextEndPadding = 0.dp
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
                    if (feather > 0.dp) {
                        Modifier.drawBehind {
                            drawReaderPanelFeather(bg, 16.dp, feather)
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
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            border = BorderStroke(1.dp, outline),
        ) {
            content()
        }
    }
}

@Composable
internal fun FloatingPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
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
                        .padding(bottom = 3.dp)
                        // Keep cover clear of back / TOC hit targets.
                        .padding(horizontal = BannerCoverSideInset),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .padding(bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
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
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                )
            }
        }
    }
}

/** Horizontal inset so the cover band ends before the side icon buttons. */
private val BannerCoverSideInset = 48.dp

@Composable
private fun rememberReaderCover(storedPath: String, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = null, storedPath, maxEdge) {
        value = if (storedPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                EpubCover.loadBitmap(File(storedPath), maxEdge)?.asImageBitmap()
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
                .then(if (blur) Modifier.blur(6.dp) else Modifier),
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
    val playSize = 56.dp
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
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
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
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
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
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/**
 * Scrim + centered scrolling card shared by the Settings and TOC modals: tap the scrim to
 * dismiss, taps inside the card are swallowed, and the card never outgrows the viewport.
 */
@Composable
internal fun ReaderModalScaffold(
    visible: Boolean,
    contentPadding: PaddingValues,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.42f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val maxCardHeight = maxHeight - ReaderOverlayVerticalPad * 2
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
            ) {
                ReaderPanelSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxCardHeight)
                        .wrapContentHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    matchReaderWidth = true,
                    feather = 0.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxCardHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(contentPadding),
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
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        onDismiss = onDismiss,
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
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close contents")
            }
        }
        chapters.forEachIndexed { index, name ->
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (index == chapterIndex) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
                color = if (index == chapterIndex) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChapter(index) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (index < chapters.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
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
    filtersGlobal: List<FilterRule>,
    filtersGroups: List<FilterRule>,
    filtersLocal: List<FilterRule>,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
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
    onAddFilter: (FilterScope) -> Unit,
    onEditFilter: (FilterScope, FilterRule) -> Unit,
    onSetFilterEnabled: (FilterScope, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = 8.dp),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
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
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            when (tab) {
                0 -> {
                    LayoutSettingsTab(
                        themeMode = themeMode,
                        accentHue = accentHue,
                        fontScale = fontScale,
                        fontFamily = fontFamily,
                        lineSpacing = lineSpacing,
                        orientation = orientation,
                        onTheme = onTheme,
                        onAccentHue = onAccentHue,
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
                        onEngine = onEngine,
                        onVoice = onVoice,
                        onSpeed = onSpeed,
                        onPitch = onPitch,
                        onPrefetchCount = onPrefetchCount,
                        onDoubleTapPlay = onDoubleTapPlay,
                        onAutoScrollWithTts = onAutoScrollWithTts,
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

    Spacer(Modifier.height(12.dp))
    SettingsLabel("Accent color")
    val chromaColors = remember(themeMode) {
        List(13) { i ->
            accentPrimary(i * 30f, themeMode)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(11.dp))
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
private fun LayoutSettingsTab(
    themeMode: ThemeMode,
    accentHue: Float,
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    orientation: ReaderOrientation,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
) {
    AppearanceSettings(
        themeMode = themeMode,
        accentHue = accentHue,
        onTheme = onTheme,
        onAccentHue = onAccentHue,
    )

    Spacer(Modifier.height(12.dp))
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

    Spacer(Modifier.height(12.dp))
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
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("Aa", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }

    Spacer(Modifier.height(8.dp))
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
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text("Loose", style = MaterialTheme.typography.labelSmall)
    }

    Spacer(Modifier.height(12.dp))
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
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    compact: Boolean = false,
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
            shape = RoundedCornerShape(16.dp),
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

    Spacer(Modifier.height(12.dp))
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
            shape = RoundedCornerShape(16.dp),
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
        Spacer(Modifier.height(12.dp))
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
            modifier = Modifier.width(48.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
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
            modifier = Modifier.width(48.dp),
        )
    }

    Spacer(Modifier.height(8.dp))
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
            modifier = Modifier.width(48.dp),
        )
    }

    Spacer(Modifier.height(16.dp))
    AudioToggleRow(
        title = "Auto-scroll with playback",
        subtitle = "Keep the spoken text centered until you scroll away",
        checked = autoScrollWithTts,
        onCheckedChange = onAutoScrollWithTts,
    )
    Spacer(Modifier.height(8.dp))
        AudioToggleRow(
            title = "Double-tap starts playback",
            subtitle = "Unavailable on body text while selection is on — use play controls",
            checked = doubleTapPlay,
            onCheckedChange = onDoubleTapPlay,
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

    Spacer(Modifier.height(12.dp))
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
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
    }

    if (rules.isEmpty()) {
        Text(
            "No filters yet. Add a rule to replace text in the reader and TTS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
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
        modifier = Modifier.padding(top = 12.dp),
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
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
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
        contentPadding = PaddingValues(bottom = 8.dp),
        onDismiss = onDismiss,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isNew) "New ${scope.label} Filter" else "Edit ${scope.label} Filter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            SettingsLabel("Title (optional)")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
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
                    shape = RoundedCornerShape(16.dp),
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

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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

            Spacer(Modifier.height(8.dp))
            SettingsLabel("Find")
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                singleLine = true,
                isError = patternError != null,
                supportingText = patternError?.let { { Text(it) } },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(8.dp))
            FilterSampleField(
                annotated = sampleAnnotated,
                plain = preview.text,
                onSpeak = onSpeak,
            )

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
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
    val shape = RoundedCornerShape(16.dp)
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
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 16.dp,
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
    Spacer(Modifier.height(16.dp))
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
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
internal fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
