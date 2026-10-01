package com.personal.flowreader.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.card.FlowFloatingCard
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/**
 * Library bottom dock: the active TTS session, shown while playing or paused. Tap opens the book
 * in the reader; play/pause works without leaving the library; X stops playback and closes it.
 */
@Composable
internal fun NowPlayingCard(
    title: String,
    snippet: String,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowFloatingCard(modifier = modifier, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = FlowTokens.Space.S, end = FlowTokens.Space.XS),
            ) {
                Text(title, style = FlowType.rowTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (snippet.isNotBlank()) {
                    Text(
                        snippet,
                        style = FlowType.hint,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            FlowIconButton(
                icon = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                onClick = onPlayPause,
            )
            FlowIconButton(
                icon = Icons.Filled.Close,
                contentDescription = "Stop and close",
                onClick = onClose,
            )
        }
    }
}
