package com.personal.flowreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.ThemeMode

private val LightBase = lightColorScheme()
private val DarkBase = darkColorScheme()
private val OledBase = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF2A2A2A),
)

private fun normalizeHue(hue: Float): Float {
    val h = hue % 360f
    return if (h < 0f) h + 360f else h
}

/** Full-saturation accent — current playhead, controls, chroma thumb. */
fun accentPrimary(hue: Float, mode: ThemeMode): Color {
    val (sat, light) = when (mode) {
        ThemeMode.Light -> 0.46f to 0.40f
        ThemeMode.Dark -> 0.58f to 0.64f
        ThemeMode.Oled -> 0.50f to 0.58f
    }
    return Color.hsl(normalizeHue(hue), sat, light)
}

/** Soft accent — same hue, one step less saturated. Cache-ahead dots, containers. */
fun accentMuted(hue: Float, mode: ThemeMode): Color {
    val (sat, light) = when (mode) {
        ThemeMode.Light -> 0.28f to 0.48f
        ThemeMode.Dark -> 0.34f to 0.56f
        ThemeMode.Oled -> 0.30f to 0.50f
    }
    return Color.hsl(normalizeHue(hue), sat, light)
}

/** Soft-accent fill at container lightness (same sat as [accentMuted]). */
fun accentContainer(hue: Float, mode: ThemeMode): Color {
    val sat = when (mode) {
        ThemeMode.Light -> 0.28f
        ThemeMode.Dark -> 0.34f
        ThemeMode.Oled -> 0.30f
    }
    val light = when (mode) {
        ThemeMode.Light -> 0.90f
        ThemeMode.Dark -> 0.24f
        ThemeMode.Oled -> 0.16f
    }
    return Color.hsl(normalizeHue(hue), sat, light)
}

private fun onFor(bg: Color): Color =
    if (bg.luminance() > 0.45f) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)

fun schemeFor(mode: ThemeMode, accentHue: Float = AccentHue.DEFAULT): ColorScheme {
    val base = when (mode) {
        ThemeMode.Light -> LightBase
        ThemeMode.Dark -> DarkBase
        ThemeMode.Oled -> OledBase
    }
    val primary = accentPrimary(accentHue, mode)
    val muted = accentMuted(accentHue, mode)
    val container = accentContainer(accentHue, mode)
    return base.copy(
        primary = primary,
        onPrimary = onFor(primary),
        primaryContainer = container,
        onPrimaryContainer = onFor(container),
        secondary = muted,
        onSecondary = onFor(muted),
        secondaryContainer = container,
        onSecondaryContainer = onFor(container),
    )
}

@Composable
fun FlowTheme(
    mode: ThemeMode,
    accentHue: Float = AccentHue.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = schemeFor(mode, accentHue), content = content)
}
