package com.personal.flowreader.ui.settings


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
import com.personal.flowreader.ui.settings.AppSettingsOverlay
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.ModalHeaderRow
import com.personal.flowreader.ui.settings.SettingsToggleRow
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt


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
    minSignal: Float = TtsPrefs.DEFAULT_MIN_SIGNAL,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onMinSignal: (Float, Boolean) -> Unit = { _, _ -> },
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
            minSignal = minSignal,
            sentenceGapMs = sentenceGapMs,
            highlightSyncMs = highlightSyncMs,
            onDoubleTapPlay = onDoubleTapPlay,
            onAutoScrollWithTts = onAutoScrollWithTts,
            onMinSignal = onMinSignal,
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
    minSignal: Float = TtsPrefs.DEFAULT_MIN_SIGNAL,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onMinSignal: (Float, Boolean) -> Unit = { _, _ -> },
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
    var signalDragging by remember { mutableStateOf(false) }
    var localSignal by remember { mutableFloatStateOf(minSignal) }
    val shownSignal = if (signalDragging) localSignal else minSignal

    SettingsToggleRow(
        title = "Auto-scroll with playback",
        subtitle = "Keep the spoken text centered until you scroll away",
        checked = autoScrollWithTts,
        onCheckedChange = onAutoScrollWithTts,
    )
    Spacer(Modifier.height(FlowTokens.Space.S))
    SettingsToggleRow(
        title = "Double-tap starts playback",
        subtitle = "Unavailable on body text while selection is on — use play controls",
        checked = doubleTapPlay,
        onCheckedChange = onDoubleTapPlay,
    )

    Spacer(Modifier.height(FlowTokens.Space.L))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Tonal underlay",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            formatMinSignalLabel(shownSignal),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        "Prevents audio drop out on some hardware at low tones.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Slider(
        value = TtsPrefs.tonalUnderlayIndex(shownSignal).toFloat(),
        onValueChange = {
            val index = it.roundToInt().coerceIn(TtsPrefs.TONAL_UNDERLAY_STEPS.indices)
            val level = TtsPrefs.TONAL_UNDERLAY_STEPS[index]
            signalDragging = true
            localSignal = level
            onMinSignal(level, false)
        },
        onValueChangeFinished = {
            onMinSignal(TtsPrefs.coerceMinSignal(localSignal), true)
            signalDragging = false
        },
        valueRange = 0f..TtsPrefs.TONAL_UNDERLAY_STEPS.lastIndex.toFloat(),
        steps = TtsPrefs.TONAL_UNDERLAY_STEPS.size - 2,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(FlowTokens.Space.L))
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

private fun formatMinSignalLabel(level: Float): String =
    TtsPrefs.TONAL_UNDERLAY_LABELS[TtsPrefs.tonalUnderlayIndex(level)]

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
