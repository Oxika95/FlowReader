package com.personal.flowreader.ui.debug

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import com.personal.flowreader.FlowApp
import com.personal.flowreader.R
import com.personal.flowreader.tts.SynthDebugLog
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.settings.ModalHeaderRow
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BubbleSize = FlowTokens.Comp.ButtonPrimary

/**
 * Draggable chat-head style bug bubble. Tap opens a full-screen synth log card
 * with live events and a save action.
 */
@Composable
fun DebugSynthDumpFab(modifier: Modifier = Modifier) {
    var panelOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        val density = LocalDensity.current
        val bubblePx = with(density) { BubbleSize.toPx() }
        val padPx = with(density) { FlowTokens.Space.M.toPx() }
        val maxX = (constraints.maxWidth - bubblePx - padPx).coerceAtLeast(padPx)
        val maxY = (constraints.maxHeight - bubblePx - padPx).coerceAtLeast(padPx)

        var offsetX by remember { mutableFloatStateOf(Float.NaN) }
        var offsetY by remember { mutableFloatStateOf(Float.NaN) }

        LaunchedEffect(maxX, maxY) {
            if (offsetX.isNaN()) {
                offsetX = maxX
                offsetY = padPx
            } else {
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
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = CircleShape,
                    )
                    .pointerInput(maxX, maxY, padPx) {
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
                                }
                                if (dragged) {
                                    change.consume()
                                    offsetX = (offsetX + delta.x).coerceIn(padPx, maxX)
                                    offsetY = (offsetY + delta.y).coerceIn(padPx, maxY)
                                }
                            }
                            if (!dragged) {
                                panelOpen = true
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_bug),
                    contentDescription = "Open synth log",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(FlowTokens.Icon.L),
                )
            }
        }

        SynthDebugLogPanel(
            visible = panelOpen,
            onDismiss = { panelOpen = false },
        )
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

    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
        fillMaxCardHeight = true,
        contentScrollable = false,
    ) {
        ModalHeaderRow(
            title = "Synth log",
            onDismiss = onDismiss,
            closeContentDescription = "Close synth log",
        )
        Text(
            if (lines.isEmpty()) "No events yet — play TTS to capture synthesizer activity."
            else "${lines.size} events (live)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = FlowTokens.ModalBodyPadding,
                vertical = FlowTokens.Space.XS,
            ),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = FlowTokens.ModalBodyPadding),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = FlowTokens.ModalBodyPadding,
                    vertical = FlowTokens.Space.S,
                ),
            horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = {
                    SynthDebugLog.clear()
                    saveStatus = null
                },
                enabled = lines.isNotEmpty(),
            ) {
                Text("Clear")
            }
            Button(
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
                modifier = Modifier.weight(1f),
            ) {
                Text("Save logs")
            }
        }
        saveStatus?.let { status ->
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = FlowTokens.ModalBodyPadding,
                    end = FlowTokens.ModalBodyPadding,
                    bottom = FlowTokens.Space.S,
                ),
            )
        }
    }
}
