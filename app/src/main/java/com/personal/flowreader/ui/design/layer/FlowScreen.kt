package com.personal.flowreader.ui.design.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowMotion
import com.personal.flowreader.ui.theme.FlowTokens

/** The two primary screen types. */
enum class FlowScreenKind {
    /** System bars visible: docks and actions clear the status and navigation bars. */
    Library,
    /** Immersive reading: no system bars, docks sit at the raw screen edges. */
    Reader,
}

/** Space currently covered by the docks; content pads by this. */
@Immutable
data class FlowScreenPadding(val top: Dp, val bottom: Dp) {
    companion object {
        val Zero = FlowScreenPadding(0.dp, 0.dp)
    }
}

/**
 * Shell for a primary screen. Draws, in [FlowLayer] order: [content], the top and bottom
 * docks, then the action layer ([fab] bottom-end, [snackbar] above it). The FAB hides while
 * any fullscreen card is open. Content receives the docks' occupied heights.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowScreen(
    kind: FlowScreenKind,
    modifier: Modifier = Modifier,
    topDock: (@Composable FlowDockScope.() -> Unit)? = null,
    bottomDock: (@Composable FlowDockScope.() -> Unit)? = null,
    fab: (@Composable () -> Unit)? = null,
    snackbar: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.(FlowScreenPadding) -> Unit,
) {
    var topHeight by remember { mutableStateOf(0.dp) }
    var bottomHeight by remember { mutableStateOf(0.dp) }
    val overlays = LocalFlowOverlays.current
    val barsVisible = kind == FlowScreenKind.Library
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (barsVisible) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier),
        ) {
            content(FlowScreenPadding(topHeight, bottomHeight))
        }
        if (topDock != null) {
            FlowDock(
                edge = DockEdge.Top,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(if (barsVisible) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier),
                onOccupiedHeight = { topHeight = it },
                content = topDock,
            )
        }
        if (bottomDock != null) {
            FlowDock(
                edge = DockEdge.Bottom,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .then(
                        if (barsVisible) {
                            Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                        } else {
                            Modifier
                        },
                    ),
                onOccupiedHeight = { bottomHeight = it },
                content = bottomDock,
            )
        }
        val fabVisible = fab != null && (overlays?.isEmpty ?: true)
        val actionInsets = if (barsVisible) {
            Modifier.windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
        } else {
            Modifier
        }
        AnimatedVisibility(
            visible = fabVisible,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(FlowLayer.Action.z)
                .then(actionInsets)
                .padding(end = FlowTokens.Pad.Screen, bottom = bottomHeight + FlowTokens.Pad.Screen),
            enter = FlowMotion.popEnter(),
            exit = FlowMotion.popExit(),
        ) {
            fab?.invoke()
        }
        if (snackbar != null) {
            val lift = if (fabVisible) FlowTokens.Comp.Fab + FlowTokens.Space.S else FlowTokens.Space.None
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(FlowLayer.Action.z)
                    .then(actionInsets)
                    .padding(bottom = bottomHeight + FlowTokens.Pad.Screen + lift),
            ) { snackbar() }
        }
    }
}
