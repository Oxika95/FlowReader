package com.personal.flowreader.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SettingsSections = listOf("About", "Audio", "Filters", "Import", "Layout")

/**
 * App settings (shared by Library and Reader): one fullscreen card with pinned primary tabs.
 * Editors opened from a tab (filter, router, parse rules) stack above this card.
 */
@Composable
internal fun SettingsOverlay(
    visible: Boolean,
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    tts: TtsSettingsState,
    ttsCallbacks: TtsSettingsCallbacks,
    filters: FilterSettingsState,
    filterCallbacks: FilterSettingsCallbacks,
    debugEnabled: Boolean,
    onDebugEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ruleEditor = remember { DomainRuleEditorState() }
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (!visible) ruleEditor.request = null
    }

    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Settings",
        tabs = {
            FlowTabBar(
                tabs = flowTextTabs(SettingsSections, tab) { tab = it },
                level = FlowTabLevel.Primary,
                inset = FlowTokens.Pad.CardBody,
            )
        },
    ) {
        when (tab) {
            0 -> AboutSettingsTab(
                debugEnabled = debugEnabled,
                onDebugEnabled = onDebugEnabled,
            )
            1 -> AudioSettingsTab(
                engineKey = tts.engineKey,
                voiceId = tts.voiceId,
                engines = tts.engines,
                voices = tts.voices,
                speed = tts.speed,
                pitch = tts.pitch,
                prefetchCount = tts.prefetchCount,
                clipTargetChars = tts.clipTargetChars,
                clipFlexChars = tts.clipFlexChars,
                doubleTapPlay = tts.doubleTapPlay,
                mobileDataFallback = tts.mobileDataFallback,
                autoPlayOnShare = tts.autoPlayOnShare,
                shareInterruptsPlayback = tts.shareInterruptsPlayback,
                autoScrollWithTts = tts.autoScrollWithTts,
                minSignal = tts.minSignal,
                underlayBtAddress = tts.underlayBtAddress,
                underlayBtName = tts.underlayBtName,
                underlayBtConnected = tts.underlayBtConnected,
                sentenceGapMs = tts.sentenceGapMs,
                highlightSyncMs = tts.highlightSyncMs,
                onEngine = ttsCallbacks.onEngine,
                onVoice = ttsCallbacks.onVoice,
                onSpeed = ttsCallbacks.onSpeed,
                onPitch = ttsCallbacks.onPitch,
                onPrefetchCount = ttsCallbacks.onPrefetchCount,
                onClipTargetChars = ttsCallbacks.onClipTargetChars,
                onClipFlexChars = ttsCallbacks.onClipFlexChars,
                onDoubleTapPlay = ttsCallbacks.onDoubleTapPlay,
                onMobileDataFallback = ttsCallbacks.onMobileDataFallback,
                onAutoPlayOnShare = ttsCallbacks.onAutoPlayOnShare,
                onShareInterruptsPlayback = ttsCallbacks.onShareInterruptsPlayback,
                onAutoScrollWithTts = ttsCallbacks.onAutoScrollWithTts,
                onMinSignal = ttsCallbacks.onMinSignal,
                onUnderlayBtDevice = ttsCallbacks.onUnderlayBtDevice,
                underlayBondedDevices = ttsCallbacks.underlayBondedDevices,
                onSentenceGapMs = ttsCallbacks.onSentenceGapMs,
                onHighlightSyncMs = ttsCallbacks.onHighlightSyncMs,
            )
            2 -> FiltersSettingsTab(
                filtersGlobal = filters.filtersGlobal,
                filtersGroups = filters.filtersGroups,
                filtersLocal = filters.filtersLocal,
                scopes = filters.filterScopes,
                onAdd = filterCallbacks.onAddFilter,
                onEdit = filterCallbacks.onEditFilter,
                onSetEnabled = filterCallbacks.onSetFilterEnabled,
                onReorder = filterCallbacks.onReorderFilters,
                onDelete = filterCallbacks.onDeleteFilters,
            )
            3 -> SharingSettingsHost(ruleEditor)
            else -> LayoutSettingsTab(
                themeMode = appearance.themeMode,
                accentHue = appearance.accentHue,
                accentSaturation = appearance.accentSaturation,
                uiScale = appearance.uiScale,
                fontScale = appearance.fontScale,
                fontFamily = appearance.fontFamily,
                lineSpacing = appearance.lineSpacing,
                justifyText = appearance.justifyText,
                orientation = appearance.orientation,
                showChapterHeadingsInBody = appearance.showChapterHeadingsInBody,
                keepScreenAwake = appearance.keepScreenAwake,
                homePosition = appearance.homePosition,
                showHomeMarker = appearance.showHomeMarker,
                onTheme = appearanceCallbacks.onTheme,
                onAccentHue = appearanceCallbacks.onAccentHue,
                onAccentSaturation = appearanceCallbacks.onAccentSaturation,
                onUiScale = appearanceCallbacks.onUiScale,
                onFontScale = appearanceCallbacks.onFontScale,
                onFontFamily = appearanceCallbacks.onFontFamily,
                onLineSpacing = appearanceCallbacks.onLineSpacing,
                onJustifyText = appearanceCallbacks.onJustifyText,
                onOrientation = appearanceCallbacks.onOrientation,
                onShowChapterHeadingsInBody = appearanceCallbacks.onShowChapterHeadingsInBody,
                onKeepScreenAwake = appearanceCallbacks.onKeepScreenAwake,
                onHomePosition = appearanceCallbacks.onHomePosition,
                onShowHomeMarker = appearanceCallbacks.onShowHomeMarker,
            )
        }
    }

    if (visible) {
        DomainRuleEditorHost(ruleEditor)
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
    var routerRules by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(emptyList<com.personal.flowreader.share.RouterRule>())
    }
    var parseRules by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(emptyList<com.personal.flowreader.share.ParseRule>())
    }
    var customTabs by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(emptyList<com.personal.flowreader.data.CustomLibraryTab>())
    }
    var overlayOk by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(
            com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context),
        )
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        prefs = withContext(Dispatchers.IO) { app.settings.shareOnce() }
        routerRules = withContext(Dispatchers.IO) { app.settings.shareRouterRulesOnce() }
        parseRules = withContext(Dispatchers.IO) { app.settings.shareParseRulesOnce() }
        customTabs = withContext(Dispatchers.IO) { app.settings.customLibraryTabsOnce() }
        overlayOk = com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
    }
    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            overlayOk = com.personal.flowreader.share.ShareOverlayPermission.canDrawOverlays(context)
            customTabs = withContext(Dispatchers.IO) { app.settings.customLibraryTabsOnce() }
        }
    }
    com.personal.flowreader.ui.settings.SharingSettingsTab(
        prefs = prefs,
        routerRules = routerRules,
        parseRules = parseRules,
        overlayAllowed = overlayOk,
        plugins = app.pluginManager.installed.collectAsState().value.map {
            com.personal.flowreader.ui.settings.SharePluginOption(it.id, it.name)
        },
        customTabs = customTabs.map {
            com.personal.flowreader.ui.settings.SharePluginOption(it.id, it.title)
        },
        editorState = ruleEditor,
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
        onSaveRouterRules = { next ->
            routerRules = next
            app.appScope.launch { app.settings.setShareRouterRules(next) }
        },
        onSaveParseRules = { next ->
            parseRules = com.personal.flowreader.share.ParseRules.withDefault(next)
            app.appScope.launch { app.settings.setShareParseRules(next) }
        },
    )
}