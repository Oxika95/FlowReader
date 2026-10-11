package com.personal.flowreader.ui.design.card.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt

/**
 * Reading-position slider whose track is the cache [MediaSegmentStrip]. Dragging moves the locus
 * mark; release calls [onSeek] with the chosen segment when it differs from the saved one.
 * [MediaSegments.pending] holds the mark there while the caller confirms. Null [onSeek] = read-only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSegmentSlider(segments: MediaSegments, onSeek: ((Int) -> Unit)?, modifier: Modifier = Modifier) {
    val count = segments.count
    if (count <= 0) return
    val last = (count - 1).coerceAtLeast(1)
    val shown = segments.pending ?: segments.locus
    var dragging by remember(segments.locus, segments.pending) { mutableStateOf<Int?>(null) }
    val current = (dragging ?: shown).coerceIn(0, count - 1)
    Slider(
        value = current.toFloat(),
        onValueChange = { dragging = it.roundToInt().coerceIn(0, count - 1) },
        onValueChangeFinished = {
            val target = dragging
            dragging = null
            if (target != null && target != segments.locus) onSeek?.invoke(target)
        },
        valueRange = 0f..last.toFloat(),
        enabled = onSeek != null && count > 1,
        modifier = modifier
            .fillMaxWidth()
            .semantics { stateDescription = "Chapter ${current + 1} of $count" },
        thumb = {
            Box(
                Modifier
                    .size(FlowTokens.Comp.SegmentStrip)
                    .background(MaterialTheme.colorScheme.primary, FlowTokens.Shape.Pill),
            )
        },
        track = {
            MediaSegmentStrip(
                segments.copy(locus = current),
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.Comp.SegmentStrip),
            )
        },
    )
}
