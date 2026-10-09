package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.PluginSource
import com.personal.flowreader.plugin.api.PluginBrowsePage
import com.personal.flowreader.plugin.api.PluginBrowseTab
import com.personal.flowreader.plugin.api.PluginWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One loaded tab of an entry page; [posts] is the local story when the tab is a story pane. */
data class PluginBrowseTabUi(
    val page: PluginBrowsePage,
    val pageNo: Int = 1,
    val posts: PluginStory? = null,
)

/** Open entry page of a browse list (e.g. one creator). Tabs load on first view and stay cached while open. */
data class PluginBrowseUi(
    val listId: String,
    val row: PluginWork,
    val tabs: List<PluginBrowseTab> = emptyList(),
    val tab: String = "",
    val content: Map<String, PluginBrowseTabUi> = emptyMap(),
    val loading: Boolean = true,
    val postsOldestFirst: Boolean = false,
) {
    val current: PluginBrowseTabUi? get() = content[tab]
}

/** Loads tabs and pages of [PluginBrowseUi] into the plugin tab state; one request in flight at a time. */
internal class PluginBrowseController(
    private val scope: CoroutineScope,
    private val ui: MutableStateFlow<PluginTabUi>,
    private val source: () -> PluginSource,
    private val fail: (Throwable, String) -> Unit,
    /** Local story for a story-pane tab (fetched once if not stored yet). */
    private val loadPosts: suspend (workId: String) -> PluginStory,
) {
    private var job: Job? = null

    fun open(listId: String, row: PluginWork) {
        job?.cancel()
        ui.update { it.copy(browse = PluginBrowseUi(listId, row), error = null) }
        load(row.id, tab = "", page = 1, sort = "", append = false)
    }

    fun selectTab(tab: String) {
        val b = ui.value.browse ?: return
        if (tab == b.tab) return
        job?.cancel()
        ui.update { it.copy(browse = it.browse?.copy(tab = tab, loading = false), error = null) }
        if (b.content[tab] == null) load(b.row.id, tab, page = 1, sort = "", append = false)
    }

    fun setSort(sort: String) {
        val b = ui.value.browse ?: return
        if (sort == b.current?.page?.sort && !b.loading) return
        load(b.row.id, b.tab, page = 1, sort = sort, append = false)
    }

    fun more() {
        val b = ui.value.browse ?: return
        val current = b.current ?: return
        if (!current.page.hasMore || b.loading) return
        load(b.row.id, b.tab, page = current.pageNo + 1, sort = current.page.sort, append = true)
    }

    fun setPostsOrder(oldestFirst: Boolean) = ui.update { it.copy(browse = it.browse?.copy(postsOldestFirst = oldestFirst)) }

    /** Swap in a fresher copy of a story shown in a story-pane tab (after downloads or a ToC refresh). */
    fun updatePosts(story: PluginStory) = ui.update { s ->
        val b = s.browse ?: return@update s
        if (b.content.values.none { it.posts?.bookId == story.bookId }) return@update s
        s.copy(
            browse = b.copy(
                content = b.content.mapValues { (_, t) -> if (t.posts?.bookId == story.bookId) t.copy(posts = story) else t },
            ),
        )
    }

    fun postsBookIds(): Set<String> =
        ui.value.browse?.content?.values?.mapNotNull { it.posts?.bookId }?.toSet().orEmpty()

    /** Returns the entry that was open, if any. */
    fun close(): PluginBrowseUi? {
        job?.cancel()
        val open = ui.value.browse
        ui.update { it.copy(browse = null) }
        return open
    }

    private fun load(id: String, tab: String, page: Int, sort: String, append: Boolean) {
        job?.cancel()
        ui.update { it.copy(browse = it.browse?.copy(loading = true), error = null) }
        job = scope.launch {
            try {
                val (result, posts) = withContext(Dispatchers.IO) {
                    val result = source().browse(id, page, sort, tab)
                    result to result.storyId.takeIf { it.isNotBlank() }?.let { loadPosts(it) }
                }
                ui.update { s ->
                    val b = s.browse?.takeIf { it.row.id == id && it.tab == tab } ?: return@update s
                    val key = result.tab.ifBlank { tab }
                    val previous = b.content[key]
                    val merged = if (append && previous != null) {
                        result.copy(items = (previous.page.items + result.items).distinctBy { it.id })
                    } else {
                        result
                    }
                    s.copy(
                        browse = b.copy(
                            tabs = result.tabs.ifEmpty { b.tabs },
                            tab = key,
                            content = b.content + (key to PluginBrowseTabUi(merged, page, posts ?: previous?.posts)),
                            loading = false,
                        ),
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                ui.update { it.copy(browse = it.browse?.copy(loading = false)) }
                fail(t, "Could not load ${ui.value.browse?.row?.title ?: "page"}")
            }
        }
    }
}
