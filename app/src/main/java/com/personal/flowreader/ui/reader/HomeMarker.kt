package com.personal.flowreader.ui.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.max
import com.personal.flowreader.data.HomePosition
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import kotlin.math.roundToInt

/** Bar thickness as a fraction of the body font size, so it grows with text size. */
private const val BarThicknessPerEm = 0.25f
private const val BarAlphaDragging = 0.85f
/** Touch slop added around the bar; the band blocks text gestures, so keep it tight. */
private val GripSlop = FlowTokens.Space.L
private val GripMinHeight = FlowTokens.Comp.ChipHeight

/**
 * Reader overlay at the home position: a solid bar across the reading column whose thickness
 * follows [textSize]. The whole bar is the drag grip. Fills the same box as the reading list so
 * fractions line up.
 */
@Composable
internal fun HomeMarker(
    position: Float,
    textSize: TextUnit,
    onPositionChange: (Float) -> Unit,
    onPositionCommitted: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    val current by rememberUpdatedState(position)
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thickness = with(density) { (textSize.toPx() * BarThicknessPerEm).toDp() }
        .coerceAtLeast(FlowTokens.Stroke.Hairline)
    val gripHeight = max(thickness + GripSlop, GripMinHeight)
    BoxWithConstraints(modifier.fillMaxSize()) {
        val heightPx = constraints.maxHeight.toFloat()
        val y = position * heightPx
        if (dragging) {
            Text(
                "Home ${(position * 100f).roundToInt()}%",
                style = FlowType.hint,
                color = color,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val lift = (gripHeight / 2 + FlowTokens.Space.L).roundToPx()
                        IntOffset(0, (y.roundToInt() - lift).coerceAtLeast(0))
                    }
                    .padding(start = ReaderContentStartPadding),
            )
        }
        Canvas(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(0, y.roundToInt() - gripHeight.roundToPx() / 2) }
                .fillMaxWidth()
                .height(gripHeight)
                .semantics { contentDescription = "Home position bar" }
                .pointerInput(heightPx) {
                    if (heightPx <= 0f) return@pointerInput
                    // Accumulate locally: [current] only catches up after recomposition.
                    var draft = current
                    detectVerticalDragGestures(
                        onDragStart = {
                            draft = current
                            dragging = true
                        },
                        onDragEnd = {
                            dragging = false
                            onPositionCommitted(draft)
                        },
                        onDragCancel = {
                            dragging = false
                            onPositionCommitted(draft)
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            draft = HomePosition.coerce(draft + dy / heightPx)
                            onPositionChange(draft)
                        },
                    )
                },
        ) {
            val stroke = thickness.toPx()
            val cy = size.height / 2f
            drawLine(
                color = color.copy(alpha = if (dragging) BarAlphaDragging else FlowTokens.Alpha.SegmentBehind),
                start = Offset(ReaderContentStartPadding.toPx() + stroke / 2f, cy),
                end = Offset(size.width - ReaderListEndPadding.toPx() - stroke / 2f, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
