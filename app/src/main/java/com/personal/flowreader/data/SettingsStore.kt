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
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "flow_settings")

data class ReaderPrefs(
    val theme: ThemeMode = ThemeMode.Oled,
    val accentHue: Float = AccentHue.DEFAULT,
    val fontScale: Float = 1f,
    val fontFamily: ReaderFont = ReaderFont.Sans,
    val lineSpacing: Float = 1f,
    val orientation: ReaderOrientation = ReaderOrientation.Auto,
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
) {
    companion object {
        const val DEFAULT_EDGE_VOICE = "en-US-AndrewNeural"
        const val DEFAULT_PREFETCH = 2
        const val MIN_PREFETCH = 1
        const val MAX_PREFETCH = 10
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

    suspend fun setFontScale(scale: Float) {
        store.edit { it[KEY_FONT_SCALE] = scale }
    }

    suspend fun setFontFamily(font: ReaderFont) {
        store.edit { it[KEY_FONT_FAMILY] = font.name }
    }

    suspend fun setLineSpacing(spacing: Float) {
        store.edit { it[KEY_LINE_SPACING] = spacing }
    }

    suspend fun setOrientation(orientation: ReaderOrientation) {
        store.edit { it[KEY_ORIENTATION] = orientation.name }
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

    suspend fun libraryTabOnce(): LibraryTab =
        runCatching {
            LibraryTab.valueOf(store.data.first()[KEY_LIBRARY_TAB] ?: LibraryTab.Files.name)
        }.getOrDefault(LibraryTab.Files)

    suspend fun setLibraryTab(tab: LibraryTab) {
        store.edit { it[KEY_LIBRARY_TAB] = tab.name }
    }

    suspend fun notificationsAskedOnce(): Boolean =
        store.data.first()[KEY_NOTIFICATIONS_ASKED] ?: false

    suspend fun setNotificationsAskedOnce(asked: Boolean) {
        store.edit { it[KEY_NOTIFICATIONS_ASKED] = asked }
    }

    companion object {
        private val KEY_THEME = stringPreferencesKey("theme")
        private val KEY_ACCENT = stringPreferencesKey("accent") // legacy enum name
        private val KEY_ACCENT_HUE = floatPreferencesKey("accent_hue")
        private val KEY_FONT_SCALE = floatPreferencesKey("font_scale")
        private val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        private val KEY_LINE_SPACING = floatPreferencesKey("line_spacing")
        private val KEY_ORIENTATION = stringPreferencesKey("orientation")
        private val KEY_TTS_ENGINE = stringPreferencesKey("tts_engine")
        private val KEY_TTS_VOICE = stringPreferencesKey("tts_voice")
        private val KEY_TTS_SPEED = floatPreferencesKey("tts_speed")
        private val KEY_TTS_PITCH = floatPreferencesKey("tts_pitch")
        private val KEY_TTS_PREFETCH = intPreferencesKey("tts_prefetch")
        private val KEY_TTS_DOUBLE_TAP_PLAY = booleanPreferencesKey("tts_double_tap_play")
        private val KEY_TTS_AUTO_SCROLL = booleanPreferencesKey("tts_auto_scroll")
        private val KEY_GLOBAL_FILTERS = stringPreferencesKey("global_filters")
        private val KEY_GROUP_FILTERS = stringPreferencesKey("group_filters")
        private val KEY_LIBRARY_VIEW = stringPreferencesKey("library_view")
        private val KEY_LIBRARY_TAB = stringPreferencesKey("library_tab")
        private val KEY_NOTIFICATIONS_ASKED = booleanPreferencesKey("notifications_asked")

        private fun Preferences.toReaderPrefs() = ReaderPrefs(
            theme = runCatching { ThemeMode.valueOf(this[KEY_THEME] ?: ThemeMode.Oled.name) }
                .getOrDefault(ThemeMode.Oled),
            accentHue = resolveAccentHue(),
            fontScale = this[KEY_FONT_SCALE] ?: 1f,
            fontFamily = runCatching {
                ReaderFont.valueOf(this[KEY_FONT_FAMILY] ?: ReaderFont.Sans.name)
            }.getOrDefault(ReaderFont.Sans),
            lineSpacing = this[KEY_LINE_SPACING] ?: 1f,
            orientation = runCatching {
                ReaderOrientation.valueOf(this[KEY_ORIENTATION] ?: ReaderOrientation.Auto.name)
            }.getOrDefault(ReaderOrientation.Auto),
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
        )
    }
}
