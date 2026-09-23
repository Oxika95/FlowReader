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
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.chrome.ReaderPanelShape


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsOverlay(
    visible: Boolean,
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    tts: TtsSettingsState,
    ttsCallbacks: TtsSettingsCallbacks,
    filters: FilterSettingsState,
    filterCallbacks: FilterSettingsCallbacks,
    onDismiss: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val ruleEditor = remember { com.personal.flowreader.ui.settings.DomainRuleEditorState() }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (!visible) ruleEditor.request = null
    }

    AppSettingsOverlay(
        visible = visible,
        onDismiss = onDismiss,
    ) {
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
                        themeMode = appearance.themeMode,
                        accentHue = appearance.accentHue,
                        uiScale = appearance.uiScale,
                        fontScale = appearance.fontScale,
                        fontFamily = appearance.fontFamily,
                        lineSpacing = appearance.lineSpacing,
                        justifyText = appearance.justifyText,
                        orientation = appearance.orientation,
                        showChapterHeadingsInBody = appearance.showChapterHeadingsInBody,
                        keepScreenAwake = appearance.keepScreenAwake,
                        onTheme = appearanceCallbacks.onTheme,
                        onAccentHue = appearanceCallbacks.onAccentHue,
                        onUiScale = appearanceCallbacks.onUiScale,
                        onFontScale = appearanceCallbacks.onFontScale,
                        onFontFamily = appearanceCallbacks.onFontFamily,
                        onLineSpacing = appearanceCallbacks.onLineSpacing,
                        onJustifyText = appearanceCallbacks.onJustifyText,
                        onOrientation = appearanceCallbacks.onOrientation,
                        onShowChapterHeadingsInBody = appearanceCallbacks.onShowChapterHeadingsInBody,
                        onKeepScreenAwake = appearanceCallbacks.onKeepScreenAwake,
                    )
                }
                1 -> {
                    AudioSettingsTab(
                        engineKey = tts.engineKey,
                        voiceId = tts.voiceId,
                        engines = tts.engines,
                        voices = tts.voices,
                        speed = tts.speed,
                        pitch = tts.pitch,
                        prefetchCount = tts.prefetchCount,
                        doubleTapPlay = tts.doubleTapPlay,
                        autoScrollWithTts = tts.autoScrollWithTts,
                        keepAliveUnderlay = tts.keepAliveUnderlay,
                        sentenceGapMs = tts.sentenceGapMs,
                        highlightSyncMs = tts.highlightSyncMs,
                        onEngine = ttsCallbacks.onEngine,
                        onVoice = ttsCallbacks.onVoice,
                        onSpeed = ttsCallbacks.onSpeed,
                        onPitch = ttsCallbacks.onPitch,
                        onPrefetchCount = ttsCallbacks.onPrefetchCount,
                        onDoubleTapPlay = ttsCallbacks.onDoubleTapPlay,
                        onAutoScrollWithTts = ttsCallbacks.onAutoScrollWithTts,
                        onKeepAliveUnderlay = ttsCallbacks.onKeepAliveUnderlay,
                        onSentenceGapMs = ttsCallbacks.onSentenceGapMs,
                        onHighlightSyncMs = ttsCallbacks.onHighlightSyncMs,
                    )
                }
                2 -> {
                    FiltersSettingsTab(
                        filtersGlobal = filters.filtersGlobal,
                        filtersGroups = filters.filtersGroups,
                        filtersLocal = filters.filtersLocal,
                        scopes = filters.filterScopes,
                        onAdd = filterCallbacks.onAddFilter,
                        onEdit = filterCallbacks.onEditFilter,
                        onSetEnabled = filterCallbacks.onSetFilterEnabled,
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
