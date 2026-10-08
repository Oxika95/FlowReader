package com.personal.flowreader.ui.design.card.media

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.FlowIcons
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.controls.FlowCircleButton
import com.personal.flowreader.ui.design.controls.FlowMetaRow
import com.personal.flowreader.ui.design.controls.FlowPrimaryButton
import com.personal.flowreader.ui.design.controls.FlowSecondaryButton
import com.personal.flowreader.ui.design.surface.FlowCover
import com.personal.flowreader.ui.design.surface.FlowProgressBar
import com.personal.flowreader.ui.design.surface.FlowProgressTrack
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Max secondary footer buttons per row before wrapping. */
private const val FooterRowMax = 3

/**
 * The file/media card: a Hero fullscreen card showing one work's metadata from a
 * [MediaCardModel]. Layout is fixed; empty slots are hidden.
 *
 * Cover band (blurred [art], gradient, dark band with title, subtitle, badges + stats, tags,
 * synopsis, progress, segments), top-end rail of circular actions, then status, links, error and
 * the footer (secondary/destructive rows, then the primary button).
 *
 * [onAction] receives every rail and footer tap; the caller routes by [MediaAction.id] /
 * [MediaAction.owner]. [onLongAction] receives long presses on footer actions marked
 * [MediaAction.longPress]; [onSegmentsLongPress] a long press on the segment strip. Links open
 * in the browser unless [onLink] is given.
 */
@Composable
fun FlowMediaCard(
    visible: Boolean,
    model: MediaCardModel?,
    art: ImageBitmap?,
    onDismiss: () -> Unit,
    onAction: (MediaAction) -> Unit,
    onLink: ((MediaLink) -> Unit)? = null,
    onLongAction: ((MediaAction) -> Unit)? = null,
    onSegmentsLongPress: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    FlowFullscreenCard(
        visible = visible && model != null,
        onDismiss = onDismiss,
        variant = FlowCardVariant.Hero,
        bodySpacing = Arrangement.Top,
    ) {
        val m = model ?: return@FlowFullscreenCard
        MediaCoverBand(m, art, onAction, onSegmentsLongPress)
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(FlowTokens.Space.S),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        ) {
            if (m.status.isNotBlank()) {
                Text(
                    m.status,
                    style = FlowType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = FlowTokens.Space.S),
                )
            }
            if (m.links.isNotEmpty()) {
                MediaLinks(m.links) { link ->
                    if (onLink != null) {
                        onLink(link)
                    } else {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link.url)))
                        }
                    }
                }
            }
            MediaFooter(m.footer, onAction, onLongAction)
            if (m.error.isNotBlank()) {
                Text(
                    m.error,
                    color = MaterialTheme.colorScheme.error,
                    style = FlowType.hint,
                    modifier = Modifier.padding(horizontal = FlowTokens.Space.S),
                )
            }
        }
    }
}

