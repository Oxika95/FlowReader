package com.personal.flowreader.ui.design.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.FlowIcons
import com.personal.flowreader.ui.design.card.media.MediaStat
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Badge color: neutral, or a status (e.g. paid vs lapsed membership). */
enum class FlowBadgeTone { Neutral, Positive, Negative }

/** Short status pill. [onCover] for dark cover bands, otherwise page colors. */
@Composable
fun FlowBadge(
    label: String,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
    tone: FlowBadgeTone = FlowBadgeTone.Neutral,
) {
    val toneColor = when (tone) {
        FlowBadgeTone.Neutral -> null
        FlowBadgeTone.Positive -> FlowTokens.MatchGreen
        FlowBadgeTone.Negative -> MaterialTheme.colorScheme.error
    }
    val fg = when {
        toneColor != null -> toneColor
        onCover -> Color.White
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val bg = when {
        toneColor != null -> toneColor.copy(alpha = FlowTokens.Alpha.TrackOnDark)
        onCover -> Color.White.copy(alpha = FlowTokens.Alpha.Track)
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val ring = if (onCover) Color.White.copy(alpha = FlowTokens.Alpha.CircleRingOnCover) else Color.Transparent
    Text(
        label,
        style = FlowType.label,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(bg, FlowTokens.Shape.Pill)
            .border(FlowTokens.Stroke.Hairline, ring, FlowTokens.Shape.Pill)
            .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.Hair),
    )
}

/** Icon + value stat. Label is used as the icon's content description. */
@Composable
fun FlowStat(stat: MediaStat, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.XS),
    ) {
        if (stat.icon != null) {
            Icon(
                FlowIcons.forToken(stat.icon),
                contentDescription = stat.label.ifBlank { null },
                tint = color,
                modifier = Modifier.size(FlowTokens.Icon.S),
            )
        }
        Text(stat.value, style = FlowType.hint, color = color, maxLines = 1)
    }
}

/** Wrapping row of badges then stats, as shown on media cards and display cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowMetaRow(
    badges: List<String>,
    stats: List<MediaStat>,
    modifier: Modifier = Modifier,
    onCover: Boolean = false,
    badgeTones: Map<String, FlowBadgeTone> = emptyMap(),
) {
    if (badges.isEmpty() && stats.isEmpty()) return
    val statColor = if (onCover) FlowTokens.CoverMutedWhite else MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.XS),
    ) {
        badges.forEach {
            FlowBadge(
                it,
                Modifier.align(Alignment.CenterVertically),
                onCover = onCover,
                tone = badgeTones[it] ?: FlowBadgeTone.Neutral,
            )
        }
        stats.forEach { FlowStat(it, statColor, Modifier.align(Alignment.CenterVertically)) }
    }
}
