package com.personal.flowreader.ui.design.controls

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.personal.flowreader.ui.theme.FlowMotion
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * List-row skeleton stencils (cover + title/meta bars) for empty loading states.
 * Matches [com.personal.flowreader.ui.design.card.FlowDisplayLayout.Row] proportions.
 */
@Composable
fun FlowSkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    Column(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = "Loading" }
            .padding(horizontal = FlowTokens.Pad.Screen),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        repeat(rows.coerceAtLeast(1)) { FlowSkeletonRow() }
    }
}

@Composable
private fun FlowSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(FlowTokens.Comp.ListRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            Modifier
                .height(FlowTokens.Comp.ListRow)
                .aspectRatio(FlowTokens.CoverAspect)
                .clip(FlowTokens.Shape.Field),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = FlowTokens.Space.M,
                    end = FlowTokens.Space.L,
                    top = FlowTokens.Space.S,
                    bottom = FlowTokens.Space.S,
                ),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        ) {
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(SkeletonTitleFraction)
                    .height(FlowTokens.Comp.SkeletonTitle)
                    .clip(FlowTokens.Shape.Pill),
            )
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(SkeletonMetaFraction)
                    .height(FlowTokens.Comp.SkeletonMeta)
                    .clip(FlowTokens.Shape.Pill),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S)) {
                SkeletonBlock(
                    Modifier
                        .width(FlowTokens.Comp.SkeletonChip)
                        .height(FlowTokens.Comp.SkeletonMeta)
                        .clip(FlowTokens.Shape.Pill),
                )
                SkeletonBlock(
                    Modifier
                        .width(FlowTokens.Comp.SkeletonChipWide)
                        .height(FlowTokens.Comp.SkeletonMeta)
                        .clip(FlowTokens.Shape.Pill),
                )
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = FlowTokens.Alpha.Track)
    var widthPx by remember { mutableFloatStateOf(0f) }
    val shift by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(FlowMotion.Skeleton, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer",
    )
    val span = widthPx.coerceAtLeast(1f)
    Box(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat() }
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(x = shift * span * 2f - span, y = 0f),
                    end = Offset(x = shift * span * 2f, y = 0f),
                ),
            ),
    )
}

private const val SkeletonTitleFraction = 0.72f
private const val SkeletonMetaFraction = 0.48f
