package com.personal.flowreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.personal.flowreader.data.ThemeMode

private val Light = lightColorScheme()
private val Dark = darkColorScheme()
private val Oled = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF121212),
)

fun schemeFor(mode: ThemeMode): ColorScheme = when (mode) {
    ThemeMode.Light -> Light
    ThemeMode.Dark -> Dark
    ThemeMode.Oled -> Oled
}

@Composable
fun FlowTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = schemeFor(mode), content = content)
}
