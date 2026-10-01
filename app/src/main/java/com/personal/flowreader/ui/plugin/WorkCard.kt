package com.personal.flowreader.ui.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.common.rememberRemoteCover
import com.personal.flowreader.ui.design.card.FlowDisplayCard
import com.personal.flowreader.ui.design.card.FlowDisplayLayout
import com.personal.flowreader.ui.design.card.media.MediaStat
import com.personal.flowreader.ui.theme.FlowTokens

/** Remote search / list result as a display card. Tap opens the story's media card. */
@Composable
fun WorkCard(
    work: PluginWork,
    enabled: Boolean,
    onOpen: (PluginWork) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cover by rememberRemoteCover(work.cover, FlowTokens.CoverEdge.Row)
    FlowDisplayCard(
        title = work.title,
        art = cover,
        onClick = { onOpen(work) },
        modifier = modifier,
        layout = FlowDisplayLayout.Row,
        subtitle = listOf(work.author, work.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
        badges = work.badges,
        stats = work.stats.map { MediaStat(it.icon, it.value, it.label) },
        enabled = enabled,
    )
}
