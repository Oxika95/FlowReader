package com.personal.flowreader.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.AccentLightness
import com.personal.flowreader.data.AccentSaturation
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.accentPrimary

@Composable
internal fun ThemeSettingsPane(
    themeMode: ThemeMode,
    accentHue: Float,
    accentSaturation: Float,
    accentLightness: Float,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onAccentSaturation: (Float) -> Unit,
    onAccentLightness: (Float) -> Unit,
) {
    var accentDragging by remember { mutableStateOf(false) }
    var localAccent by remember { mutableFloatStateOf(accentHue) }
    val shownAccent = if (accentDragging) localAccent else accentHue
    var satDragging by remember { mutableStateOf(false) }
    var localSat by remember { mutableFloatStateOf(accentSaturation) }
    val shownSat = if (satDragging) localSat else accentSaturation
    var lightDragging by remember { mutableStateOf(false) }
    var localLight by remember { mutableFloatStateOf(accentLightness) }
    val shownLight = if (lightDragging) localLight else accentLightness
    val thumb = accentPrimary(shownAccent, themeMode, shownSat, shownLight)

    FlowLabel("Theme")
    FlowChipRow {
        ThemeMode.entries.forEach { mode ->
            FilterChip(
                selected = themeMode == mode,
                onClick = { onTheme(mode) },
                label = { Text(mode.label) },
            )
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    FlowLabel("Accent color")
    val chromaColors = remember(themeMode, shownSat, shownLight) {
        List(13) { i ->
            accentPrimary(i * 30f, themeMode, shownSat, shownLight)
        }
    }
    GradientSlider(
        value = shownAccent,
        onValueChange = {
            accentDragging = true
            localAccent = it
            onAccentHue(it)
        },
        onValueChangeFinished = {
            accentDragging = false
            onAccentHue(localAccent)
        },
        valueRange = AccentHue.MIN..AccentHue.MAX,
        track = chromaColors,
        thumb = thumb,
    )

    Spacer(Modifier.height(FlowTokens.Space.S))
    FlowLabel("Saturation")
    val satColors = remember(themeMode, shownAccent, shownLight) {
        List(5) { i ->
            val t = i / 4f
            accentPrimary(
                shownAccent,
                themeMode,
                AccentSaturation.MIN + t * (AccentSaturation.MAX - AccentSaturation.MIN),
                shownLight,
            )
        }
    }
    GradientSlider(
        value = shownSat,
        onValueChange = {
            satDragging = true
            localSat = it
            onAccentSaturation(it)
        },
        onValueChangeFinished = {
            satDragging = false
            onAccentSaturation(localSat)
        },
        valueRange = AccentSaturation.MIN..AccentSaturation.MAX,
        track = satColors,
        thumb = thumb,
    )

    Spacer(Modifier.height(FlowTokens.Space.S))
    FlowLabel("Lightness")
    val lightColors = remember(themeMode, shownAccent, shownSat) {
        List(5) { i ->
            val t = i / 4f
            accentPrimary(
                shownAccent,
                themeMode,
                shownSat,
                AccentLightness.MIN + t * (AccentLightness.MAX - AccentLightness.MIN),
            )
        }
    }
    GradientSlider(
        value = shownLight,
        onValueChange = {
            lightDragging = true
            localLight = it
            onAccentLightness(it)
        },
        onValueChangeFinished = {
            lightDragging = false
            onAccentLightness(localLight)
        },
        valueRange = AccentLightness.MIN..AccentLightness.MAX,
        track = lightColors,
        thumb = thumb,
    )
}

/** Slider drawn over a horizontal color gradient (accent hue, saturation, lightness). */
@Composable
private fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    track: List<Color>,
    thumb: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FlowTokens.Space.XS, bottom = FlowTokens.Space.Hair),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FlowTokens.Comp.AccentTrack)
                .align(Alignment.Center)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(track)),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = thumb,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
        )
    }
}
