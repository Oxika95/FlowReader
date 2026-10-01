package com.personal.flowreader.ui.design.layer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowMotion
import com.personal.flowreader.ui.theme.FlowTokens

/** Scrim/dismiss behavior of one fullscreen overlay entry. */
data class FlowOverlaySpec(
    val scrimAlpha: Float = FlowTokens.Scrim.Standard,
    /** Scrim tap and Back call [onDismiss]; when false both are swallowed (e.g. busy sign-in). */
    val dismissible: Boolean = true,
    val onDismiss: () -> Unit = {},
)

/** One registered fullscreen card. Created by [rememberFlowOverlay]; drawn by [FlowOverlayHost]. */
@Stable
class FlowOverlayEntry internal constructor(internal val id: Long) {
    internal var visible by mutableStateOf(false)
    internal var spec by mutableStateOf(FlowOverlaySpec())
    internal var content by mutableStateOf<@Composable () -> Unit>({})
    internal var locals by mutableStateOf<CompositionLocalContext?>(null)
    internal val transition = MutableTransitionState(false)
}

/**
 * Stack of fullscreen cards in open order. The last visible entry is the top card: it alone
 * draws the scrim, receives Back, and is full scale; entries below are dimmed by that scrim
 * and scaled by [FlowTokens.Scrim.ParentScale].
 */
@Stable
class FlowOverlayState {
    internal val entries = mutableStateListOf<FlowOverlayEntry>()
    private var nextId = 0L

    /** Number of visible fullscreen cards. */
    val depth: Int by derivedStateOf { entries.count { it.visible } }

    /** True when no fullscreen card is visible; FABs and docks use this to hide. */
    val isEmpty: Boolean by derivedStateOf { depth == 0 }

    internal fun newEntry(): FlowOverlayEntry = FlowOverlayEntry(nextId++)

    internal fun setVisible(entry: FlowOverlayEntry, visible: Boolean) {
        if (visible == entry.visible && (!visible || entry in entries)) return
        if (visible) {
            entries.remove(entry)
            entries.add(entry)
        }
        entry.visible = visible
        entry.transition.targetState = visible
    }

    internal fun removeIfGone(entry: FlowOverlayEntry) {
        if (!entry.visible) entries.remove(entry)
    }

    internal fun top(): FlowOverlayEntry? = entries.lastOrNull { it.visible }
}

/** The overlay stack for the current window. Provided by [FlowOverlayHost]. */
val LocalFlowOverlays = staticCompositionLocalOf<FlowOverlayState?> { null }

/**
 * Root of every window that shows Flow UI (MainActivity, share ingress). Draws [content], then
 * the fullscreen card stack at [FlowLayer.Fullscreen], then [system] at [FlowLayer.System].
 */
@Composable
fun FlowOverlayHost(
    modifier: Modifier = Modifier,
    state: FlowOverlayState = remember { FlowOverlayState() },
    system: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    CompositionLocalProvider(LocalFlowOverlays provides state) {
        Box(modifier.fillMaxSize()) {
            content()
            OverlayLayer(state)
            Box(Modifier.fillMaxSize().zIndex(FlowLayer.System.z), content = system)
        }
    }
}

@Composable
private fun BoxScope.OverlayLayer(state: FlowOverlayState) {
    val top = state.top()
    val topIndex = top?.let { state.entries.indexOf(it) } ?: -1

    // One scrim, moved to sit directly under the top card. It keeps its last z while fading out.
    var scrimZ by remember { mutableFloatStateOf(entryZ(0) - 0.5f) }
    var scrimTarget by remember { mutableFloatStateOf(FlowTokens.Scrim.Standard) }
    if (top != null) {
        scrimZ = entryZ(topIndex) - 0.5f
        scrimTarget = top.spec.scrimAlpha
    }
    val scrimAlpha by animateFloatAsState(scrimTarget, label = "scrim")
    AnimatedVisibility(
        visible = top != null,
        modifier = Modifier.matchParentSize().zIndex(scrimZ),
        enter = FlowMotion.scrimEnter(),
        exit = FlowMotion.scrimExit(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    val t = state.top() ?: return@clickable
                    if (t.spec.dismissible) t.spec.onDismiss()
                },
        )
    }

    if (top != null) {
        key(top.id) {
            BackHandler {
                if (top.spec.dismissible) top.spec.onDismiss()
            }
        }
    }

    state.entries.forEachIndexed { index, entry ->
        key(entry.id) {
            LaunchedEffect(entry) {
                snapshotFlow { entry.transition.isIdle && !entry.transition.currentState }
                    .collect { gone -> if (gone) state.removeIfGone(entry) }
            }
            AnimatedVisibility(
                visibleState = entry.transition,
                modifier = Modifier.matchParentSize().zIndex(entryZ(index)),
                enter = FlowMotion.fullscreenEnter(),
                exit = FlowMotion.fullscreenExit(),
            ) {
                val locals = entry.locals
                if (locals != null) {
                    CompositionLocalProvider(locals) {
                        FlowOverlayFrame(isTop = entry === top) { entry.content() }
                    }
                } else {
                    FlowOverlayFrame(isTop = entry === top) { entry.content() }
                }
            }
        }
    }
}

private fun entryZ(index: Int): Float = FlowLayer.Fullscreen.z + index

/**
 * Registers a fullscreen overlay with the nearest [FlowOverlayHost] while [visible].
 *
 * Content is captured with the caller's composition locals. While hidden the last visible
 * content is frozen, so exit animations never render an empty or null state.
 * Use [com.personal.flowreader.ui.design.card.FlowFullscreenCard] instead of calling this directly.
 */
@Composable
fun rememberFlowOverlay(
    visible: Boolean,
    spec: FlowOverlaySpec,
    content: @Composable () -> Unit,
): FlowOverlayEntry {
    val state = LocalFlowOverlays.current
        ?: error("FlowFullscreenCard requires a FlowOverlayHost above it")
    val entry = remember(state) { state.newEntry() }
    val locals = currentCompositionLocalContext
    SideEffect {
        if (visible) {
            entry.content = content
            entry.locals = locals
            entry.spec = spec
        }
        state.setVisible(entry, visible)
    }
    DisposableEffect(state, entry) {
        onDispose { state.setVisible(entry, false) }
    }
    return entry
}
