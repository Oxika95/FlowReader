package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.personal.flowreader.plugin.api.PluginCapability
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.design.card.FlowDisplayList
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
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

/** Search sub-tab: query field, work display cards, and "Load more" when the plugin reports more pages. */
@Composable
internal fun PluginSearchPane(
    ui: PluginTabUi,
    onQuery: (String) -> Unit,
    onSearch: (more: Boolean) -> Unit,
    onOpen: (PluginWork) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SearchField(
            ui = ui,
            onQuery = onQuery,
            onSearch = { onSearch(false) },
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.Screen, vertical = FlowTokens.Space.S),
        )
        FlowDisplayList(contentPadding = flowDisplayListPadding()) {
            items(ui.searchResults, key = { it.id }) { work ->
                WorkCard(work = work, enabled = !ui.busy, onOpen = onOpen)
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

/** FAB card: paste a link (resolveUrl) and, when supported, search the site. */
@Composable
internal fun AddFromSourceSheet(
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onUrl: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onOpen: (PluginWork) -> Unit,
) {
    val manifest = ui.manifest
    val canSearch = manifest.has(PluginCapability.Search)
    val canResolve = manifest.has(PluginCapability.ResolveUrl)
    FlowFullscreenCard(
        visible = ui.showAdd,
        onDismiss = onDismiss,
        title = "Add from ${manifest.name}",
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        FlowHint("Open a story to see its card, then add it to a list.")
        if (canSearch) SearchField(ui = ui, onQuery = onQuery, onSearch = onSearch)
        if (canResolve) {
            FlowTextField(
                value = ui.urlDraft,
                onValueChange = onUrl,
                label = "Story or chapter link",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onOpenUrl() }),
                trailingIcon = { TextButton(onClick = onOpenUrl) { Text("Go", style = FlowType.action) } },
            )
        }
        ui.error?.let { FlowHint(it, error = true) }
        ui.searchResults.forEach { work -> WorkCard(work = work, enabled = !ui.busy, onOpen = onOpen) }
    }
}
