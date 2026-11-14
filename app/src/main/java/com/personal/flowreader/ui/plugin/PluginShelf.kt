package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.api.PluginBrowseTab
import com.personal.flowreader.plugin.api.PluginSort
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.ui.design.card.FlowDisplayList
import com.personal.flowreader.ui.design.card.FlowEmptyState
import com.personal.flowreader.ui.design.card.flowDisplayListPadding
import com.personal.flowreader.ui.design.controls.FlowDropdownRow
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.layer.FlowPullRefresh
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ShelfRequest(
    val tab: String = "library",
    val sort: String = "",
    val page: Int = 1,
    val drillId: String? = null,
    val drillTitle: String = "",
    val generation: Int = 0,
)

private data class ShelfState(
    val tabs: List<PluginBrowseTab> = emptyList(),
    val sorts: List<PluginSort> = emptyList(),
    val items: List<PluginWork> = emptyList(),
    val hasMore: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val descending: Boolean = false,
    val request: ShelfRequest = ShelfRequest(),
)

/**
 * A browse list with [com.personal.flowreader.plugin.api.PluginList.shelf]: the tab is the shelf.
 * `browse` tabs are the views and `sorts` is the menu. A row id of `series:`, `collection:`,
 * or `author:` replaces the shelf with that group's books.
 */
@Composable
internal fun PluginShelfPane(
    pluginId: String,
    listId: String,
    bearerToken: String?,
    onOpenBook: (PluginWork) -> Unit,
    revision: Int = 0,
    modifier: Modifier = Modifier,
    bottomInset: Dp = FlowTokens.Space.None,
) {
    val app = LocalContext.current.applicationContext as FlowApp
    var state by remember(pluginId, listId) { mutableStateOf(ShelfState()) }
    val request = state.request
    LaunchedEffect(pluginId, listId, request) {
        val append = request.page > 1
        state = state.copy(loading = true, error = null)
        val result = try {
            Result.success(
                withContext(Dispatchers.IO) {
                    app.pluginManager.source(pluginId).browse(
                        id = request.drillId ?: listId,
                        page = request.page,
                        sort = request.sort,
                        tab = request.tab,
                    )
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        val page = result.getOrNull()
        state = if (page != null) {
            state.copy(
                tabs = page.tabs.ifEmpty { state.tabs },
                sorts = page.sorts.ifEmpty { state.sorts },
                items = if (append) (state.items + page.items).distinctBy { it.id } else page.items,
                hasMore = page.hasMore,
                loading = false,
                refreshing = false,
                error = null,
                descending = page.descending ?: state.descending,
            )
        } else {
            state.copy(
                loading = false,
                refreshing = false,
                error = result.exceptionOrNull()?.message ?: "Could not load the shelf",
            )
        }
    }
    fun go(next: ShelfRequest, refresh: Boolean = false) {
        state = state.copy(request = next, refreshing = refresh)
    }
    LaunchedEffect(revision) {
        if (revision == 0) return@LaunchedEffect
        go(request.copy(page = 1, generation = request.generation + 1), refresh = true)
    }
    Column(modifier.fillMaxSize()) {
        ShelfToolbar(
            state = state,
            onView = { tab -> go(ShelfRequest(tab = tab, generation = request.generation + 1)) },
            onSort = { sort ->
                val current = bareSort(request.sort).ifBlank { state.sorts.firstOrNull()?.id.orEmpty() }
                val flipping = current.isNotEmpty() && current == sort.id
                val next = if (!flipping) sort.id else if (state.descending) "+${sort.id}" else "!${sort.id}"
                go(request.copy(sort = next, page = 1, generation = request.generation + 1))
            },
            onBack = {
                go(request.copy(drillId = null, drillTitle = "", page = 1, sort = "", generation = request.generation + 1))
            },
        )
        if (state.loading && state.items.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let {
            FlowHint(it, error = true, modifier = Modifier.padding(horizontal = FlowTokens.Pad.Screen))
        }
        FlowPullRefresh(
            refreshing = state.refreshing,
            onRefresh = { go(request.copy(page = 1, generation = request.generation + 1), refresh = true) },
            modifier = Modifier.weight(1f),
        ) {
            if (!state.loading && state.items.isEmpty() && state.error == null) {
                FlowEmptyState("Nothing to show yet.", Modifier.fillMaxSize())
            } else {
                FlowDisplayList(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = flowDisplayListPadding(bottomExtra = bottomInset),
                ) {
                    items(groupedRows(state.items), key = { it.key() }) { entry ->
                        when (entry) {
                            is GroupedRow.Header -> GroupHeader(entry.group, entry.first)
                            is GroupedRow.Empty -> FlowHint(entry.text)
                            is GroupedRow.Row -> WorkCard(
                                work = entry.work,
                                enabled = !state.loading,
                                bearerToken = bearerToken,
                                onOpen = { work ->
                                    if (isShelfGroup(work.id)) {
                                        go(
                                            request.copy(
                                                drillId = work.id,
                                                drillTitle = work.title,
                                                page = 1,
                                                sort = "",
                                                generation = request.generation + 1,
                                            ),
                                        )
                                    } else {
                                        onOpenBook(work)
                                    }
                                },
                            )
                        }
                    }
                    if (state.hasMore) {
                        item(key = "more") {
                            TextButton(
                                onClick = { go(request.copy(page = request.page + 1)) },
                                enabled = !state.loading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Load more", style = FlowType.action)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfToolbar(
    state: ShelfState,
    onView: (String) -> Unit,
    onSort: (PluginSort) -> Unit,
    onBack: () -> Unit,
) {
    val request = state.request
    val sorts = state.sorts
    val selectedSort = sorts.firstOrNull { it.id == bareSort(request.sort) } ?: sorts.firstOrNull()
    val selectedView = state.tabs.firstOrNull { it.id == request.tab } ?: state.tabs.firstOrNull()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Pad.Screen, vertical = FlowTokens.Space.S),
    ) {
        if (request.drillId != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back", style = FlowType.action) }
                Text(request.drillTitle, style = FlowType.rowTitle, modifier = Modifier.weight(1f))
            }
        }
        if (selectedView != null || selectedSort != null) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectedView != null) {
                    FlowDropdownRow(
                        selected = selectedView,
                        options = state.tabs,
                        optionLabel = { tab -> tab.label.ifBlank { tab.id } },
                        onSelect = { onView(it.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (selectedSort != null) {
                    SortMenu(selectedSort, sorts, state.descending, onSort, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    selected: PluginSort,
    sorts: List<PluginSort>,
    descending: Boolean,
    onSort: (PluginSort) -> Unit,
    modifier: Modifier,
) {
    FlowDropdownRow(
        selected = selected,
        options = sorts,
        optionLabel = { option ->
            if (option.id == selected.id) option.label + if (descending) " ↓" else " ↑" else option.label
        },
        onSelect = onSort,
        modifier = modifier,
    )
}

private fun bareSort(sort: String): String = sort.removePrefix("!").removePrefix("+")

private fun isShelfGroup(id: String): Boolean {
    val kind = id.substringBefore(':')
    return kind == "series" || kind == "collection" || kind == "author"
}
