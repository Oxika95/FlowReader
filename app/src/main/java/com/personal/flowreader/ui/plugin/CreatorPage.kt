package com.personal.flowreader.ui.plugin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.plugin.api.PluginBrowsePage
import com.personal.flowreader.plugin.api.PluginBrowseSection
import com.personal.flowreader.plugin.api.PluginLink
import com.personal.flowreader.plugin.api.PluginSort
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.common.rememberRemoteCover
import com.personal.flowreader.ui.design.card.FlowDisplayCard
import com.personal.flowreader.ui.design.card.FlowDisplayList
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.card.media.MediaStat
import com.personal.flowreader.ui.design.controls.FlowChoiceChips
import com.personal.flowreader.ui.design.controls.FlowCollapsible
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Rows of a browse list (e.g. creators); tap opens the entry page instead of the reader. */
@Composable
internal fun BrowseListPane(
    ui: PluginTabUi,
    listId: String,
    onOpen: (PluginWork) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = FlowTokens.Space.None,
) {
    val rows = ui.listRows.values.toList()
    if (rows.isEmpty()) {
        val title = ui.manifest.list(listId)?.title ?: listId
        val hint = when {
            ui.manifest.auth != null && !ui.session.loggedIn -> "Sign in to import $title."
            else -> "Nothing in $title yet. Sync it from the account card."
        }
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { FlowHint(hint) }
        return
    }
    FlowDisplayList(modifier = modifier, contentPadding = flowDisplayListPadding(bottomExtra = bottomInset)) {
        items(groupedRows(rows), key = { it.key() }) { entry ->
            when (entry) {
                is GroupedRow.Header -> GroupHeader(entry.group, entry.first)
                is GroupedRow.Empty -> FlowHint(entry.text)
                is GroupedRow.Row -> WorkCard(work = entry.work, enabled = !ui.busy, onOpen = onOpen)
            }
        }
    }
}

private fun GroupedRow.key(): String = when (this) {
    is GroupedRow.Header -> "g:$group"
    is GroupedRow.Empty -> "e:$group"
    is GroupedRow.Row -> "w:${work.id}"
}

/** Callbacks of [CreatorPageOverlay]. */
internal class CreatorPageActions(
    val onDismiss: () -> Unit,
    val onTab: (String) -> Unit,
    val onSort: (String) -> Unit,
    val onMore: () -> Unit,
    val onPostsOrder: (oldestFirst: Boolean) -> Unit,
    /** Item tab row: open its story card. */
    val onOpen: (PluginWork) -> Unit,
    /** Story-pane tab: open that story's card positioned on the tapped chapter. */
    val onOpenStory: (workId: String, chapterIndex: Int) -> Unit,
)

/**
 * Page for one browse-list entry: plugin tabs (pinned), sort chips below them, then the tab's
 * content: text (About), story chapters with lock / downloaded icons (Posts), or story items.
 */
@Composable
internal fun CreatorPageOverlay(ui: PluginTabUi, actions: CreatorPageActions) {
    val browse = ui.browse
    FlowFullscreenCard(
        visible = browse != null,
        onDismiss = actions.onDismiss,
        title = browse?.row?.title,
        scrollable = false,
        bodyPadding = PaddingValues(bottom = FlowTokens.Space.S),
        tabs = browse?.tabs?.takeIf { it.size > 1 }?.let { tabs ->
            {
                FlowTabBar(
                    tabs = tabs.map { t ->
                        FlowTab.text(t.label, selected = t.id == browse.tab, onClick = { actions.onTab(t.id) }, key = t.id)
                    },
                    level = FlowTabLevel.Secondary,
                )
            }
        },
    ) {
        if (browse == null) return@FlowFullscreenCard
        val current = browse.current
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            contentPadding = PaddingValues(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.S),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        ) {
            val page = current?.page
            val posts = current?.posts
            when {
                posts != null -> item(key = "sort") {
                    val options = listOf(PluginSort("new", "Newest first"), PluginSort("old", "Oldest first"))
                    FlowChoiceChips(
                        options = options,
                        selected = options[if (browse.postsOldestFirst) 1 else 0],
                        optionLabel = { it.label },
                        onSelect = { actions.onPostsOrder(it.id == "old") },
                    )
                }
                page != null && page.sorts.size > 1 -> item(key = "sort") {
                    FlowChoiceChips(
                        options = page.sorts,
                        selected = page.sorts.firstOrNull { it.id == page.sort } ?: page.sorts.first(),
                        optionLabel = { it.label },
                        onSelect = { actions.onSort(it.id) },
                        enabled = !browse.loading,
                    )
                }
            }
            if (browse.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            ui.error?.let { item(key = "error") { FlowHint(it, error = true) } }
            when {
                page == null -> Unit
                posts != null -> postsPane(posts, browse.postsOldestFirst, page.storyId, actions, enabled = !ui.busy)
                page.isText -> item(key = "text") { TextPane(browse.row, page) }
                else -> itemsPane(ui, page, browse.loading, actions)
            }
        }
    }
}

