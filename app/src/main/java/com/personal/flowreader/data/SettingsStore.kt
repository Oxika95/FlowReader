package com.personal.flowreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.RouterRule
import com.personal.flowreader.share.RouterRules
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.SharePrefs
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_settings")

data class ReaderPrefs(
    val theme: ThemeMode = ThemeMode.Oled,
    val accentHue: Float = AccentHue.DEFAULT,
    val accentSaturation: Float = AccentSaturation.DEFAULT,
    val accentLightness: Float = AccentLightness.DEFAULT,
    val uiScale: Float = UiScale.DEFAULT,
    val fontScale: Float = 1f,
    val fontFamily: ReaderFont = ReaderFont.Sans,
    val lineSpacing: Float = 1f,
    /** When true, body text is justified edge-to-edge. */
    val justifyText: Boolean = false,
    val orientation: ReaderOrientation = ReaderOrientation.Auto,
    /** When true, each chapter title appears as a heading in the reading body. */
    val showChapterHeadingsInBody: Boolean = false,
    /** When true, the reader keeps the display on while open. */
    val keepScreenAwake: Boolean = false,
    /** When true, synth debug logging and the floating dump FAB are available. */
    val debugEnabled: Boolean = false,
    /** See [HomePosition]. */
    val homePosition: Float = HomePosition.DEFAULT,
    /** When true, the reader shows a draggable marker at [homePosition]. */
    val showHomeMarker: Boolean = false,
)

