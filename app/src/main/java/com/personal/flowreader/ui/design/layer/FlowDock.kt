package com.personal.flowreader.ui.design.layer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowMotion
import com.personal.flowreader.ui.theme.FlowTokens

enum class DockEdge { Top, Bottom }

/** Items of a [FlowDock]. Declare items in visual order, top to bottom. */
@Stable
interface FlowDockScope {
    val edge: DockEdge

    /**
     * One docked slot. Hidden items collapse with [FlowMotion.dockExit], so the remaining
     * items slide into place instead of jumping. Each item keeps [FlowTokens.Dock.Gap] to its
     * neighbor (or [FlowTokens.Dock.EdgePad] to the screen edge), measured border to border.
     */
    @Composable
    fun Item(
        visible: Boolean,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    )
}

private class DockScopeImpl(override val edge: DockEdge) : FlowDockScope {
    @Composable
    override fun Item(visible: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
        val fromTop = edge == DockEdge.Top
        AnimatedVisibility(
            visible = visible,
            modifier = modifier.fillMaxWidth(),
            enter = FlowMotion.dockEnter(fromTop),
            exit = FlowMotion.dockExit(fromTop),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (fromTop) FlowTokens.Dock.Gap else FlowTokens.Space.None,
                        bottom = if (fromTop) FlowTokens.Space.None else FlowTokens.Dock.Gap,
                    )
                    .padding(horizontal = FlowTokens.Pad.Screen),
            ) {
                content()
            }
        }
    }
}

/**
 * Top or bottom floating dock. Place it at the matching screen edge (FlowScreen does this).
 * [onOccupiedHeight] reports the space the dock currently covers — content padding,
 * FAB/snackbar lift, and the reader pin-edge logic consume it. An empty dock reports 0.
 */
@Composable
fun FlowDock(
    edge: DockEdge,
    modifier: Modifier = Modifier,
    onOccupiedHeight: (Dp) -> Unit = {},
    content: @Composable FlowDockScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = if (edge == DockEdge.Top) TopScope else BottomScope
    Column(
        modifier
            .fillMaxWidth()
            .zIndex(FlowLayer.Dock.z)
            .onSizeChanged { onOccupiedHeight(with(density) { it.height.toDp() }) },
    ) {
        scope.content()
    }
}

private val TopScope: FlowDockScope = DockScopeImpl(DockEdge.Top)
private val BottomScope: FlowDockScope = DockScopeImpl(DockEdge.Bottom)
