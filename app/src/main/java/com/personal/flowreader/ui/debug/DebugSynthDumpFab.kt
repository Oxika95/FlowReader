package com.personal.flowreader.ui.debug

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowSliderRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowDropdownRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.personal.flowreader.FlowApp
import com.personal.flowreader.R
import com.personal.flowreader.tts.SynthDebugLog
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BubbleSize = FlowTokens.Comp.ButtonPrimary
private val DismissTargetSize = 56.dp

/**
 * Draggable chat-head style bug bubble. Tap opens a full-screen synth log card
 * with live events and a save action. Drag onto the bottom dismiss target to
 * turn off debug mode (Android bubble-style remove).
 *
 * @param onCloseDebugger Turns off debug mode (hides the bubble and stops logging).
 */
@Composable
fun DebugSynthDumpFab(
    modifier: Modifier = Modifier,
    onCloseDebugger: () -> Unit,
) {
    var panelOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        val density = LocalDensity.current
        val bubblePx = with(density) { BubbleSize.toPx() }
        val padPx = with(density) { FlowTokens.Space.M.toPx() }
        val dismissSizePx = with(density) { DismissTargetSize.toPx() }
        val maxX = (constraints.maxWidth - bubblePx - padPx).coerceAtLeast(padPx)
        val maxY = (constraints.maxHeight - bubblePx - padPx).coerceAtLeast(padPx)
        val dragMaxX = (constraints.maxWidth - bubblePx).coerceAtLeast(0f)
        val dragMaxY = (constraints.maxHeight - bubblePx).coerceAtLeast(0f)
        val dismissCenter = Offset(
            x = constraints.maxWidth / 2f,
            y = constraints.maxHeight - padPx - dismissSizePx / 2f,
        )
        // Hit when bubble center is near the dismiss target (generous grab radius).
        val dismissHitRadius = bubblePx / 2f + dismissSizePx

        var offsetX by remember { mutableFloatStateOf(Float.NaN) }
        var offsetY by remember { mutableFloatStateOf(Float.NaN) }
        var dragging by remember { mutableStateOf(false) }
        var overDismiss by remember { mutableStateOf(false) }

        fun bubbleCenter() = Offset(offsetX + bubblePx / 2f, offsetY + bubblePx / 2f)

        fun updateOverDismiss() {
            overDismiss = (bubbleCenter() - dismissCenter).getDistance() <= dismissHitRadius
        }

        LaunchedEffect(maxX, maxY) {
            if (offsetX.isNaN()) {
                offsetX = maxX
                // Start below the top dock (title card) so the bubble never covers it.
                offsetY = with(density) { (FlowTokens.Comp.ButtonPrimary * 2).toPx() }.coerceAtMost(maxY)
            } else if (!dragging) {
                offsetX = offsetX.coerceIn(padPx, maxX)
                offsetY = offsetY.coerceIn(padPx, maxY)
            }
        }

        if (!panelOpen && !offsetX.isNaN() && !offsetY.isNaN()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(BubbleSize)
                    .background(
                        color = if (overDismiss) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        shape = CircleShape,
                    )
                    .pointerInput(maxX, maxY, padPx, dragMaxX, dragMaxY, dismissCenter, dismissHitRadius) {
                        val touchSlop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var total = Offset.Zero
                            var dragged = false
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                total += delta
                                if (!dragged && total.getDistance() > touchSlop) {
                                    dragged = true
                                    dragging = true
                                }
                                if (dragged) {
                                    change.consume()
                                    offsetX = (offsetX + delta.x).coerceIn(0f, dragMaxX)
                                    offsetY = (offsetY + delta.y).coerceIn(0f, dragMaxY)
                                    updateOverDismiss()
                                }
                            }
                            if (!dragged) {
                                panelOpen = true
                            } else if (overDismiss) {
                                dragging = false
                                overDismiss = false
                                onCloseDebugger()
                            } else {
                                offsetX = offsetX.coerceIn(padPx, maxX)
                                offsetY = offsetY.coerceIn(padPx, maxY)
                                dragging = false
                                overDismiss = false
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bug),
                    contentDescription = "Open synth log",
                    tint = if (overDismiss) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                    modifier = Modifier.size(FlowTokens.Icon.L),
                )
            }
        }

        BubbleDismissTarget(
            visible = dragging,
            armed = overDismiss,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = FlowTokens.Space.M),
        )

        SynthDebugLogPanel(
            visible = panelOpen,
            onDismiss = { panelOpen = false },
        )
    }
}

@Composable
private fun BubbleDismissTarget(
    visible: Boolean,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.25f else 1f,
        label = "dismissTargetScale",
    )
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.6f),
        exit = fadeOut() + scaleOut(targetScale = 0.6f),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .size(DismissTargetSize)
                .background(
                    color = if (armed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Drag here to turn off debugger",
                tint = if (armed) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(FlowTokens.Icon.L),
            )
        }
    }
}

@Composable
private fun SynthDebugLogPanel(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by SynthDebugLog.revision.collectAsState()
    val lines = remember(revision) { SynthDebugLog.snapshot() }
    val listState = rememberLazyListState()
    var saveStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(revision, visible) {
        if (!visible || lines.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(lines.lastIndex)
    }

    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Synth log",
        scrollable = false,
        footer = {
            FlowActionRow(
                start = {
                    FlowTextAction(
                        "Clear",
                        onClick = {
                            SynthDebugLog.clear()
                            saveStatus = null
                        },
                        enabled = lines.isNotEmpty(),
                    )
                },
            ) {
                FlowTextAction(
                    "Save",
                    onClick = {
                        scope.launch {
                            val app = context.applicationContext as FlowApp
                            saveStatus = withContext(Dispatchers.IO) {
                                runCatching { app.tts.dumpSynthLog() }.fold(
                                    onSuccess = { "Saved: ${it.absolutePath}" },
                                    onFailure = { it.message ?: "Save failed" },
                                )
                            }
                            saveStatus?.let {
                                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                )
            }
        },
    ) {
        FlowHint(
            if (lines.isEmpty()) "No events yet — play TTS to capture synthesizer activity."
            else "${lines.size} events (live)",
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = FlowTokens.Space.S),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.Hair),
        ) {
            items(lines.size) { index ->
                Text(
                    lines[index],
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        saveStatus?.let { status -> FlowHint(status) }
    }
}