package com.personal.flowreader.ui.settings

import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.ui.open.OpenBookViewModel
import com.personal.flowreader.ui.open.OpenUi

/** Appearance prefs shown in Settings Layout tab (library + reader). */
data class AppearanceSettingsState(
    val themeMode: ThemeMode,
    val accentHue: Float,
    val uiScale: Float,
    val fontScale: Float,
    val fontFamily: ReaderFont,
    val lineSpacing: Float,
    val justifyText: Boolean,
    val orientation: ReaderOrientation,
    val showChapterHeadingsInBody: Boolean,
    val keepScreenAwake: Boolean,
)

data class AppearanceSettingsCallbacks(
    val onTheme: (ThemeMode) -> Unit,
    val onAccentHue: (Float) -> Unit,
    val onUiScale: (Float) -> Unit,
    val onFontScale: (Float) -> Unit,
    val onFontFamily: (ReaderFont) -> Unit,
    val onLineSpacing: (Float) -> Unit,
    val onJustifyText: (Boolean) -> Unit,
    val onOrientation: (ReaderOrientation) -> Unit,
    val onShowChapterHeadingsInBody: (Boolean) -> Unit,
    val onKeepScreenAwake: (Boolean) -> Unit,
)

fun AppearanceSettingsState(openUi: OpenUi) = AppearanceSettingsState(
    themeMode = openUi.theme,
    accentHue = openUi.accentHue,
    uiScale = openUi.uiScale,
    fontScale = openUi.fontScale,
    fontFamily = openUi.fontFamily,
    lineSpacing = openUi.lineSpacing,
    justifyText = openUi.justifyText,
    orientation = openUi.orientation,
    showChapterHeadingsInBody = openUi.showChapterHeadingsInBody,
    keepScreenAwake = openUi.keepScreenAwake,
)

fun AppearanceSettingsCallbacks(openVm: OpenBookViewModel) = AppearanceSettingsCallbacks(
    onTheme = openVm::setTheme,
    onAccentHue = openVm::setAccentHue,
    onUiScale = openVm::setUiScale,
    onFontScale = openVm::setFontScale,
    onFontFamily = openVm::setFontFamily,
    onLineSpacing = openVm::setLineSpacing,
    onJustifyText = openVm::setJustifyText,
    onOrientation = openVm::setOrientation,
    onShowChapterHeadingsInBody = openVm::setShowChapterHeadingsInBody,
    onKeepScreenAwake = openVm::setKeepScreenAwake,
)

/** TTS prefs + engines for the Audio settings tab. */
data class TtsSettingsState(
    val engineKey: String,
    val voiceId: String,
    val engines: List<TtsEngineOption>,
    val voices: List<TtsVoiceOption>,
    val speed: Float,
    val pitch: Float,
    val prefetchCount: Int,
    val doubleTapPlay: Boolean,
    val autoScrollWithTts: Boolean,
    val minSignal: Float,
    val sentenceGapMs: Int,
    val highlightSyncMs: Int,
)

data class TtsSettingsCallbacks(
    val onEngine: (String) -> Unit,
    val onVoice: (String) -> Unit,
    val onSpeed: (Float) -> Unit,
    val onPitch: (Float) -> Unit,
    val onPrefetchCount: (Int) -> Unit,
    val onDoubleTapPlay: (Boolean) -> Unit,
    val onAutoScrollWithTts: (Boolean) -> Unit,
    val onMinSignal: (Float, Boolean) -> Unit,
    val onSentenceGapMs: (Int) -> Unit,
    val onHighlightSyncMs: (Int) -> Unit,
)

data class FilterSettingsState(
    val filtersGlobal: List<FilterRule>,
    val filtersGroups: List<FilterRule>,
    val filtersLocal: List<FilterRule>,
    val filterScopes: List<FilterScope>,
)

data class FilterSettingsCallbacks(
    val onAddFilter: (FilterScope) -> Unit,
    val onEditFilter: (FilterScope, FilterRule) -> Unit,
    val onSetFilterEnabled: (FilterScope, String, Boolean) -> Unit,
)
