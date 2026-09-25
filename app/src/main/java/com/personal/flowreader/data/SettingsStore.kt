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
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_settings")

data class ReaderPrefs(
    val theme: ThemeMode = ThemeMode.Oled,
    val accentHue: Float = AccentHue.DEFAULT,
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
    /** When true, the list keeps the spoken block centered until the user scrolls away. */
    val autoScrollWithTts: Boolean = true,
    /**
     * Tonal underlay while playing. `0` = off; otherwise dB below full-scale PCM
     * (negative). Snaps to [TONAL_UNDERLAY_STEPS]. Keeps a faint signal on the
     * output so some car head units stay awake.
     */
    val minSignal: Float = DEFAULT_MIN_SIGNAL,
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

        /**
         * Maps legacy 0…10 underlay prefs (gain = value/10) onto the dB ladder.
         * Values already on the new ladder pass through [coerceMinSignal].
         */
        fun migrateMinSignal(stored: Float): Float {
            if (stored == 0f) return DEFAULT_MIN_SIGNAL
            // New format: negative dB steps.
            if (stored < 0f) return coerceMinSignal(stored)
            // Legacy 0…10 linear loudness → dB FS via gain = stored/10.
            val gain = (stored / 10f).coerceIn(0.0001f, 1f)
            val db = 20f * log10(gain)
            return coerceMinSignal(db)
        }

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

    suspend fun setAutoScrollWithTts(enabled: Boolean) {
        store.edit { it[KEY_TTS_AUTO_SCROLL] = enabled }
    }

    suspend fun setMinSignal(level: Float) {
        store.edit { it[KEY_TTS_TONAL_UNDERLAY] = TtsPrefs.coerceMinSignal(level) }
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

    /**
     * Router rules (content kind → match/parse → destination).
     * Migrates legacy handoffs + route defaults once.
     */
    suspend fun shareRouterRulesOnce(): List<RouterRule> {
        val p = store.data.first()
        val raw = p[KEY_SHARE_ROUTER_RULES]
        if (!raw.isNullOrBlank()) {
            val decoded = RouterRules.decode(raw)
            if (decoded.isNotEmpty()) return decoded
        }
        val migrated = RouterRules.migrateFromLegacy(
            handoffJson = p[KEY_SHARE_PLUGIN_HANDOFFS],
            bookDest = p[KEY_SHARE_ROUTE_BOOK],
            textDest = p[KEY_SHARE_ROUTE_TEXT],
            urlDest = p[KEY_SHARE_ROUTE_URL],
            legacyDomainJson = p[KEY_SHARE_DOMAIN_RULES],
        )
        store.edit { it[KEY_SHARE_ROUTER_RULES] = RouterRules.encode(migrated) }
        return migrated
    }

    suspend fun setShareRouterRules(rules: List<RouterRule>) {
        store.edit { it[KEY_SHARE_ROUTER_RULES] = RouterRules.encode(rules) }
    }

    suspend fun shareParseRulesOnce(): List<ParseRule> {
        val p = store.data.first()
        val raw = p[KEY_SHARE_PARSE_RULES]
        if (!raw.isNullOrBlank()) {
            return ParseRules.decode(raw)
        }
        val legacy = p[KEY_SHARE_DOMAIN_RULES]
        if (!legacy.isNullOrBlank()) {
            val (_, parses) = ParseRules.migrateLegacy(legacy)
            store.edit { it[KEY_SHARE_PARSE_RULES] = ParseRules.encode(parses) }
            return parses
        }
        return emptyList()
    }

    suspend fun setShareParseRules(rules: List<ParseRule>) {
        store.edit { it[KEY_SHARE_PARSE_RULES] = ParseRules.encode(rules) }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_ACCENT = stringPreferencesKey("accent") // legacy enum name
        private val KEY_ACCENT_HUE = floatPreferencesKey("accent_hue")
        private val KEY_UI_SCALE = floatPreferencesKey("ui_scale")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_JUSTIFY_TEXT = booleanPreferencesKey("justify_text")
        private val KEY_ORIENTATION = stringPreferencesKey("orientation")
        private val KEY_SHOW_CHAPTER_HEADINGS = booleanPreferencesKey("show_chapter_headings")
        private val KEY_KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        private val KEY_DEBUG_ENABLED = booleanPreferencesKey("debug_enabled")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_PREFETCH = intPreferencesKey("tts_prefetch")
        private val KEY_TTS_DOUBLE_TAP_PLAY = booleanPreferencesKey("tts_double_tap_play")
        private val KEY_TTS_AUTO_SCROLL = booleanPreferencesKey("tts_auto_scroll")
        private val KEY_TTS_KEEP_ALIVE = booleanPreferencesKey("tts_keep_alive")
        private val KEY_TTS_MIN_SIGNAL = floatPreferencesKey("tts_min_signal")
        private val KEY_TTS_TONAL_UNDERLAY = floatPreferencesKey("tts_tonal_underlay")
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
        private val KEY_SHARE_ROUTE_BOOK = stringPreferencesKey("share_route_book") // legacy
        private val KEY_SHARE_ROUTE_TEXT = stringPreferencesKey("share_route_text") // legacy
        private val KEY_SHARE_ROUTE_URL = stringPreferencesKey("share_route_url") // legacy
        private val KEY_SHARE_DOMAIN_RULES = stringPreferencesKey("share_domain_rules") // legacy
        private val KEY_SHARE_PLUGIN_HANDOFFS = stringPreferencesKey("share_plugin_handoffs") // legacy
        private val KEY_SHARE_ROUTER_RULES = stringPreferencesKey("share_router_rules")
        private val KEY_SHARE_PARSE_RULES = stringPreferencesKey("share_parse_rules")

        private fun decodeIdSet(raw: String?): Set<String> =
            raw?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet().orEmpty()

        private fun Preferences.toReaderPrefs() = ReaderPrefs(
            theme = runCatching { ThemeMode.valueOf(this[KEY_THEME] ?: ThemeMode.Oled.name) }
                .getOrDefault(ThemeMode.Oled),
            accentHue = resolveAccentHue(),
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
        )

        private fun Preferences.resolveAccentHue(): Float {
            this[KEY_ACCENT_HUE]?.let { return it.coerceIn(AccentHue.MIN, AccentHue.MAX) }
            // Migrate legacy named accents → hues.
            return when (this[KEY_ACCENT]) {
                "Purple" -> 288f
                "Blue" -> 218f
                "Teal" -> 174f
                "Green" -> 142f
                "Amber" -> 38f
                "Rose" -> 348f
                else -> AccentHue.DEFAULT
            }
        }

        private fun Preferences.toTtsPrefs() = TtsPrefs(
            engineKey = this[KEY_TTS_ENGINE] ?: TtsEngines.EDGE,
            voiceId = this[KEY_TTS_VOICE] ?: TtsPrefs.DEFAULT_EDGE_VOICE,
            speed = this[KEY_TTS_SPEED] ?: 1f,
            pitch = this[KEY_TTS_PITCH] ?: 1f,
            prefetchCount = (this[KEY_TTS_PREFETCH] ?: TtsPrefs.DEFAULT_PREFETCH)
                .coerceIn(TtsPrefs.MIN_PREFETCH, TtsPrefs.MAX_PREFETCH),
            doubleTapPlay = this[KEY_TTS_DOUBLE_TAP_PLAY] ?: true,
            autoScrollWithTts = this[KEY_TTS_AUTO_SCROLL] ?: true,
            minSignal = TtsPrefs.migrateMinSignal(
                this[KEY_TTS_TONAL_UNDERLAY] ?: when {
                    this[KEY_TTS_MIN_SIGNAL] != null -> this[KEY_TTS_MIN_SIGNAL]!! / 10f
                    this[KEY_TTS_KEEP_ALIVE] == true -> 1f
                    else -> TtsPrefs.DEFAULT_MIN_SIGNAL
                },
            ),
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