data class TtsPrefs(
    val engineKey: String = TtsEngines.EDGE,
    val voiceId: String = DEFAULT_EDGE_VOICE,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    /** How many upcoming sentences to Edge-cache ahead of the playhead (1–10). */
    val prefetchCount: Int = DEFAULT_PREFETCH,
    /** When true, double-tapping reader text seeks and starts TTS. */
    val doubleTapPlay: Boolean = true,
    /** When true, Edge TTS may send over mobile data while Wi-Fi is weak or failing. */
    val mobileDataFallback: Boolean = true,
    /** When true, a share opens in the reader and starts TTS if nothing is playing. */
    val autoPlayOnShare: Boolean = false,
    /** With [autoPlayOnShare], a share also replaces what is playing. */
    val shareInterruptsPlayback: Boolean = false,
    /** When true, the list keeps the spoken block centered until the user scrolls away. */
    val autoScrollWithTts: Boolean = true,
    /**
     * Tonal underlay while playing. `0` = off; otherwise dB below full-scale PCM
     * (negative). Snaps to [TONAL_UNDERLAY_STEPS]. Keeps a faint signal on the
     * output so some car head units stay awake.
     */
    val minSignal: Float = DEFAULT_MIN_SIGNAL,
    /**
     * When non-empty, underlay stays armed at [minSignal] but only sounds while
     * this paired Bluetooth device is connected (otherwise standby).
     */
    val underlayBtAddress: String = "",
    /** Display name cache for [underlayBtAddress]. */
    val underlayBtName: String = "",
    /** Extra pause (+) or crossfade (−) after each spoken sentence (−500…500 ms). */
    val sentenceGapMs: Int = DEFAULT_SENTENCE_GAP_MS,
    /** Offset applied to Edge heard-clock word cues (ms). */
    val highlightSyncMs: Int = DEFAULT_HIGHLIGHT_SYNC_MS,
    /** Ideal characters per TTS clip (join/split target). */
    val clipTargetChars: Int = DEFAULT_CLIP_TARGET_CHARS,
    /** Characters allowed above/below [clipTargetChars]. */
    val clipFlexChars: Int = DEFAULT_CLIP_FLEX_CHARS,
) {
    companion object {
        const val DEFAULT_EDGE_VOICE = "en-US-AndrewNeural"
        const val DEFAULT_PREFETCH = 5
        const val MIN_PREFETCH = 1
        const val MAX_PREFETCH = 10
        const val DEFAULT_SENTENCE_GAP_MS = 0
        const val MIN_SENTENCE_GAP_MS = -500
        const val MAX_SENTENCE_GAP_MS = 500
        const val SENTENCE_GAP_STEP_MS = 50
        const val DEFAULT_HIGHLIGHT_SYNC_MS = 0
        const val MIN_HIGHLIGHT_SYNC_MS = -500
        const val MAX_HIGHLIGHT_SYNC_MS = 500
        const val HIGHLIGHT_SYNC_STEP_MS = 50
        const val DEFAULT_CLIP_TARGET_CHARS = SentenceLengthNormalizer.DEFAULT_TARGET_CHARS
        const val MIN_CLIP_TARGET_CHARS = SentenceLengthNormalizer.MIN_TARGET_CHARS
        const val MAX_CLIP_TARGET_CHARS = SentenceLengthNormalizer.MAX_TARGET_CHARS
        const val CLIP_TARGET_STEP_CHARS = 25
        const val DEFAULT_CLIP_FLEX_CHARS = SentenceLengthNormalizer.DEFAULT_FLEX_CHARS
        const val MIN_CLIP_FLEX_CHARS = SentenceLengthNormalizer.MIN_FLEX_CHARS
        const val MAX_CLIP_FLEX_CHARS = SentenceLengthNormalizer.MAX_FLEX_CHARS
        const val CLIP_FLEX_STEP_CHARS = 5
        /** Off. Non-zero stored values are negative dB vs full-scale PCM. */
        const val DEFAULT_MIN_SIGNAL = 0f
        const val MIN_SIGNAL = 0f
        /**
         * Off + −100…−20 dB (10 dB steps). Former 0…10 linear scale bottomed out
         * around −40 dB FS and was still clearly audible.
         */
        val TONAL_UNDERLAY_STEPS = floatArrayOf(
            0f, -100f, -90f, -80f, -70f, -60f, -50f, -40f, -30f, -20f,
        )
        val TONAL_UNDERLAY_LABELS = arrayOf(
            "Off", "-100 dB", "-90 dB", "-80 dB", "-70 dB",
            "-60 dB", "-50 dB", "-40 dB", "-30 dB", "-20 dB",
        )

        fun isUnderlayEnabled(level: Float): Boolean = level < 0f

        /**
         * Level fed to the keep-alive oscillator. Prefs keep [minSignal] armed;
         * when a BT address is set and that device is disconnected, returns 0
         * (standby) without clearing the stored Level.
         */
        fun effectiveMinSignal(
            minSignal: Float,
            underlayBtAddress: String,
            targetConnected: Boolean,
        ): Float = when {
            !isUnderlayEnabled(minSignal) -> DEFAULT_MIN_SIGNAL
            underlayBtAddress.isBlank() -> coerceMinSignal(minSignal)
            targetConnected -> coerceMinSignal(minSignal)
            else -> DEFAULT_MIN_SIGNAL
        }

        /** Linear PCM amplitude for [level] (`0` → silence, `-20` → ~0.1). */
        fun underlayLinearGain(level: Float): Float {
            if (!isUnderlayEnabled(level)) return 0f
            return 10f.pow(coerceMinSignal(level) / 20f).coerceIn(0f, 1f)
        }

        fun tonalUnderlayIndex(level: Float): Int {
            var best = 0
            var bestDist = Float.MAX_VALUE
            for (i in TONAL_UNDERLAY_STEPS.indices) {
                val dist = abs(TONAL_UNDERLAY_STEPS[i] - level)
                if (dist < bestDist) {
                    best = i
                    bestDist = dist
                }
            }
            return best
        }

        fun coerceMinSignal(level: Float): Float =
            TONAL_UNDERLAY_STEPS[tonalUnderlayIndex(level)]

        fun coerceSentenceGapMs(ms: Int): Int {
            val clamped = ms.coerceIn(MIN_SENTENCE_GAP_MS, MAX_SENTENCE_GAP_MS)
            val stepped = ((clamped.toFloat() / SENTENCE_GAP_STEP_MS).roundToInt()
                * SENTENCE_GAP_STEP_MS)
            return stepped.coerceIn(MIN_SENTENCE_GAP_MS, MAX_SENTENCE_GAP_MS)
        }

        fun coerceHighlightSyncMs(ms: Int): Int {
            val clamped = ms.coerceIn(MIN_HIGHLIGHT_SYNC_MS, MAX_HIGHLIGHT_SYNC_MS)
            val stepped = ((clamped.toFloat() / HIGHLIGHT_SYNC_STEP_MS).roundToInt()
                * HIGHLIGHT_SYNC_STEP_MS)
            return stepped.coerceIn(MIN_HIGHLIGHT_SYNC_MS, MAX_HIGHLIGHT_SYNC_MS)
        }

        fun coerceClipTargetChars(chars: Int): Int {
            val clamped = chars.coerceIn(MIN_CLIP_TARGET_CHARS, MAX_CLIP_TARGET_CHARS)
            val stepped = ((clamped.toFloat() / CLIP_TARGET_STEP_CHARS).roundToInt()
                * CLIP_TARGET_STEP_CHARS)
            return stepped.coerceIn(MIN_CLIP_TARGET_CHARS, MAX_CLIP_TARGET_CHARS)
        }

        fun coerceClipFlexChars(chars: Int): Int {
            val clamped = chars.coerceIn(MIN_CLIP_FLEX_CHARS, MAX_CLIP_FLEX_CHARS)
            val stepped = ((clamped.toFloat() / CLIP_FLEX_STEP_CHARS).roundToInt()
                * CLIP_FLEX_STEP_CHARS)
            return stepped.coerceIn(MIN_CLIP_FLEX_CHARS, MAX_CLIP_FLEX_CHARS)
        }
    }
}