private fun LazyListScope.itemsPane(ui: PluginTabUi, page: PluginBrowsePage, loading: Boolean, actions: CreatorPageActions) {
    if (!loading && page.items.isEmpty() && page.groups.isEmpty() && ui.error == null) {
        item(key = "empty") { FlowHint("Nothing to show yet.") }
    }
    items(groupedRows(page.items, page.groups, complete = !page.hasMore), key = { it.key() }) { entry ->
        when (entry) {
            is GroupedRow.Header -> GroupHeader(entry.group, entry.first)
            is GroupedRow.Empty -> FlowHint(entry.text)
            is GroupedRow.Row -> {
                val work = entry.work
                val listed = ui.storyLists[work.id]
                WorkCard(
                    work = if (listed == null) work else work.copy(badges = listOf("In $listed") + work.badges),
                    enabled = !ui.busy,
                    onOpen = actions.onOpen,
                )
            }
        }
    }
    if (page.hasMore) {
        item(key = "more") {
            TextButton(onClick = actions.onMore, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text("Load more", style = FlowType.action)
            }
        }
    }
}

private fun LazyListScope.postsPane(
    posts: PluginStory,
    oldestFirst: Boolean,
    workId: String,
    actions: CreatorPageActions,
    enabled: Boolean,
) {
    val order = posts.toc.indices.toList().let { if (oldestFirst) it else it.asReversed() }
    items(order, key = { "p:$it" }) { index ->
        val ref = posts.toc[index]
        PostRow(
            title = ref.title,
            locked = ref.locked,
            downloaded = index in posts.cachedIndices,
            enabled = enabled,
            onClick = { actions.onOpenStory(workId, index) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun PostRow(title: String, locked: Boolean, downloaded: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = FlowTokens.Space.M),
    ) {
        Text(
            title,
            style = FlowType.body,
            color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (locked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = "Locked",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(FlowTokens.Icon.S),
            )
        }
        if (downloaded) {
            Icon(
                Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(FlowTokens.Icon.S),
            )
        }
    }
}

@Composable
private fun TextPane(row: PluginWork, page: PluginBrowsePage) {
    val context = LocalContext.current
    val open = { url: String -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    val cover by rememberRemoteCover(page.cover.ifBlank { row.cover }, FlowTokens.CoverEdge.Row)
    Column(verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M)) {
        FlowDisplayCard(
            title = row.title,
            art = cover,
            onClick = { (page.links.firstOrNull()?.url ?: row.url).takeIf { it.isNotBlank() }?.let(open) },
            subtitle = row.subtitle,
            badges = page.badges,
            stats = page.stats.map { MediaStat(it.icon, it.value, it.label) },
            badgeTones = flowTones(page.badgeTones),
        )
        Paragraphs(page.text)
        Links(page.links, open)
        sectionGroups(page.sections).forEachIndexed { i, group ->
            when {
                group.collapsed -> {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    FlowCollapsible(group.heading) { Sections(group.sections, open) }
                }
                group.heading.isNotBlank() -> {
                    GroupHeader(group.heading, first = false)
                    Sections(group.sections, open)
                }
                else -> {
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Sections(group.sections, open)
                }
            }
        }
    }
}

/** Sections under one heading (or before the first heading); [collapsed] comes from the heading section. */
internal data class SectionGroup(
    val heading: String,
    val collapsed: Boolean,
    val sections: List<PluginBrowseSection>,
)

internal fun sectionGroups(sections: List<PluginBrowseSection>): List<SectionGroup> {
    val out = ArrayList<SectionGroup>()
    sections.forEach { s ->
        val last = out.lastOrNull()
        if (last == null || s.heading.isNotBlank()) {
            out += SectionGroup(s.heading, s.collapsed, listOf(s))
        } else {
            out[out.lastIndex] = last.copy(sections = last.sections + s)
        }
    }
    return out
}

@Composable
private fun Sections(sections: List<PluginBrowseSection>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.M)) {
        sections.forEachIndexed { i, section ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (section.title.isNotBlank()) {
                Text(section.title, style = FlowType.rowTitle, color = MaterialTheme.colorScheme.onBackground)
            }
            Paragraphs(section.text)
            Links(section.links, open)
        }
    }
}

@Composable
private fun Paragraphs(text: String) {
    text.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.forEach {
        Text(it, style = FlowType.body, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Links(links: List<PluginLink>, open: (String) -> Unit) {
    if (links.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S)) {
        links.forEach { link -> FlowTextAction(link.label, { open(link.url) }) }
    }
}
