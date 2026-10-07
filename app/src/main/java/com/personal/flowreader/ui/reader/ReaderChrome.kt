package com.personal.flowreader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.design.card.FlowFloatingCard
import com.personal.flowreader.ui.design.surface.FlowCover
import com.personal.flowreader.ui.design.surface.FlowProgressBar
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/*
 * Reader dock cards. Each is plain floating-card content: visibility, motion, and position come
 * from the FlowDock item that holds it (ReaderScreen), never from the card itself.
 */

/** Horizontal inset so the cover band ends before the side icon buttons. */
private val BannerCoverSideInset = FlowTokens.Comp.ButtonPrimary

/**
 * Top dock: back, title + chapter over a blurred cover band, settings; progress on the bottom edge.
 * Long-press opens the book's media card.
 */
@Composable
internal fun TitleBannerCard(
    title: String,
    chapter: String,
    progress: Float,
    storedPath: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover by rememberBookCover(storedPath, maxEdge = FlowTokens.CoverEdge.Banner)
    FlowFloatingCard(
        modifier = modifier,
        contentPadding = PaddingValues(FlowTokens.Space.None),
        onLongClick = onLongPress,
    ) {
        if (cover != null) {
            BannerCoverUnderlay(
                cover = cover!!,
                modifier = Modifier
                    .matchParentSize()
                    .padding(bottom = FlowTokens.Comp.ProgressBar)
                    .padding(horizontal = BannerCoverSideInset),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FlowTokens.Pad.FloatingCard)
                .padding(bottom = FlowTokens.Comp.ProgressBar),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(FlowTokens.Icon.Hero)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    modifier = Modifier.size(FlowTokens.Icon.M),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FlowTokens.Space.XS),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val textShadow = Shadow(
                    color = Color.Black.copy(alpha = FlowTokens.Alpha.TextShadow),
                    offset = Offset(0f, 1f),
                    blurRadius = 6f,
                )
                Text(
                    title,
                    style = FlowType.rowTitle.copy(shadow = textShadow),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    chapter,
                    style = FlowType.hint.copy(shadow = textShadow),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            // Same width as Back so the title stays centered.
            IconButton(onClick = onSettings, modifier = Modifier.size(FlowTokens.Icon.Hero)) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
        FlowProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun BannerCoverUnderlay(cover: ImageBitmap, modifier: Modifier = Modifier) {
    val cardBg = MaterialTheme.colorScheme.background
    Box(modifier) {
        FlowCover(art = cover, title = "", blur = true, modifier = Modifier.fillMaxSize())
        // Soft side fades into the card; light center wash for title contrast.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to cardBg,
                            0.18f to cardBg.copy(alpha = 0.35f),
                            0.50f to cardBg.copy(alpha = 0.45f),
                            0.82f to cardBg.copy(alpha = 0.35f),
                            1f to cardBg,
                        ),
                    ),
                ),
        )
    }
}

/**
 * Bottom dock: Contents, previous, play/pause (overlapping, taller than the card), next,
 * scroll lock. The item is as tall as the play control; the card stays short.
 */
@Composable
internal fun MediaControlCard(
    playing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onScrollLock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playSize = FlowTokens.Comp.Fab
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FlowFloatingCard(
            contentPadding = PaddingValues(horizontal = FlowTokens.Pad.FloatingCard, vertical = FlowTokens.Space.None),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous sentence")
                }
                // Slot for the overlapping play control; does not set card height.
                Spacer(Modifier.width(playSize))
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next sentence")
                }
            }
            IconButton(onClick = onToc, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
            }
            IconButton(onClick = onScrollLock, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(Icons.Filled.Lock, contentDescription = "Lock scroll to TTS")
            }
        }
        FilledIconButton(
            onClick = { if (playing) onPause() else onPlay() },
            modifier = Modifier
                .align(Alignment.Center)
                .size(playSize),
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                modifier = Modifier.size(FlowTokens.Icon.XL),
            )
        }
    }
}

/**
 * Bottom dock, scroll-lock mode: play/pause centered (where the media card's play control sits)
 * and unlock on the same point as the media card's lock button (card content pad + half the 48dp
 * button minus half this circle). Both need a double-tap so stray touches do nothing.
 */
@Composable
internal fun ScrollLockControls(
    playing: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val endPad = FlowTokens.Pad.FloatingCard + (FlowTokens.Comp.ButtonPrimary - FlowTokens.Comp.CircleButton) / 2
    val togglePlay = { if (playing) onPause() else onPlay() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(FlowTokens.Comp.Fab),
    ) {
        DoubleTapCircle(
            label = if (playing) "Pause" else "Play",
            onDoubleTap = togglePlay,
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Double-tap to pause" else "Double-tap to play",
            )
        }
        DoubleTapCircle(
            label = "Unlock scroll",
            onDoubleTap = onUnlock,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = endPad),
        ) {
            Icon(Icons.Filled.LockOpen, contentDescription = "Double-tap to unlock scroll")
        }
    }
}

@Composable
private fun DoubleTapCircle(
    label: String,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val action by rememberUpdatedState(onDoubleTap)
    Box(
        modifier = modifier
            .size(FlowTokens.Comp.CircleButton)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .semantics { onClick(label = label) { action(); true } }
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { action() }) },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