@Composable
private fun MediaCoverBand(
    m: MediaCardModel,
    art: ImageBitmap?,
    onAction: (MediaAction) -> Unit,
    onSegmentsLongPress: (() -> Unit)?,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(art?.let { it.width.toFloat() / it.height.toFloat() } ?: FlowTokens.CoverAspect),
    ) {
        val coverMaxHeight = maxHeight
        FlowCover(
            art = art,
            title = m.title,
            blur = true,
            contentScale = ContentScale.FillWidth,
            placeholderStyle = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.CoverGradientHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, FlowTokens.CoverBandBlack))),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FlowTokens.CoverBandBlack)
                    .padding(
                        start = FlowTokens.Space.L,
                        end = FlowTokens.Space.L,
                        top = FlowTokens.Space.XS,
                        bottom = FlowTokens.Space.M,
                    ),
            ) {
                MediaBandText(m, coverMaxHeight, onSegmentsLongPress)
            }
        }
        if (m.rail.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = FlowTokens.Space.M, end = FlowTokens.Space.M),
                verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                m.rail.forEach { action ->
                    FlowCircleButton(
                        icon = FlowIcons.forToken(action.icon),
                        contentDescription = action.label,
                        selected = action.kind == MediaActionKind.Toggle && action.on,
                        enabled = action.enabled,
                        onClick = { onAction(action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaBandText(m: MediaCardModel, coverMaxHeight: Dp, onSegmentsLongPress: (() -> Unit)?) {
    val muted = FlowTokens.CoverMutedWhite
    Text(
        m.title,
        style = FlowType.mediaTitle,
        color = MaterialTheme.colorScheme.primary,
        maxLines = m.titleMaxLines,
        overflow = TextOverflow.Ellipsis,
    )
    if (m.subtitle.isNotBlank()) {
        Text(
            m.subtitle,
            style = FlowType.body,
            color = Color.White,
            modifier = Modifier.padding(top = FlowTokens.Space.Hair),
        )
    }
    FlowMetaRow(
        badges = m.badges,
        stats = m.stats,
        onCover = true,
        modifier = Modifier.padding(top = FlowTokens.Space.XS),
    )
    if (m.tags.isNotEmpty()) {
        Text(
            m.tags.joinToString(" · "),
            style = FlowType.label,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = FlowTokens.Space.XS),
        )
    }
    if (m.synopsis.isNotBlank()) {
        Text(
            m.synopsis,
            style = FlowType.hint,
            color = muted,
            overflow = TextOverflow.Ellipsis,
            maxLines = 20,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = (coverMaxHeight * 0.42f).coerceAtLeast(FlowTokens.Comp.ButtonSecondary))
                .padding(top = FlowTokens.Space.XS, bottom = FlowTokens.Space.XS),
        )
    }
    m.progress?.let { p ->
        FlowProgressBar(
            progress = p,
            track = FlowProgressTrack.OnDark,
            roundBottom = false,
            modifier = Modifier.padding(top = FlowTokens.Space.M),
        )
    }
    m.segments?.takeIf { it.count > 0 }?.let { seg ->
        val holdable = if (onSegmentsLongPress != null) {
            val haptic = LocalHapticFeedback.current
            val longPress by rememberUpdatedState(onSegmentsLongPress)
            Modifier
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress()
                        },
                    )
                }
                .semantics {
                    onLongClick(label = m.segmentsLongPressLabel.ifBlank { null }) {
                        longPress()
                        true
                    }
                }
        } else {
            Modifier
        }
        // Vertical padding inside the gesture area widens the touch target of the thin strip.
        Box(
            Modifier
                .fillMaxWidth()
                .then(holdable)
                .padding(vertical = FlowTokens.Space.S),
        ) {
            MediaSegmentStrip(
                seg,
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.Comp.SegmentStrip),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaLinks(links: List<MediaLink>, onLink: (MediaLink) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.XS)) {
        links.forEach { link ->
            TextButton(onClick = { onLink(link) }) {
                Text(link.label, style = FlowType.action, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MediaFooter(
    actions: List<MediaAction>,
    onAction: (MediaAction) -> Unit,
    onLongAction: ((MediaAction) -> Unit)?,
) {
    val secondary = actions.filter { it.kind != MediaActionKind.Primary }
    val primary = actions.firstOrNull { it.kind == MediaActionKind.Primary }
    if (secondary.isEmpty() && primary == null) return
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FlowTokens.Space.XS),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        ) {
            secondary.chunked(FooterRowMax).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
                ) {
                    row.forEach { a ->
                        FlowSecondaryButton(
                            label = a.label,
                            onClick = { onAction(a) },
                            enabled = a.enabled,
                            destructive = a.kind == MediaActionKind.Destructive,
                            onLongClick = onLongAction?.takeIf { a.longPress }?.let { handler -> { handler(a) } },
                        )
                    }
                }
            }
            primary?.let { a -> FlowPrimaryButton(a.label, { onAction(a) }, enabled = a.enabled) }
        }
    }
}
