package com.personal.flowreader.ui.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Loading pulse — independent of accent. */
private val RailLoading = Color(0xFF6A6A6A)
private val RailLoadingBright = Color(0xFF8A8A8A)
private val RailDotRadius = 1.25.dp
private val RailDotStep = FlowTokens.Space.XS
/** Loading pill matches cache-dot diameter. */
private val RailBarWidth = RailDotRadius * 2
private val RailCurrentBarWidth = 7.dp
private const val RailForceRegenHoldMs = 3_000L
/** Loading solid → cache window (dots) after Edge audio lands. */
private const val RailReadyRevealMs = 1_500
/** Behind (before) cache dots — neutral gray. Ahead uses soft accent (secondary). */
private val RailBehindDot = FlowTokens.NeutralCacheGray

/**
 * Hold without letting LazyColumn steal the gesture after touch-slop.
 * Consumes small moves inside the rail hit target for [holdMs], then fires [onHold].
 */
private suspend fun PointerInputScope.detectRailHold(
    holdMs: Long,
    onHold: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val holdSlop = viewConfiguration.touchSlop * 2f
        // Completes early on release or drag; null means the finger outlasted [holdMs].
        val heldToTimeout = withTimeoutOrNull(holdMs) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull
                val travel = (change.position - down.position).getDistance()
                if (travel > holdSlop) return@withTimeoutOrNull
                // Consume so the parent LazyColumn does not start a scroll.
                change.consume()
                if (!change.pressed) return@withTimeoutOrNull
            }
        } == null
        if (heldToTimeout) {
            onHold()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { it.consume() }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
            }
        }
    }
}

@Composable
internal fun LocusRail(
    modifier: Modifier,
    sentences: List<BlockSentence>,
    textLayout: TextLayoutResult?,
    currentSentenceIndex: Int,
    ready: Set<Int>,
    generating: Set<Int>,
    softAccent: Color,
    onForceRegenerate: (Int) -> Unit,
    /** Root-Y lines of the current sentence; fires on every layout or scroll move. */
    onCurrentSpan: (SentenceSpan) -> Unit,
) {
    if (sentences.isEmpty()) {
        Spacer(modifier)
        return
    }

    val farthestGenerating = generating.maxOrNull()
    val transition = rememberInfiniteTransition(label = "rail-pulse")
    // Composing the animation only while something is generating keeps the per-frame
    // clock off entirely during normal reading.
    val pulse = if (generating.isEmpty()) {
        0f
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rail-pulse-t",
        ).value
    }
    val density = LocalDensity.current
    val onForceRegenerateState = rememberUpdatedState(onForceRegenerate)

    fun segmentGenerating(index: Int): Boolean = index in generating
    fun segmentReady(index: Int): Boolean =
        index in ready && index !in generating && index != currentSentenceIndex
    fun canForceRegenerate(index: Int): Boolean =
        index in ready && index !in generating

    /** One shared absolute-Y grid; windows only reveal it (lapping won't densify). */
    fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCacheDotWindows(
        windows: List<Pair<Float, Float>>,
        color: Color,
    ) {
        if (windows.isEmpty()) return
        val step = RailDotStep.toPx()
        val r = RailDotRadius.toPx()
        val cx = size.width * 0.5f
        val phase = step * 0.5f
        for ((top, bottom) in windows) {
            if (bottom <= top) continue
            clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
                var y = phase + ceil((top - phase) / step).toInt() * step
                while (y < bottom) {
                    drawCircle(color = color, radius = r, center = Offset(cx, y))
                    y += step
                }
            }
        }
    }

    Box(
        modifier.drawBehind {
            val layout = textLayout
            val behind = ArrayList<Pair<Float, Float>>()
            val ahead = ArrayList<Pair<Float, Float>>()
            if (layout != null && layout.layoutInput.text.isNotEmpty()) {
                val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
                for (s in sentences) {
                    if (!segmentReady(s.index)) continue
                    val start = s.start.coerceIn(0, lastChar)
                    val endInclusive = (s.end - 1).coerceIn(start, lastChar)
                    val top = layout.getLineTop(layout.getLineForOffset(start))
                    val bottom = layout.getLineBottom(layout.getLineForOffset(endInclusive))
                    if (s.index > currentSentenceIndex) ahead += top to bottom
                    else behind += top to bottom
                }
            } else {
                val h = size.height / sentences.size.coerceAtLeast(1)
                for ((i, s) in sentences.withIndex()) {
                    if (!segmentReady(s.index)) continue
                    val top = i * h
                    val bottom = (i + 1) * h
                    if (s.index > currentSentenceIndex) ahead += top to bottom
                    else behind += top to bottom
                }
            }
            // Before = gray; after = soft accent (scheme secondary).
            drawCacheDotWindows(behind, RailBehindDot)
            drawCacheDotWindows(ahead, softAccent)
        },
    ) {
        val layout = textLayout
        val reportCurrent = rememberUpdatedState(onCurrentSpan)
        fun Modifier.reportIfCurrent(sentenceIndex: Int): Modifier {
            if (sentenceIndex != currentSentenceIndex) return this
            return onGloballyPositioned { coords ->
                val top = coords.positionInRoot().y
                reportCurrent.value(SentenceSpan(sentenceIndex, top, top + coords.size.height))
            }
        }
        // Farthest from current drawn first; current gets zIndex 0 (top) when bars lap.
        val drawOrder = remember(sentences, currentSentenceIndex) {
            sentences.sortedByDescending { abs(it.index - currentSentenceIndex) }
        }
        if (layout == null || layout.layoutInput.text.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                for (s in sentences) {
                    key(s.index) {
                        RailSegmentMark(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .zIndex(-abs(s.index - currentSentenceIndex).toFloat())
                                .reportIfCurrent(s.index),
                            isCurrent = s.index == currentSentenceIndex,
                            isGenerating = segmentGenerating(s.index),
                            isReady = segmentReady(s.index),
                            emphasizePulse = s.index == farthestGenerating ||
                                (segmentGenerating(s.index) && s.index == currentSentenceIndex),
                            pulse = pulse,
                            canForceRegenerate = canForceRegenerate(s.index),
                            onForceRegenerate = { onForceRegenerateState.value(s.index) },
                        )
                    }
                }
            }
            return@Box
        }

        val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
        for (s in drawOrder) {
            val start = s.start.coerceIn(0, lastChar)
            val endInclusive = (s.end - 1).coerceIn(start, lastChar)
            // Same geometry the amber SpanStyle highlight uses: line boxes for the char range.
            val top = layout.getLineTop(layout.getLineForOffset(start))
            val bottom = layout.getLineBottom(layout.getLineForOffset(endInclusive))
            val heightPx = (bottom - top).coerceAtLeast(1f)
            val dist = abs(s.index - currentSentenceIndex)
            key(s.index) {
                RailSegmentMark(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(-dist.toFloat())
                        .offset { IntOffset(0, top.roundToInt()) }
                        .width(RailGutterWidth)
                        .height(with(density) { heightPx.toDp() })
                        .reportIfCurrent(s.index),
                    isCurrent = s.index == currentSentenceIndex,
                    isGenerating = segmentGenerating(s.index),
                    isReady = segmentReady(s.index),
                    emphasizePulse = s.index == farthestGenerating ||
                        (segmentGenerating(s.index) && s.index == currentSentenceIndex),
                    pulse = pulse,
                    canForceRegenerate = canForceRegenerate(s.index),
                    onForceRegenerate = { onForceRegenerateState.value(s.index) },
                )
            }
        }
    }
}

