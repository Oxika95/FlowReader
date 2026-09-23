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
internal fun ThemeSettingsPane(
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
internal fun UiScaleSettingsPane(
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
internal fun LayoutSettingsTab(
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
            SettingsToggleRow(
                title = "Chapter headings in body",
                subtitle = "Show each chapter title in the reading text",
                checked = showChapterHeadingsInBody,
                onCheckedChange = onShowChapterHeadingsInBody,
            )
            Spacer(Modifier.height(FlowTokens.Space.S))
            SettingsToggleRow(
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
internal fun FontSettingsTab(
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
    SettingsToggleRow(
        title = "Justify text",
        subtitle = "Stretch each line of body text from edge to edge",
        checked = justifyText,
        onCheckedChange = onJustifyText,
    )
}
