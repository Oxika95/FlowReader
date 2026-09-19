package com.personal.flowreader.ui.reader

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Double-tap while selection is visible: observe on Initial pass so selection
 * handlers cannot swallow the gesture before we cancel.
 */
internal suspend fun PointerInputScope.detectSelectionCancelGestures(onCancel: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        secondDown.consume()
        onCancel()
        waitForUpOrCancellation(pass = PointerEventPass.Initial)
    }
}

/**
 * Android-style back: swipe left starting within [edgeWidthPx] of the right edge.
 * Uses the Initial pass and only consumes once the swipe is confirmed, so vertical
 * scrolls and body taps in that strip still reach children.
 */
internal suspend fun PointerInputScope.detectRightEdgeBackSwipe(
    edgeWidthPx: Float,
    onBack: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.x < size.width - edgeWidthPx) return@awaitEachGesture

        val slop = viewConfiguration.touchSlop
        var totalDx = 0f
        var totalDy = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            val delta = change.positionChange()
            totalDx += delta.x
            totalDy += delta.y
            if (!change.pressed) return@awaitEachGesture

            val traveled = abs(totalDx) > slop || abs(totalDy) > slop
            if (!traveled) continue

            if (abs(totalDy) >= abs(totalDx) || totalDx >= -slop) {
                // Vertical or not leftward — leave the gesture for scroll/selection.
                return@awaitEachGesture
            }

            change.consume()
            onBack()
            while (true) {
                val rest = awaitPointerEvent(PointerEventPass.Initial)
                rest.changes.forEach { it.consume() }
                val c = rest.changes.firstOrNull { it.id == down.id }
                if (c == null || !c.pressed) break
            }
            return@awaitEachGesture
        }
    }
}
