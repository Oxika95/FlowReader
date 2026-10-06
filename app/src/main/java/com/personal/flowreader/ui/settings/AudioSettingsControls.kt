package com.personal.flowreader.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.min

@Composable
internal fun SettingsFlyoutHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (expanded) "Hide $title" else "Show $title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatSentenceGapLabel(ms: Int): String = when {
    ms > 0 -> "+$ms ms pause"
    ms < 0 -> "$ms ms fade"
    else -> "0 ms"
}

internal fun formatHighlightSyncLabel(ms: Int): String = when {
    ms > 0 -> "+$ms ms"
    ms < 0 -> "$ms ms"
    else -> "0 ms"
}

internal fun formatMinSignalLabel(level: Float): String =
    TtsPrefs.TONAL_UNDERLAY_LABELS[TtsPrefs.tonalUnderlayIndex(level)]

/**
 * Slider whose active track grows from value 0 (visual center when the range is
 * symmetric) toward the thumb — left for negative, right for positive.
 *
 * Matches [SliderDefaults.Track] geometry (16.dp height, thumb gaps, inside
 * corners, step ticks) with a centered active range and − / + end labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CenterOriginSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = SliderDefaults.colors()
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = colors,
        track = { state ->
            CenterOriginSliderTrack(
                value = state.value,
                valueRange = state.valueRange,
                steps = steps,
                activeColor = colors.activeTrackColor,
                inactiveColor = colors.inactiveTrackColor,
                activeTickColor = colors.activeTickColor,
                inactiveTickColor = colors.inactiveTickColor,
            )
        },
    )
}

/** M3 SliderTokens: ActiveTrackHeight / ActiveHandleLeadingSpace / HandleWidth. */
private val CenterOriginTrackHeight = 16.dp
private val CenterOriginThumbWidth = 4.dp
private val CenterOriginThumbTrackGap = 6.dp
private val CenterOriginInsideCorner = 2.dp

@Composable
private fun CenterOriginSliderTrack(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    activeColor: Color,
    inactiveColor: Color,
    activeTickColor: Color,
    inactiveTickColor: Color,
    modifier: Modifier = Modifier,
) {
    val stopSize = SliderDefaults.TrackStopIndicatorSize
    val textMeasurer = rememberTextMeasurer()
    val trackPath = remember { Path() }
    Canvas(modifier = modifier.fillMaxWidth().height(CenterOriginTrackHeight)) {
        val trackH = size.height
        val outerCorner = trackH / 2f
        val insideCorner = CenterOriginInsideCorner.toPx()
        val gap = CenterOriginThumbWidth.toPx() / 2f + CenterOriginThumbTrackGap.toPx()
        val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
        val zeroX = ((0f - valueRange.start) / span).coerceIn(0f, 1f) * size.width
        val thumbX = ((value - valueRange.start) / span).coerceIn(0f, 1f) * size.width
        val thumbLeft = thumbX - gap
        val thumbRight = thumbX + gap
        val markerR = stopSize.toPx() / 2f

        fun drawSegment(
            left: Float,
            right: Float,
            color: Color,
            startCorner: Float,
            endCorner: Float,
        ) {
            val width = right - left
            if (width <= 0.5f) return
            val maxR = min(trackH / 2f, width / 2f)
            val startR = startCorner.coerceIn(0f, maxR)
            val endR = endCorner.coerceIn(0f, maxR)
            trackPath.rewind()
            trackPath.addRoundRect(
                RoundRect(
                    rect = Rect(left, 0f, right, trackH),
                    topLeft = CornerRadius(startR, startR),
                    topRight = CornerRadius(endR, endR),
                    bottomRight = CornerRadius(endR, endR),
                    bottomLeft = CornerRadius(startR, startR),
                ),
            )
            drawPath(trackPath, color)
        }

        // Same corner rules as SliderDefaults.Track: full round on outer ends,
        // insideCorner on grip-facing ends. Active nose is a full round centered
        // on zero so the center tick sits on that cap: ( • ==== ]|[
        // Draw inactive through the center first, then active on top so the nose
        // isn't covered (positive and negative must use the same order).
        val activeLeft: Float
        val activeRight: Float
        when {
            thumbX > zeroX + 0.5f -> {
                activeLeft = zeroX - outerCorner
                activeRight = thumbLeft
                drawSegment(0f, zeroX, inactiveColor, outerCorner, insideCorner)
                drawSegment(thumbRight, size.width, inactiveColor, insideCorner, outerCorner)
                drawSegment(activeLeft, activeRight, activeColor, outerCorner, insideCorner)
            }
            thumbX < zeroX - 0.5f -> {
                activeLeft = thumbRight
                activeRight = zeroX + outerCorner
                drawSegment(0f, thumbLeft, inactiveColor, outerCorner, insideCorner)
                drawSegment(zeroX, size.width, inactiveColor, insideCorner, outerCorner)
                drawSegment(activeLeft, activeRight, activeColor, insideCorner, outerCorner)
            }
            else -> {
                activeLeft = zeroX
                activeRight = zeroX
                drawSegment(0f, thumbLeft, inactiveColor, outerCorner, insideCorner)
                drawSegment(thumbRight, size.width, inactiveColor, insideCorner, outerCorner)
            }
        }

        fun isOnActive(x: Float): Boolean =
            x in activeLeft..activeRight && activeRight > activeLeft + 0.5f

        fun tickColor(x: Float): Color =
            if (isOnActive(x)) activeTickColor else inactiveTickColor

        fun inThumbGap(x: Float): Boolean = x in thumbLeft..thumbRight

        // M3 ticks lerp between the inset stop positions — not 0..width — so the
        // outermost slots sit exactly where stop indicators (our − / +) go.
        val tickStart = outerCorner
        val tickEnd = size.width - outerCorner
        fun tickX(index: Int, tickCount: Int): Float =
            tickStart + (tickEnd - tickStart) * (index.toFloat() / (tickCount - 1))

        // Step ticks; index 0 / last are replaced by − / + (same as M3 skipping
        // end ticks when stop indicators are drawn).
        if (steps > 0) {
            val tickCount = steps + 2
            for (i in 1 until tickCount - 1) {
                val x = tickX(i, tickCount)
                if (inThumbGap(x)) continue
                if (kotlin.math.abs(x - zeroX) < markerR * 2f) continue
                drawCircle(
                    color = tickColor(x),
                    radius = markerR,
                    center = Offset(x, center.y),
                )
            }
        }

        // End labels + center zero marker (track layer → under the thumb).
        // Hide when the thumb gap covers that slot — same as tick culling, and
        // matches M3 not drawing a stop when the inactive end segment is gone.
        fun drawEndLabel(label: String, centerX: Float, color: Color) {
            val layout = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            )
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(
                    centerX - layout.size.width / 2f,
                    center.y - layout.size.height / 2f,
                ),
            )
        }
        if (!inThumbGap(tickStart)) {
            drawEndLabel("−", tickStart, tickColor(tickStart))
        }
        // Center zero uses active-tick color so it reads on the active nose
        // (same contrast as ticks on the filled side of other sliders).
        if (!inThumbGap(zeroX)) {
            drawCircle(
                color = activeTickColor,
                radius = markerR,
                center = Offset(zeroX, center.y),
            )
        }
        if (!inThumbGap(tickEnd)) {
            drawEndLabel("+", tickEnd, tickColor(tickEnd))
        }
    }
}