data class PluginCacheDefaults(
    val cacheLevel: Int = DEFAULT_LEVEL,
    val cleanup: Boolean = false,
) {
    companion object {
        const val DEFAULT_LEVEL = 1
    }
}

/** Background new-chapter check for plugin stories with the bell on. */
data class PluginUpdatePrefs(
    /** Hours between checks; 0 = off. */
    val intervalHours: Int = DEFAULT_INTERVAL_HOURS,
    val wifiOnly: Boolean = false,
) {
    companion object {
        const val DEFAULT_INTERVAL_HOURS = 12
        val INTERVAL_CHOICES = listOf(0, 3, 6, 12, 24)
    }
}

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    suspend fun readerOnce(): ReaderPrefs = store.data.first().toReaderPrefs()
    suspend fun ttsOnce(): TtsPrefs = store.data.first().toTtsPrefs()

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[KEY_THEME] = mode.name }
    }

    suspend fun setAccentHue(hue: Float) {
        store.edit {
            it[KEY_ACCENT_HUE] = hue.coerceIn(AccentHue.MIN, AccentHue.MAX)
        }
    }

    suspend fun setAccentSaturation(value: Float) {
        store.edit { it[KEY_ACCENT_SATURATION] = AccentSaturation.coerce(value) }
    }

    suspend fun setAccentLightness(value: Float) {
        store.edit { it[KEY_ACCENT_LIGHTNESS] = AccentLightness.coerce(value) }
    }

    suspend fun setUiScale(scale: Float) {
        store.edit { it[KEY_UI_SCALE] = UiScale.coerce(scale) }
    }

    suspend fun setFontScale(scale: Float) {
        store.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun setFontFamily(font: ReaderFont) {
        store.edit { it[KEY_FONT_FAMILY] = font.name }
    }

    suspend fun setLineSpacing(spacing: Float) {
        store.edit { it[KEY_LINE_SPACING] = spacing }
    }

    suspend fun setJustifyText(enabled: Boolean) {
        store.edit { it[KEY_JUSTIFY_TEXT] = enabled }
    }

    suspend fun setOrientation(orientation: ReaderOrientation) {
        store.edit { it[KEY_ORIENTATION] = orientation.name }
    }

    suspend fun setShowChapterHeadingsInBody(enabled: Boolean) {
        store.edit { it[KEY_SHOW_CHAPTER_HEADINGS] = enabled }
    }

    suspend fun setKeepScreenAwake(enabled: Boolean) {
        store.edit { it[KEY_KEEP_SCREEN_AWAKE] = enabled }
    }

    suspend fun setDebugEnabled(enabled: Boolean) {
        store.edit { it[KEY_DEBUG_ENABLED] = enabled }
    }

    suspend fun setHomePosition(position: Float) {
        store.edit { it[KEY_HOME_POSITION] = HomePosition.coerce(position) }
    }

    suspend fun setShowHomeMarker(enabled: Boolean) {
        store.edit { it[KEY_SHOW_HOME_MARKER] = enabled }
    }

    suspend fun setEngine(key: String) {
        store.edit { it[KEY_TTS_ENGINE] = key }
    }

    suspend fun setVoice(voiceId: String) {
        store.edit { it[KEY_TTS_VOICE] = voiceId }
    }

    suspend fun setSpeed(speed: Float) {
        store.edit { it[KEY_TTS_SPEED] = speed }
    }

    suspend fun setPitch(pitch: Float) {
        store.edit { it[KEY_TTS_PITCH] = pitch }
    }

    suspend fun setPrefetchCount(count: Int) {
        store.edit {
            it[KEY_TTS_PREFETCH] = count.coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH)
        }
    }

    suspend fun setDoubleTapPlay(enabled: Boolean) {
        store.edit { it[KEY_TTS_DOUBLE_TAP_PLAY] = enabled }
    }

    suspend fun setAutoPlayOnShare(enabled: Boolean) {
        store.edit { it[KEY_TTS_AUTO_PLAY_ON_SHARE] = enabled }
    }

    suspend fun setShareInterruptsPlayback(enabled: Boolean) {
        store.edit { it[KEY_TTS_SHARE_INTERRUPTS] = enabled }
    }

    suspend fun setMobileDataFallback(enabled: Boolean) {
        store.edit { it[KEY_TTS_MOBILE_DATA_FALLBACK] = enabled }
    }

    suspend fun setAutoScrollWithTts(enabled: Boolean) {
        store.edit { it[KEY_TTS_AUTO_SCROLL] = enabled }
    }

    suspend fun setMinSignal(level: Float) {
        store.edit { it[KEY_TTS_TONAL_UNDERLAY] = TtsPrefs.coerceMinSignal(level) }
    }

    suspend fun setUnderlayBtDevice(address: String, name: String) {
        store.edit {
            it[KEY_TTS_UNDERLAY_BT_ADDRESS] = address.trim()
            it[KEY_TTS_UNDERLAY_BT_NAME] = name.trim()
        }
    }

    suspend fun setSentenceGapMs(ms: Int) {
        store.edit { it[KEY_TTS_SENTENCE_GAP_MS] = TtsPrefs.coerceSentenceGapMs(ms) }
    }

    suspend fun setHighlightSyncMs(ms: Int) {
        store.edit { it[KEY_TTS_HIGHLIGHT_SYNC_MS] = TtsPrefs.coerceHighlightSyncMs(ms) }
    }

    suspend fun setClipTargetChars(chars: Int) {
        store.edit { it[KEY_TTS_CLIP_TARGET_CHARS] = TtsPrefs.coerceClipTargetChars(chars) }
    }

    suspend fun setClipFlexChars(chars: Int) {
        store.edit { it[KEY_TTS_CLIP_FLEX_CHARS] = TtsPrefs.coerceClipFlexChars(chars) }
    }

    suspend fun globalFiltersOnce(): List<FilterRule> =
        TextFilters.decodeRules(store.data.first()[KEY_GLOBAL_FILTERS])

    suspend fun setGlobalFilters(rules: List<FilterRule>) {
        store.edit { it[KEY_GLOBAL_FILTERS] = TextFilters.encodeRules(rules) }
    }

    suspend fun groupFiltersOnce(): List<FilterRule> =
        TextFilters.decodeRules(store.data.first()[KEY_GROUP_FILTERS])

    suspend fun setGroupFilters(rules: List<FilterRule>) {
        store.edit { it[KEY_GROUP_FILTERS] = TextFilters.encodeRules(rules) }
    }

    suspend fun libraryViewModeOnce(): LibraryViewMode =
        runCatching {
            LibraryViewMode.valueOf(
                store.data.first()[KEY_LIBRARY_VIEW] ?: LibraryViewMode.List.name,
            )
        }.getOrDefault(LibraryViewMode.List)

    suspend fun setLibraryViewMode(mode: LibraryViewMode) {
        store.edit { it[KEY_LIBRARY_VIEW] = mode.name }
    }

    suspend fun libraryTabIdOnce(): String =
        store.data.first()[KEY_LIBRARY_TAB] ?: LibraryTabId.ID_FILES

    suspend fun setLibraryTabId(id: String) {
        store.edit { it[KEY_LIBRARY_TAB] = id }
    }

    suspend fun enabledPluginIdsOnce(): Set<String> =
        decodeIdSet(store.data.first()[KEY_ENABLED_PLUGINS])

    suspend fun setEnabledPluginIds(ids: Set<String>) {
        store.edit { it[KEY_ENABLED_PLUGINS] = ids.sorted().joinToString(",") }
    }

    suspend fun customLibraryTabsOnce(): List<CustomLibraryTab> {
        val raw = store.data.first()[KEY_CUSTOM_TABS]
        return decodeCustomTabs(raw)
    }

    suspend fun setCustomLibraryTabs(tabs: List<CustomLibraryTab>) {
        store.edit { it[KEY_CUSTOM_TABS] = encodeCustomTabs(tabs) }
    }

    suspend fun notificationsAskedOnce(): Boolean =
        store.data.first()[KEY_NOTIFICATIONS_ASKED] ?: false

    suspend fun setNotificationsAskedOnce(asked: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ASKED] = asked }
    }

    suspend fun shareOnce(): SharePrefs {
        val p = store.data.first()
        return SharePrefs(
            askMode = runCatching {
                ShareAskMode.valueOf(p[KEY_SHARE_ASK_MODE] ?: ShareAskMode.Auto.name)
            }.getOrDefault(ShareAskMode.Auto),
        )
    }

    suspend fun setShareAskMode(mode: ShareAskMode) {
        store.edit { it[KEY_SHARE_ASK_MODE] = mode.name }
    }

    /** Router rules (content kind → match/parse → destination); [RouterRules.seed] when unset. */
    suspend fun shareRouterRulesOnce(): List<RouterRule> {
        val decoded = RouterRules.decode(store.data.first()[KEY_SHARE_ROUTER_RULES])
        return decoded.ifEmpty { RouterRules.seed() }
    }

    suspend fun setShareRouterRules(rules: List<RouterRule>) {
        store.edit { it[KEY_SHARE_ROUTER_RULES] = RouterRules.encode(rules) }
    }

    /** Router rule ids already seeded from plugin `shareHosts` (so user deletions stick). */
    suspend fun seededPluginShareRuleIdsOnce(): Set<String> =
        decodeIdSet(store.data.first()[KEY_SEEDED_PLUGIN_SHARE_RULES])

    suspend fun setSeededPluginShareRuleIds(ids: Set<String>) {
        store.edit { it[KEY_SEEDED_PLUGIN_SHARE_RULES] = ids.sorted().joinToString(",") }
    }

    /** Plugin repository index URLs; null until the user first edits the list. */
    suspend fun pluginReposOnce(): List<String>? {
        val raw = store.data.first()[KEY_PLUGIN_REPOS] ?: return null
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    }

    suspend fun setPluginRepos(urls: List<String>) {
        store.edit { it[KEY_PLUGIN_REPOS] = urls.joinToString("\n") }
    }

    /** Cache level and cleanup that new plugin stories start with. */
    suspend fun pluginCacheDefaultsOnce(): PluginCacheDefaults {
        val p = store.data.first()
        return PluginCacheDefaults(
            cacheLevel = (p[KEY_PLUGIN_CACHE_LEVEL] ?: PluginCacheDefaults.DEFAULT_LEVEL).coerceAtLeast(0),
            cleanup = p[KEY_PLUGIN_CACHE_CLEANUP] ?: false,
        )
    }

    suspend fun setPluginCacheLevel(level: Int) {
        store.edit { it[KEY_PLUGIN_CACHE_LEVEL] = level.coerceAtLeast(0) }
    }

    suspend fun setPluginCacheCleanup(on: Boolean) {
        store.edit { it[KEY_PLUGIN_CACHE_CLEANUP] = on }
    }

    suspend fun pluginUpdatePrefsOnce(): PluginUpdatePrefs {
        val p = store.data.first()
        return PluginUpdatePrefs(
            intervalHours = (p[KEY_PLUGIN_UPDATE_INTERVAL] ?: PluginUpdatePrefs.DEFAULT_INTERVAL_HOURS).coerceAtLeast(0),
            wifiOnly = p[KEY_PLUGIN_UPDATE_WIFI_ONLY] ?: false,
        )
    }

    suspend fun setPluginUpdateInterval(hours: Int) {
        store.edit { it[KEY_PLUGIN_UPDATE_INTERVAL] = hours.coerceAtLeast(0) }
    }

    suspend fun setPluginUpdateWifiOnly(on: Boolean) {
        store.edit { it[KEY_PLUGIN_UPDATE_WIFI_ONLY] = on }
    }

    /** Parse rules, always ending with the protected Default rule ([ParseRules.withDefault]). */
    suspend fun shareParseRulesOnce(): List<ParseRule> =
        ParseRules.withDefault(ParseRules.decode(store.data.first()[KEY_SHARE_PARSE_RULES]))

    suspend fun setShareParseRules(rules: List<ParseRule>) {
        store.edit { it[KEY_SHARE_PARSE_RULES] = ParseRules.encode(ParseRules.withDefault(rules)) }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_ACCENT_HUE = floatPreferencesKey("accent_hue")
        private val KEY_ACCENT_SATURATION = floatPreferencesKey("accent_saturation")
        private val KEY_ACCENT_LIGHTNESS = floatPreferencesKey("accent_lightness")
        private val KEY_UI_SCALE = floatPreferencesKey("ui_scale")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_JUSTIFY_TEXT = booleanPreferencesKey("justify_text")
        private val KEY_ORIENTATION = stringPreferencesKey("orientation")
        private val KEY_SHOW_CHAPTER_HEADINGS = booleanPreferencesKey("show_chapter_headings")
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        private val KEY_HOME_POSITION = floatPreferencesKey("home_position")
        private val KEY_SHOW_HOME_MARKER = booleanPreferencesKey("show_home_marker")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_PREFETCH = intPreferencesKey("tts_prefetch")
        private val KEY_TTS_DOUBLE_TAP_PLAY = booleanPreferencesKey("tts_double_tap_play")
        private val KEY_TTS_MOBILE_DATA_FALLBACK = booleanPreferencesKey("tts_mobile_data_fallback")
        private val KEY_TTS_AUTO_PLAY_ON_SHARE = booleanPreferencesKey("tts_auto_play_on_share")
        private val KEY_TTS_SHARE_INTERRUPTS = booleanPreferencesKey("tts_share_interrupts")
        private val KEY_TTS_AUTO_SCROLL = booleanPreferencesKey("tts_auto_scroll")
        private val KEY_TTS_TONAL_UNDERLAY = floatPreferencesKey("tts_tonal_underlay")
        private val KEY_TTS_UNDERLAY_BT_ADDRESS = stringPreferencesKey("tts_underlay_bt_address")
        private val KEY_TTS_UNDERLAY_BT_NAME = stringPreferencesKey("tts_underlay_bt_name")
        private val KEY_TTS_SENTENCE_GAP_MS = intPreferencesKey("tts_sentence_gap_ms")
        private val KEY_TTS_HIGHLIGHT_SYNC_MS = intPreferencesKey("tts_highlight_sync_ms")
        private val KEY_TTS_CLIP_TARGET_CHARS = intPreferencesKey("tts_clip_target_chars")
        private val KEY_TTS_CLIP_FLEX_CHARS = intPreferencesKey("tts_clip_flex_chars")
        private val KEY_GLOBAL_FILTERS = stringPreferencesKey("global_filters")
        private val KEY_GROUP_FILTERS = stringPreferencesKey("group_filters")
        private val KEY_LIBRARY_VIEW = stringPreferencesKey("library_view")
        private val KEY_LIBRARY_TAB = stringPreferencesKey("library_tab")
        private val KEY_ENABLED_PLUGINS = stringPreferencesKey("enabled_plugins")
        private val KEY_CUSTOM_TABS = stringPreferencesKey("custom_library_tabs")
        private val KEY_NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        private val KEY_SHARE_ASK_MODE = stringPreferencesKey("share_ask_mode")
        private val KEY_SHARE_ROUTER_RULES = stringPreferencesKey("share_router_rules")
        private val KEY_SHARE_PARSE_RULES = stringPreferencesKey("share_parse_rules")
        private val KEY_SEEDED_PLUGIN_SHARE_RULES = stringPreferencesKey("seeded_plugin_share_rules")
        private val KEY_PLUGIN_REPOS = stringPreferencesKey("plugin_repos")
        private val KEY_PLUGIN_CACHE_LEVEL = intPreferencesKey("plugin_cache_level")
        private val KEY_PLUGIN_CACHE_CLEANUP = booleanPreferencesKey("plugin_cache_cleanup")
        private val KEY_PLUGIN_UPDATE_INTERVAL = intPreferencesKey("plugin_update_interval")
        private val KEY_PLUGIN_UPDATE_WIFI_ONLY = booleanPreferencesKey("plugin_update_wifi_only")

        private fun decodeIdSet(raw: String?): Set<String> =
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

        private fun Preferences.toReaderPrefs() = ReaderPrefs(
            theme = runCatching { ThemeMode.valueOf(this[KEY_THEME] ?: ThemeMode.Oled.name) }
                .getOrDefault(ThemeMode.Oled),
            accentHue = (this[KEY_ACCENT_HUE] ?: AccentHue.DEFAULT).coerceIn(AccentHue.MIN, AccentHue.MAX),
            accentSaturation = AccentSaturation.coerce(
                this[KEY_ACCENT_SATURATION] ?: AccentSaturation.DEFAULT,
            ),
            accentLightness = AccentLightness.coerce(
                this[KEY_ACCENT_LIGHTNESS] ?: AccentLightness.DEFAULT,
            ),
            uiScale = UiScale.coerce(this[KEY_UI_SCALE] ?: UiScale.DEFAULT),
            fontScale = this[KEY_FONT_SCALE] ?: 1f,
            fontFamily = runCatching {
                ReaderFont.valueOf(this[KEY_FONT_FAMILY] ?: ReaderFont.Sans.name)
            }.getOrDefault(ReaderFont.Sans),
            lineSpacing = this[KEY_LINE_SPACING] ?: 1f,
            justifyText = this[KEY_JUSTIFY_TEXT] ?: false,
            orientation = runCatching {
                ReaderOrientation.valueOf(this[KEY_ORIENTATION] ?: ReaderOrientation.Auto.name)
            }.getOrDefault(ReaderOrientation.Auto),
            showChapterHeadingsInBody = this[KEY_SHOW_CHAPTER_HEADINGS] ?: false,
            keepScreenAwake = this[KEY_KEEP_SCREEN_AWAKE] ?: false,
            debugEnabled = this[KEY_DEBUG_ENABLED] ?: false,
            homePosition = HomePosition.coerce(this[KEY_HOME_POSITION] ?: HomePosition.DEFAULT),
            showHomeMarker = this[KEY_SHOW_HOME_MARKER] ?: false,
        )

        private fun Preferences.toTtsPrefs() = TtsPrefs(
            engineKey = this[KEY_TTS_ENGINE] ?: TtsEngines.EDGE,
            voiceId = this[KEY_TTS_VOICE] ?: TtsPrefs.DEFAULT_EDGE_VOICE,
            speed = this[KEY_TTS_SPEED] ?: 1f,
            pitch = this[KEY_TTS_PITCH] ?: 1f,
            prefetchCount = (this[KEY_TTS_PREFETCH] ?: TtsPrefs.DEFAULT_PREFETCH)
                .coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH),
            doubleTapPlay = this[KEY_TTS_DOUBLE_TAP_PLAY] ?: true,
            mobileDataFallback = this[KEY_TTS_MOBILE_DATA_FALLBACK] ?: true,
            autoPlayOnShare = this[KEY_TTS_AUTO_PLAY_ON_SHARE] ?: false,
            shareInterruptsPlayback = this[KEY_TTS_SHARE_INTERRUPTS] ?: false,
            autoScrollWithTts = this[KEY_TTS_AUTO_SCROLL] ?: true,
            minSignal = TtsPrefs.coerceMinSignal(this[KEY_TTS_TONAL_UNDERLAY] ?: TtsPrefs.DEFAULT_MIN_SIGNAL),
            underlayBtAddress = this[KEY_TTS_UNDERLAY_BT_ADDRESS].orEmpty(),
            underlayBtName = this[KEY_TTS_UNDERLAY_BT_NAME].orEmpty(),
            sentenceGapMs = TtsPrefs.coerceSentenceGapMs(
                this[KEY_TTS_SENTENCE_GAP_MS] ?: TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
            ),
            highlightSyncMs = TtsPrefs.coerceHighlightSyncMs(
                this[KEY_TTS_HIGHLIGHT_SYNC_MS] ?: TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
            ),
            clipTargetChars = TtsPrefs.coerceClipTargetChars(
                this[KEY_TTS_CLIP_TARGET_CHARS] ?: TtsPrefs.DEFAULT_CLIP_TARGET_CHARS,
            ),
            clipFlexChars = TtsPrefs.coerceClipFlexChars(
                this[KEY_TTS_CLIP_FLEX_CHARS] ?: TtsPrefs.DEFAULT_CLIP_FLEX_CHARS,
            ),
        )

        private fun encodeCustomTabs(tabs: List<CustomLibraryTab>): String {
            val arr = JSONArray()
            tabs.sortedBy { it.order }.forEach { tab ->
                arr.put(
                    JSONObject()
                        .put("id", tab.id)
                        .put("title", tab.title)
                        .put("order", tab.order),
                )
            }
            return arr.toString()
        }

        private fun decodeCustomTabs(raw: String?): List<CustomLibraryTab> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id").trim()
                        val title = o.optString("title").trim()
                        if (id.isEmpty() || title.isEmpty()) continue
                        add(
                            CustomLibraryTab(
                                id = id,
                                title = title,
                                order = o.optInt("order", i),
                            ),
                        )
                    }
                }.sortedBy { it.order }
            }.getOrDefault(emptyList())
        }
    }
}
