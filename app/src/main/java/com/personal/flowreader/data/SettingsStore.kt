package com.personal.flowreader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    val readerPrefs: Flow<ReaderPrefs> = store.data.map { it.toReaderPrefs() }
    val ttsPrefs: Flow<TtsPrefs> = store.data.map { it.toTtsPrefs() }

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
        )
    }
}
