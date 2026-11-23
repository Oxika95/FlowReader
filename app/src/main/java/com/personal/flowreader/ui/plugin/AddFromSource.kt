package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.design.card.FlowDisplayList
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

@Composable
private fun SearchField(ui: PluginTabUi, onQuery: (String) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    FlowTextField(
        value = ui.query,
        onValueChange = onQuery,
        modifier = modifier,
        label = "Search ${ui.manifest.name}",
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        trailingIcon = {
            IconButton(onClick = onSearch) { Icon(Icons.Filled.Search, contentDescription = "Search") }
        },
    )
}

/**
 * Search section: optional discover shelf ([PluginList.openInSearch]) with dual dropdowns, then
 * title-query mode via "Search {name} >". Without an openInSearch list, query mode only.
 */
@Composable
internal fun PluginSearchPane(
    pluginId: String,
    ui: PluginTabUi,
    onQuery: (String) -> Unit,
    onSearch: (more: Boolean) -> Unit,
    onOpen: (PluginWork) -> Unit,
    onSearchQueryMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    bearerToken: String? = null,
    bottomInset: Dp = FlowTokens.Space.None,
) {
    val shelf = ui.manifest.searchShelfList()
    if (shelf != null && !ui.searchQueryMode) {
        PluginShelfPane(
            pluginId = pluginId,
            listId = shelf.id,
            bearerToken = bearerToken,
            onOpenBook = onOpen,
            modifier = modifier,
            bottomInset = bottomInset,
            belowToolbar = {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Pad.Screen)
                        .padding(top = FlowTokens.Space.None, bottom = FlowTokens.Space.XS),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowTextAction(
                        "Search ${ui.manifest.name} >",
                        { onSearchQueryMode(true) },
                        enabled = !ui.busy,
                        compact = true,
                    )
                }
            },
        )
        return
    }
    Column(modifier.fillMaxSize()) {
        if (shelf != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlowTokens.Pad.Screen, vertical = FlowTokens.Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSearchQueryMode(false) }, enabled = !ui.busy) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to lists")
                }
                Text("Search ${ui.manifest.name}", style = FlowType.rowTitle, modifier = Modifier.weight(1f))
            }
        }
        SearchField(
            ui = ui,
            onQuery = onQuery,
            onSearch = { onSearch(false) },
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.Screen, vertical = FlowTokens.Space.S),
        )
        FlowDisplayList(
            modifier = Modifier.weight(1f),
            contentPadding = flowDisplayListPadding(bottomExtra = bottomInset),
        ) {
            items(ui.searchResults, key = { it.id }) { work ->
                WorkCard(work = work, enabled = !ui.busy, onOpen = onOpen, bearerToken = bearerToken)
            }
            if (ui.searchHasMore) {
                item {
                    TextButton(
                        onClick = { onSearch(true) },
                        enabled = !ui.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Load more", style = FlowType.action) }
                }
            }
        }
    }
}

/** FAB card for resolveUrl-only plugins: paste a story or chapter link. */
@Composable
internal fun AddFromSourceSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onUrl: (String) -> Unit,
    onOpenUrl: () -> Unit,
) {
    val manifest = ui.manifest
    if (!manifest.has(PluginCapability.ResolveUrl)) return
    FlowFullscreenCard(
        visible = ui.showAdd,
        onDismiss = onDismiss,
        title = "Add from ${manifest.name}",
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        FlowHint("Open a story to see its card, then add it to a list.")
        FlowTextField(
            value = ui.urlDraft,
            onValueChange = onUrl,
            label = "Story or chapter link",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onOpenUrl() }),
            trailingIcon = { TextButton(onClick = onOpenUrl) { Text("Go", style = FlowType.action) } },
        )
        ui.error?.let { FlowHint(it, error = true) }
    }
}
