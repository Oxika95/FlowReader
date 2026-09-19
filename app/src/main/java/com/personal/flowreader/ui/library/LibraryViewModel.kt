package com.personal.flowreader.ui.library

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.LibraryTab
import com.personal.flowreader.data.LibraryViewMode
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.QueEntry
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.ui.reader.FilterPreviewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUi(
    val books: List<ProgressEntity> = emptyList(),
    val que: List<QueEntry> = emptyList(),
    val viewMode: LibraryViewMode = LibraryViewMode.List,
    val tab: LibraryTab = LibraryTab.Files,
    val filtersGlobal: List<FilterRule> = emptyList(),
    val filtersGroups: List<FilterRule> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    /** When set, MainActivity should navigate to the reader for this bookId. */
    val pendingOpenBookId: String? = null,
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val tts = flow.tts
    private val _ui = MutableStateFlow(LibraryUi())
    val ui: StateFlow<LibraryUi> = _ui

    init {
        viewModelScope.launch {
            val mode = flow.settings.libraryViewModeOnce()
            val tab = flow.settings.libraryTabOnce()
            _ui.value = _ui.value.copy(viewMode = mode, tab = tab)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val books = withContext(Dispatchers.IO) { flow.catalog.list() }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            _ui.value = _ui.value.copy(
                books = books,
                que = que,
                filtersGlobal = global,
                filtersGroups = groups,
            )
        }
    }

    fun setTab(tab: LibraryTab) {
        _ui.value = _ui.value.copy(tab = tab)
        viewModelScope.launch { flow.settings.setLibraryTab(tab) }
    }

    fun setViewMode(mode: LibraryViewMode) {
        _ui.value = _ui.value.copy(viewMode = mode)
        viewModelScope.launch { flow.settings.setLibraryViewMode(mode) }
    }

    /**
     * Handle ACTION_SEND / ACTION_VIEW from the share sheet or "Open with".
     * @return true if the intent was consumed (async work may still be running)
     */
    fun handleIncomingIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return false
                openExternalUri(uri)
                return true
            }
            Intent.ACTION_SEND -> {
                val component = intent.component?.className.orEmpty()
                // Aliases: text → library or Que. Direct SEND with a stream → open file.
                @Suppress("DEPRECATION")
                val stream = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null && !component.contains("ShareQueAlias") &&
                    !component.contains("ShareLibraryAlias")
                ) {
                    openExternalUri(stream)
                    return true
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
                if (text.isEmpty()) {
                    if (stream != null) {
                        openExternalUri(stream)
                        return true
                    }
                    _ui.value = _ui.value.copy(error = "Nothing to share")
                    return true
                }
                // File-like SEND of plain text without alias: treat as open if it looks like a URI stream only
                val toQue = component.endsWith("ShareQueAlias")
                ingestSharedText(text, toQue = toQue)
                return true
            }
            else -> return false
        }
    }

    /** Import or link an external book URI, then request the reader to open it. */
    fun openExternalUri(uri: Uri) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val row = withContext(Dispatchers.IO) {
                    try {
                        flow.catalog.add(uri, BookSource.Linked)
                    } catch (_: Throwable) {
                        flow.catalog.add(uri, BookSource.Imported)
                    }
                }
                val books = withContext(Dispatchers.IO) { flow.catalog.list() }
                flow.settings.setLibraryTab(LibraryTab.Files)
                _ui.value = _ui.value.copy(
                    books = books,
                    tab = LibraryTab.Files,
                    busy = false,
                    message = "Opened ${row.title}",
                    pendingOpenBookId = row.bookId,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not open file",
                )
            }
        }
    }

    fun consumePendingOpen() {
        _ui.value = _ui.value.copy(pendingOpenBookId = null)
    }

    fun ingestSharedText(text: String, toQue: Boolean) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val result = withContext(Dispatchers.IO) {
                    flow.catalog.addText(
                        text = text,
                        inLibrary = !toQue,
                        enqueue = toQue,
                    )
                }
                val books = withContext(Dispatchers.IO) { flow.catalog.list() }
                val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
                val tab = if (toQue) LibraryTab.Que else LibraryTab.Files
                flow.settings.setLibraryTab(tab)
                val msg = if (toQue) {
                    "Queued ${result.progress.title}"
                } else {
                    "Added ${result.progress.title}"
                }
                _ui.value = _ui.value.copy(
                    books = books,
                    que = que,
                    tab = tab,
                    busy = false,
                    message = msg,
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not import shared text",
                )
            }
        }
    }

    fun removeQue(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { flow.catalog.removeQue(id) }
            val que = withContext(Dispatchers.IO) { flow.catalog.listQue() }
            val books = withContext(Dispatchers.IO) { flow.catalog.list() }
            _ui.value = _ui.value.copy(que = que, books = books)
        }
    }

    fun addFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal + rule.copy(order = nextOrder(_ui.value.filtersGlobal))
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups + rule.copy(order = nextOrder(_ui.value.filtersGroups))
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun updateFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.map { if (it.id == rule.id) rule else it }
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map { if (it.id == rule.id) rule else it }
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun setFilterEnabled(scope: FilterScope, id: String, enabled: Boolean) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistGlobal(next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistGroups(next)
                }
                FilterScope.Local -> Unit
            }
        }
    }

    fun deleteFilter(scope: FilterScope, id: String) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> persistGlobal(_ui.value.filtersGlobal.filterNot { it.id == id })
                FilterScope.Groups -> persistGroups(_ui.value.filtersGroups.filterNot { it.id == id })
                FilterScope.Local -> Unit
            }
        }
    }

    fun previewApply(
        sample: String,
        draft: FilterRule,
        scope: FilterScope,
        mode: FilterPreviewMode,
    ): FilterApplyResult {
        val rules = when (mode) {
            FilterPreviewMode.Original -> emptyList()
            FilterPreviewMode.ThisRule -> listOf(draft.copy(enabled = true, order = 0))
            FilterPreviewMode.AllEnabled -> {
                val global = _ui.value.filtersGlobal
                val groups = _ui.value.filtersGroups
                val withDraft: (List<FilterRule>) -> List<FilterRule> = { list ->
                    list.map { if (it.id == draft.id) draft else it }.let { mapped ->
                        if (mapped.any { it.id == draft.id }) mapped else mapped + draft
                    }
                }
                when (scope) {
                    FilterScope.Global -> TextFilters.merge(withDraft(global), groups, emptyList())
                    FilterScope.Groups -> TextFilters.merge(global, withDraft(groups), emptyList())
                    FilterScope.Local -> TextFilters.merge(global, groups, listOf(draft))
                }
            }
        }
        return TextFilters.apply(sample, rules)
    }

    private suspend fun persistGlobal(rules: List<FilterRule>) {
        flow.settings.setGlobalFilters(rules)
        _ui.value = _ui.value.copy(filtersGlobal = rules)
    }

    private suspend fun persistGroups(rules: List<FilterRule>) {
        flow.settings.setGroupFilters(rules)
        _ui.value = _ui.value.copy(filtersGroups = rules)
    }

    private fun nextOrder(rules: List<FilterRule>): Int =
        (rules.maxOfOrNull { it.order } ?: -1) + 1

    fun add(uri: Uri, source: BookSource) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null, message = null)
            try {
                val row = withContext(Dispatchers.IO) { flow.catalog.add(uri, source) }
                val books = withContext(Dispatchers.IO) { flow.catalog.list() }
                val verb = if (source == BookSource.Linked) "Linked" else "Imported"
                _ui.value = _ui.value.copy(
                    books = books,
                    busy = false,
                    message = "$verb ${row.title}",
                )
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    busy = false,
                    error = t.message ?: "Could not add file",
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.value = _ui.value.copy(message = null)
    }

    fun consumeError() {
        _ui.value = _ui.value.copy(error = null)
    }
}
