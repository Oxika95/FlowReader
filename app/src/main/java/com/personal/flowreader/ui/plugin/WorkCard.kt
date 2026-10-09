package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.personal.flowreader.plugin.api.PluginBrowseGroup
import com.personal.flowreader.plugin.api.PluginTone
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.common.rememberRemoteCover
import com.personal.flowreader.ui.design.card.FlowDisplayCard
import com.personal.flowreader.ui.design.card.FlowDisplayLayout
import com.personal.flowreader.ui.design.card.media.MediaStat
import com.personal.flowreader.ui.design.controls.FlowBadgeTone
import com.personal.flowreader.ui.design.controls.FlowSection
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
        badgeTones = flowTones(work.badgeTones),
    )
}

internal fun flowTones(tones: Map<String, PluginTone>): Map<String, FlowBadgeTone> = tones.mapValues {
    when (it.value) {
        PluginTone.Positive -> FlowBadgeTone.Positive
        PluginTone.Negative -> FlowBadgeTone.Negative
    }
}

/** Row list entry: a group header before the first row of each group (when rows have 2+ groups). */
internal sealed interface GroupedRow {
    data class Header(val group: String, val first: Boolean) : GroupedRow
    data class Row(val work: PluginWork) : GroupedRow
    data class Empty(val group: String, val text: String) : GroupedRow
}

/**
 * Rows with group headers. [declared] groups come first in their order, each with a header and an
 * [GroupedRow.Empty] when no row has it; while more pages may follow (![complete]) empty declared
 * groups are left out. Undeclared groups follow in row order.
 */
internal fun groupedRows(
    works: List<PluginWork>,
    declared: List<PluginBrowseGroup> = emptyList(),
    complete: Boolean = true,
): List<GroupedRow> {
    if (declared.isEmpty()) {
        if (works.map { it.group }.distinct().size < 2) return works.map { GroupedRow.Row(it) }
        val out = ArrayList<GroupedRow>()
        works.forEachIndexed { i, w ->
            if (i == 0 || works[i - 1].group != w.group) out += GroupedRow.Header(w.group, first = i == 0)
            out += GroupedRow.Row(w)
        }
        return out
    }
    val out = ArrayList<GroupedRow>()
    val byGroup = works.groupBy { it.group }
    declared.forEach { g ->
        val rows = byGroup[g.title].orEmpty()
        if (rows.isEmpty() && !complete) return@forEach
        out += GroupedRow.Header(g.title, first = out.isEmpty())
        if (rows.isEmpty()) out += GroupedRow.Empty(g.title, g.empty.ifBlank { "Nothing here yet." })
        rows.forEach { out += GroupedRow.Row(it) }
    }
    val titles = declared.map { it.title }.toSet()
    byGroup.filterKeys { it !in titles }.forEach { (group, rows) ->
        out += GroupedRow.Header(group, first = out.isEmpty())
        rows.forEach { out += GroupedRow.Row(it) }
    }
    return out
}

/** Divider (except above the first group) and the group's title. */
@Composable
internal fun GroupHeader(group: String, first: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (!first) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (group.isNotBlank()) FlowSection(group)
    }
}
