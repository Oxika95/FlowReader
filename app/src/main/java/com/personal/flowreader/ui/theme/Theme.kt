package com.personal.flowreader.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.UiScale

private val LightBase = lightColorScheme()
/** Neutral charcoal dark — no Material purple tint on surfaces. */
private val DarkBase = darkColorScheme(
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF2C2C2C),
    surfaceDim = Color(0xFF121212),
    surfaceBright = Color(0xFF393939),
    surfaceContainerLowest = Color(0xFF0E0E0E),
    surfaceContainerLow = Color(0xFF1A1A1A),
    surfaceContainer = Color(0xFF1E1E1E),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    onBackground = Color(0xFFE3E3E3),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF444444),
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF1A1A1A),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
)
private val OledBase = darkColorScheme(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF1A1A1A),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0E),
    surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = Color(0xFF8E8E8E),
    outlineVariant = Color(0xFF333333),
    inverseSurface = Color(0xFFE3E3E3),
    inverseOnSurface = Color(0xFF1A1A1A),
    surfaceTint = Color.Transparent,
    scrim = Color.Black,
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
        // Keep tertiary on the same two accent sats — never Material's purple tertiary.
        tertiary = muted,
        onTertiary = onFor(muted),
        tertiaryContainer = container,
        onTertiaryContainer = onFor(container),
        surfaceTint = Color.Transparent,
    )
}

@Composable
fun FlowTheme(
    mode: ThemeMode,
    accentHue: Float = AccentHue.DEFAULT,
    uiScale: Float = UiScale.DEFAULT,
    content: @Composable () -> Unit,
) {
    val base = LocalDensity.current
    val scale = UiScale.coerce(uiScale)
    val scaled = remember(base.density, base.fontScale, scale) {
        Density(base.density * scale, base.fontScale)
    }
    CompositionLocalProvider(LocalDensity provides scaled) {
        MaterialTheme(colorScheme = schemeFor(mode, accentHue), content = content)
    }
}