@Composable
private fun RailSegmentMark(
    modifier: Modifier,
    isCurrent: Boolean,
    isGenerating: Boolean,
    isReady: Boolean,
    emphasizePulse: Boolean,
    pulse: Float,
    canForceRegenerate: Boolean,
    onForceRegenerate: () -> Unit,
) {
    val railCurrent = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.background
    val onForceRegenerateState = rememberUpdatedState(onForceRegenerate)
    // 1 = solid loading cover; 0 = fully revealed cache window underneath.
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(isGenerating, isReady, isCurrent) {
        when {
            isGenerating -> reveal.snapTo(1f)
            isCurrent -> reveal.snapTo(0f)
            isReady -> {
                if (reveal.value > 0f) {
                    reveal.animateTo(
                        0f,
                        tween(RailReadyRevealMs, easing = FastOutSlowInEasing),
                    )
                }
            }
            else -> reveal.snapTo(0f)
        }
    }

    val pulseAmount = if (emphasizePulse) pulse else pulse * 0.45f
    val loadingColor = lerp(RailLoading, RailLoadingBright, pulseAmount)
    val showCurrentBar = isCurrent && !isGenerating
    val showLoadingCover = !isCurrent && reveal.value > 0.001f
    val barWidth = if (showCurrentBar) RailCurrentBarWidth else RailBarWidth
    val holdModifier = if (canForceRegenerate) {
        Modifier.pointerInput(canForceRegenerate) {
            detectRailHold(RailForceRegenHoldMs) {
                onForceRegenerateState.value()
            }
        }
    } else {
        Modifier
    }

    val barColor = when {
        showCurrentBar -> railCurrent
        isGenerating -> loadingColor
        showLoadingCover -> RailLoading.copy(alpha = reveal.value)
        else -> null
    }

    Box(modifier.then(holdModifier), contentAlignment = Alignment.Center) {
        val color = barColor ?: return@Box
        Box(
            Modifier
                .width(barWidth)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(barWidth / 2))
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to edge,
                            0.20f to color,
                            0.80f to color,
                            1f to edge,
                        ),
                    )
                },
        )
    }
}
