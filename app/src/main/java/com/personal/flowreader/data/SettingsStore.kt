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
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.ShareDomainRule
import com.personal.flowreader.share.ShareDomainRules
import com.personal.flowreader.share.SharePrefs
import kotlinx.coroutines.flow.first
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
    /** Quiet AudioTrack noise underlay while playing (helps some car head units). */
    val keepAliveUnderlay: Boolean = false,
    /** Edge: decode sentence MP3s into one continuous PCM AudioTrack. */
    val continuousPcmPlayback: Boolean = false,
    /** Extra pause (+) or overlap (−) after each spoken sentence (−1000…1000 ms). */
    val sentenceGapMs: Int = DEFAULT_SENTENCE_GAP_MS,
) {
    companion object {
        const val DEFAULT_EDGE_VOICE = "en-US-AndrewNeural"
        const val DEFAULT_PREFETCH = 2
        const val MIN_PREFETCH = 1
        const val MAX_PREFETCH = 10
        const val DEFAULT_SENTENCE_GAP_MS = 0
        const val MIN_SENTENCE_GAP_MS = -1000
        const val MAX_SENTENCE_GAP_MS = 1000
        const val SENTENCE_GAP_STEP_MS = 50

        fun coerceSentenceGapMs(ms: Int): Int {
            val clamped = ms.coerceIn(MIN_SENTENCE_GAP_MS, MAX_SENTENCE_GAP_MS)
            val stepped = ((clamped.toFloat() / SENTENCE_GAP_STEP_MS).roundToInt()
                * SENTENCE_GAP_STEP_MS)
            return stepped.coerceIn(MIN_SENTENCE_GAP_MS, MAX_SENTENCE_GAP_MS)
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

    suspend fun setKeepAliveUnderlay(enabled: Boolean) {
        store.edit { it[KEY_TTS_KEEP_ALIVE] = enabled }
    }

    suspend fun setContinuousPcmPlayback(enabled: Boolean) {
        store.edit { it[KEY_TTS_CONTINUOUS_PCM] = enabled }
    }

    suspend fun setSentenceGapMs(ms: Int) {
        store.edit { it[KEY_TTS_SENTENCE_GAP_MS] = TtsPrefs.coerceSentenceGapMs(ms) }
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

    suspend fun notificationsAskedOnce(): Boolean =
        store.data.first()[KEY_NOTIFICATIONS_ASKED] ?: false

    suspend fun setNotificationsAskedOnce(asked: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ASKED] = asked }
    }

    suspend fun shareOnce(): SharePrefs {
        val p = store.data.first()
        return SharePrefs(
            showQueInShareSheet = p[KEY_SHARE_SHOW_QUE] ?: true,
            askMode = runCatching {
                ShareAskMode.valueOf(p[KEY_SHARE_ASK_MODE] ?: ShareAskMode.Ask.name)
            }.getOrDefault(ShareAskMode.Ask),
        )
    }

    suspend fun setShowQueInShareSheet(enabled: Boolean) {
        store.edit { it[KEY_SHARE_SHOW_QUE] = enabled }
    }

    suspend fun setShareAskMode(mode: ShareAskMode) {
        store.edit { it[KEY_SHARE_ASK_MODE] = mode.name }
    }

    suspend fun shareDomainRulesOnce(): List<ShareDomainRule> {
        val raw = store.data.first()[KEY_SHARE_DOMAIN_RULES]
        if (raw.isNullOrBlank()) return ShareDomainRules.seed()
        val decoded = ShareDomainRules.decode(raw)
        return decoded.ifEmpty { ShareDomainRules.seed() }
    }

    suspend fun setShareDomainRules(rules: List<ShareDomainRule>) {
        store.edit { it[KEY_SHARE_DOMAIN_RULES] = ShareDomainRules.encode(rules) }
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
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_PREFETCH = intPreferencesKey("tts_prefetch")
        private val KEY_TTS_DOUBLE_TAP_PLAY = booleanPreferencesKey("tts_double_tap_play")
        private val KEY_TTS_AUTO_SCROLL = booleanPreferencesKey("tts_auto_scroll")
        private val KEY_TTS_KEEP_ALIVE = booleanPreferencesKey("tts_keep_alive")
        private val KEY_TTS_CONTINUOUS_PCM = booleanPreferencesKey("tts_continuous_pcm")
        private val KEY_TTS_SENTENCE_GAP_MS = intPreferencesKey("tts_sentence_gap_ms")
        private val KEY_GLOBAL_FILTERS = stringPreferencesKey("global_filters")
        private val KEY_GROUP_FILTERS = stringPreferencesKey("group_filters")
        private val KEY_LIBRARY_VIEW = stringPreferencesKey("library_view")
        private val KEY_LIBRARY_TAB = stringPreferencesKey("library_tab")
        private val KEY_ENABLED_PLUGINS = stringPreferencesKey("enabled_plugins")
        private val KEY_NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")
        private val KEY_SHARE_SHOW_QUE = booleanPreferencesKey("share_show_que")
        private val KEY_SHARE_ASK_MODE = stringPreferencesKey("share_ask_mode")
        private val KEY_SHARE_DOMAIN_RULES = stringPreferencesKey("share_domain_rules")

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
            keepAliveUnderlay = this[KEY_TTS_KEEP_ALIVE] ?: false,
            continuousPcmPlayback = (this[KEY_TTS_CONTINUOUS_PCM] ?: false).let { pcm ->
                val gap = TtsPrefs.coerceSentenceGapMs(
                    this[KEY_TTS_SENTENCE_GAP_MS] ?: TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
                )
                if (gap < 0) true else pcm
            },
            sentenceGapMs = TtsPrefs.coerceSentenceGapMs(
                this[KEY_TTS_SENTENCE_GAP_MS] ?: TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
            ),
        )
    }
}
