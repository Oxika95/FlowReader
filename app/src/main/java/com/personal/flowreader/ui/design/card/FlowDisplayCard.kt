package com.personal.flowreader.ui.design.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.card.media.MediaStat
import com.personal.flowreader.ui.design.controls.FlowBadgeTone
import com.personal.flowreader.ui.design.controls.FlowMetaRow
import com.personal.flowreader.ui.design.surface.FlowCover
import com.personal.flowreader.ui.design.surface.FlowProgressBar
import com.personal.flowreader.ui.design.surface.FlowProgressTrack
import com.personal.flowreader.ui.design.surface.FlowSurface
import com.personal.flowreader.ui.design.surface.FlowSurfaceStyle
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Display card layouts. */
enum class FlowDisplayLayout {
    /** Cover strip at start, text column (list view, search results). */
    Row,
    /** Full cover with a dark title band (shelf grid). */
    Tile,
}

/** Small corner glyph on a display card ("Linked file"). */
data class FlowCornerBadge(val icon: ImageVector, val contentDescription: String)

/**
 * The one display card: a tappable item in a list or grid (books, plugin works).
 * Tap opens the item's media card; long press is the secondary action.
 * Empty slots (subtitle, badges, stats, progress) are hidden. See `docs/ui-system/cards.md`.
 */
@Composable
fun FlowDisplayCard(
    title: String,
    art: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    layout: FlowDisplayLayout = FlowDisplayLayout.Row,
    subtitle: String = "",
    badges: List<String> = emptyList(),
    stats: List<MediaStat> = emptyList(),
    progress: Float? = null,
    badgeTones: Map<String, FlowBadgeTone> = emptyMap(),
    corner: FlowCornerBadge? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    val sized = when (layout) {
        FlowDisplayLayout.Row -> modifier
            .fillMaxWidth()
            .then(
                if (badges.isEmpty() && stats.isEmpty()) {
                    Modifier.height(FlowTokens.Comp.ListRow)
                } else {
                    Modifier.heightIn(min = FlowTokens.Comp.ListRow)
                },
            )
        FlowDisplayLayout.Tile -> modifier
            .fillMaxWidth()
            .aspectRatio(FlowTokens.CoverAspect)
    }
    FlowSurface(
        modifier = sized,
        style = FlowSurfaceStyle.Flat,
        onClick = if (enabled) onClick else null,
        onLongClick = if (enabled) onLongClick else null,
        onClickLabel = "Open",
    ) {
        when (layout) {
            FlowDisplayLayout.Row -> DisplayRow(title, art, subtitle, badges, badgeTones, stats, progress)
            FlowDisplayLayout.Tile -> DisplayTile(title, art, progress)
        }
        if (corner != null) {
            Icon(
                corner.icon,
                contentDescription = corner.contentDescription,
                tint = if (layout == FlowDisplayLayout.Tile) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(FlowTokens.Space.S)
                    .size(FlowTokens.Icon.S),
            )
        }
        if (progress != null && layout == FlowDisplayLayout.Row) {
            FlowProgressBar(progress = progress, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun DisplayRow(
    title: String,
    art: ImageBitmap?,
    subtitle: String,
    badges: List<String>,
    badgeTones: Map<String, FlowBadgeTone>,
    stats: List<MediaStat>,
    progress: Float?,
) {
    val cardBg = MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FlowTokens.Comp.ListRow)
            .padding(bottom = if (progress != null) FlowTokens.Comp.ProgressBar else FlowTokens.Space.None),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .height(FlowTokens.Comp.ListRow)
                .aspectRatio(FlowTokens.CoverAspect),
        ) {
            FlowCover(art = art, title = title, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(colorStops = arrayOf(0.4f to Color.Transparent, 1f to cardBg))),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = FlowTokens.Space.M,
                    end = FlowTokens.Space.L,
                    top = FlowTokens.Space.S,
                    bottom = FlowTokens.Space.S,
                ),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.Hair),
        ) {
            Text(title, style = FlowType.rowTitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = FlowType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FlowMetaRow(badges, stats, Modifier.padding(top = FlowTokens.Space.XS), badgeTones = badgeTones)
        }
    }
}

@Composable
private fun DisplayTile(title: String, art: ImageBitmap?, progress: Float?) {
    Box(Modifier.fillMaxSize()) {
        FlowCover(art = art, title = title, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.ShelfGradientHeight)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, FlowTokens.CoverBandBlack))),
            )
            Column(Modifier.fillMaxWidth().background(FlowTokens.CoverBandBlack)) {
                Text(
                    title,
                    style = FlowType.tileTitle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(
                        start = FlowTokens.Space.M,
                        end = FlowTokens.Space.M,
                        top = FlowTokens.Space.Hair,
                        bottom = FlowTokens.Space.S,
                    ),
                )
                if (progress != null) FlowProgressBar(progress = progress, track = FlowProgressTrack.OnDark)
            }
        }
    }
}

/** Standard content padding for display card lists/grids (clears the FAB). */
fun flowDisplayListPadding(inset: Dp = FlowTokens.Pad.Screen, bottomExtra: Dp = FlowTokens.Space.None): PaddingValues =
    PaddingValues(start = inset, top = inset, end = inset, bottom = inset + FlowTokens.Comp.FabClearance + bottomExtra)

/** Vertical list of display cards with standard spacing and FAB clearance. */
@Composable
fun FlowDisplayList(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = flowDisplayListPadding(),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/** Adaptive grid of Tile display cards with standard spacing and FAB clearance. */
@Composable
fun FlowDisplayGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = flowDisplayListPadding(),
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(FlowTokens.Comp.GridMinCell),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M),
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

/** Centered empty-state message for a list area. */
@Composable
fun FlowEmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = FlowTokens.Pad.Screen),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = FlowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
