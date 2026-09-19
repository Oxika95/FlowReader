package com.personal.flowreader.ui.open

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OpenUi(
    val theme: ThemeMode = ThemeMode.Oled,
    val accentHue: Float = AccentHue.DEFAULT,
    val fontScale: Float = 1f,
    val fontFamily: ReaderFont = ReaderFont.Sans,
    val lineSpacing: Float = 1f,
    val orientation: ReaderOrientation = ReaderOrientation.Auto,
)

class OpenBookViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    private val _ui = MutableStateFlow(OpenUi())
    val ui: StateFlow<OpenUi> = _ui

    init {
        viewModelScope.launch {
            val prefs = flow.settings.readerOnce()
            _ui.value = _ui.value.copy(
                theme = prefs.theme,
                accentHue = prefs.accentHue,
                fontScale = prefs.fontScale,
                fontFamily = prefs.fontFamily,
                lineSpacing = prefs.lineSpacing,
                orientation = prefs.orientation,
            )
        }
    }

    fun setTheme(mode: ThemeMode) {
        _ui.value = _ui.value.copy(theme = mode)
        viewModelScope.launch { flow.settings.setTheme(mode) }
    }

    fun setAccentHue(hue: Float) {
        val value = hue.coerceIn(AccentHue.MIN, AccentHue.MAX)
        _ui.value = _ui.value.copy(accentHue = value)
        viewModelScope.launch { flow.settings.setAccentHue(value) }
    }

    fun setFontScale(scale: Float) {
        val value = scale.coerceIn(0.85f, 1.75f)
        _ui.value = _ui.value.copy(fontScale = value)
        viewModelScope.launch { flow.settings.setFontScale(value) }
    }

    fun setFontFamily(font: ReaderFont) {
        _ui.value = _ui.value.copy(fontFamily = font)
        viewModelScope.launch { flow.settings.setFontFamily(font) }
    }

    fun setLineSpacing(spacing: Float) {
        val value = spacing.coerceIn(0.85f, 1.8f)
        _ui.value = _ui.value.copy(lineSpacing = value)
        viewModelScope.launch { flow.settings.setLineSpacing(value) }
    }

    fun setOrientation(orientation: ReaderOrientation) {
        _ui.value = _ui.value.copy(orientation = orientation)
        viewModelScope.launch { flow.settings.setOrientation(orientation) }
    }
}
