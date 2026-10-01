package com.personal.flowreader.ui.design.card.media

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.max

/** Continuous segment bar: gray behind, desaturated accent ahead, saturated accent at locus. */
@Composable
fun MediaSegmentStrip(segments: MediaSegments, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val ahead = accent.copy(alpha = FlowTokens.Alpha.SegmentBehind)
    val behind = FlowTokens.NeutralCacheGray
    val track = Color.White.copy(alpha = FlowTokens.Alpha.SegmentEmpty)
    Box(
        modifier = modifier.drawBehind {
            val count = segments.count
            if (count <= 0) return@drawBehind
            val barH = size.height * 0.55f
            val barTop = (size.height - barH) / 2f
            val locus = segments.locus.coerceIn(0, count - 1)
            val slot = size.width / count

            fun colorFor(i: Int): Color = when {
                i !in segments.filled -> track
                i < locus -> behind
                else -> ahead
            }

            // Merge adjacent same-color runs into continuous segments (no gaps/dots).
            var runStart = 0
            var runColor = colorFor(0)
            for (i in 1..count) {
                val next = if (i < count) colorFor(i) else null
                if (next != runColor) {
                    drawRect(runColor, Offset(runStart * slot, barTop), Size((i - runStart) * slot, barH))
                    if (next != null) {
                        runStart = i
                        runColor = next
                    }
                }
            }

            val markW = max(slot, FlowTokens.Comp.ProgressBar.toPx())
            val markX = (locus * slot + (slot - markW) / 2f).coerceIn(0f, size.width - markW)
            drawRect(accent, Offset(markX, 0f), Size(markW, size.height))
        },
    )
}
