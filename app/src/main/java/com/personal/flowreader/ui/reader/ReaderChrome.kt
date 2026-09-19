package com.personal.flowreader.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.roundToInt

internal val ReaderPanelShape = RoundedCornerShape(16.dp)
internal val ReaderPanelFeather = 20.dp
/** Left inset of reading text (locus rail gutter). */
internal val ReaderContentStartPadding = 28.dp
/** Right inset of reading text (list end padding + text end padding). */
internal val ReaderContentEndPadding = 24.dp
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
    /** When true, the card fills the parent's height (TOC / tall overlays). */
    fillHeight: Boolean = false,
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
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
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
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onToc: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        ReaderPanelSurface(modifier = Modifier.fillMaxWidth(), matchReaderWidth = true) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            chapter,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onToc) {
                        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
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
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        FloatingPanel {
            Box(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence")
                    }
                    FilledIconButton(
                        onClick = {
                            if (playing) onPause() else onPlay()
                        },
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(28.dp),
                        )
                    }
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
                            .padding(horizontal = 8.dp, vertical = 8.dp),
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
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

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
                            .padding(bottom = 8.dp),
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
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            if (tab == 0) {
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
                            } else {
                                AudioSettingsTab(
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
                            }
                        }
                    }
                }
            }
        }
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
private fun AudioSettingsTab(
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float,
    pitch: Float,
    prefetchCount: Int,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPrefetchCount: (Int) -> Unit,
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
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$shownPrefetch",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
