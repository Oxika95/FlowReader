package com.personal.flowreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.BookFiltersEntity
import com.personal.flowreader.data.EpubIngest
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.TxtIngest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUi(
    val title: String = "",
    val doc: BookDoc? = null,
    val locus: Locus = Locus(),
    val error: String? = null,
    val loading: Boolean = true,
    /** Block id → ranges in filtered text tinted as replacements. */
    val replacedRangesByBlockId: Map<String, List<IntRange>> = emptyMap(),
    val filtersGlobal: List<FilterRule> = emptyList(),
    val filtersGroups: List<FilterRule> = emptyList(),
    val filtersLocal: List<FilterRule> = emptyList(),
)

class ReaderViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val bookId: String = savedStateHandle["bookId"] ?: ""
    val queId: String? = savedStateHandle["queId"]
    private val autoPlay: Boolean = savedStateHandle.get<String>("autoPlay") == "1"
    val tts = flow.tts

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui

    private var rawDoc: BookDoc? = null
    /** When true, [onCleared] skips [TtsController.pause] so Que handoff can keep audio seamless. */
    @Volatile
    var suppressPauseOnClear: Boolean = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val row = flow.db.progress().get(bookId)
                ?: throw IllegalArgumentException("Book not found")
            val doc = withContext(Dispatchers.IO) {
                val file = flow.catalog.materialize(row)
                if (file.extension.equals("txt", true)) TxtIngest.read(file) else EpubIngest.read(file)
            }
            rawDoc = doc
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            val local = loadLocalFilters()
            // Auto-advance starts each Que document from the beginning so a shared
            // file that was already finished does not immediately re-emit bookFinished.
            val locus = if (autoPlay) {
                Locus()
            } else {
                Locus(row.chapterIndex, row.blockIndex, row.charOffset)
            }
            applyFilters(global, groups, local, locus, invalidateCache = false)
            if (autoPlay && !sentencesEmpty()) {
                tts.play()
            }
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false)
        }
    }

    private fun sentencesEmpty(): Boolean {
        val doc = _ui.value.doc ?: return true
        return doc.chapters.all { it.blocks.isEmpty() }
    }

    /**
     * Mark the current Que row done and return the next unfinished item, if any.
     * Call only when this reader was opened with a [queId].
     */
    suspend fun finishQueAndNext(): Pair<String, String>? {
        val id = queId ?: return null
        return withContext(Dispatchers.IO) {
            val current = flow.catalog.getQue(id) ?: return@withContext null
            flow.catalog.markQueDone(id)
            val next = flow.catalog.nextUndoneQue(current.sortOrder) ?: return@withContext null
            next.progress.bookId to next.item.id
        }
    }

    private suspend fun loadLocalFilters(): List<FilterRule> {
        val json = flow.db.bookFilters().get(bookId)?.rulesJson
        return TextFilters.decodeRules(json)
    }

    private fun applyFilters(
        global: List<FilterRule>,
        groups: List<FilterRule>,
        local: List<FilterRule>,
        locus: Locus = _ui.value.locus,
        invalidateCache: Boolean,
    ) {
        val raw = rawDoc ?: return
        val merged = TextFilters.merge(global, groups, local)
        val filtered = TextFilters.apply(raw, merged)
        val clamped = clampLocus(locus, filtered.doc)
        if (invalidateCache) tts.invalidateEdgeCache()
        tts.attach(bookId, filtered.doc, clamped)
        _ui.value = ReaderUi(
            title = filtered.doc.title,
            doc = filtered.doc,
            locus = clamped,
            loading = false,
            replacedRangesByBlockId = filtered.replacedRangesByBlockId,
            filtersGlobal = global,
            filtersGroups = groups,
            filtersLocal = local,
        )
    }

    private fun reapply(
        global: List<FilterRule> = _ui.value.filtersGlobal,
        groups: List<FilterRule> = _ui.value.filtersGroups,
        local: List<FilterRule> = _ui.value.filtersLocal,
    ) {
        applyFilters(global, groups, local, invalidateCache = true)
    }

    private fun clampLocus(locus: Locus, doc: BookDoc): Locus {
        if (doc.chapters.isEmpty()) return Locus()
        val ci = locus.chapterIndex.coerceIn(0, doc.chapters.lastIndex)
        val blocks = doc.chapters[ci].blocks
        if (blocks.isEmpty()) return Locus(ci, 0, 0)
        val bi = locus.blockIndex.coerceIn(0, blocks.lastIndex)
        val len = blocks[bi].text.length
        return Locus(ci, bi, locus.charOffset.coerceIn(0, len))
    }

    fun addFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal + rule.copy(order = nextOrder(_ui.value.filtersGlobal))
                    persistGlobal(next)
                    reapply(global = next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups + rule.copy(order = nextOrder(_ui.value.filtersGroups))
                    persistGroups(next)
                    reapply(groups = next)
                }
                FilterScope.Local -> {
                    val next = _ui.value.filtersLocal + rule.copy(order = nextOrder(_ui.value.filtersLocal))
                    persistLocal(next)
                    reapply(local = next)
                }
            }
        }
    }

    fun updateFilter(scope: FilterScope, rule: FilterRule) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.map { if (it.id == rule.id) rule else it }
                    persistGlobal(next)
                    reapply(global = next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map { if (it.id == rule.id) rule else it }
                    persistGroups(next)
                    reapply(groups = next)
                }
                FilterScope.Local -> {
                    val next = _ui.value.filtersLocal.map { if (it.id == rule.id) rule else it }
                    persistLocal(next)
                    reapply(local = next)
                }
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
                    reapply(global = next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistGroups(next)
                    reapply(groups = next)
                }
                FilterScope.Local -> {
                    val next = _ui.value.filtersLocal.map {
                        if (it.id == id) it.copy(enabled = enabled) else it
                    }
                    persistLocal(next)
                    reapply(local = next)
                }
            }
        }
    }

    fun deleteFilter(scope: FilterScope, id: String) {
        viewModelScope.launch {
            when (scope) {
                FilterScope.Global -> {
                    val next = _ui.value.filtersGlobal.filterNot { it.id == id }
                    persistGlobal(next)
                    reapply(global = next)
                }
                FilterScope.Groups -> {
                    val next = _ui.value.filtersGroups.filterNot { it.id == id }
                    persistGroups(next)
                    reapply(groups = next)
                }
                FilterScope.Local -> {
                    val next = _ui.value.filtersLocal.filterNot { it.id == id }
                    persistLocal(next)
                    reapply(local = next)
                }
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
                val local = _ui.value.filtersLocal
                val withDraft: (List<FilterRule>) -> List<FilterRule> = { list ->
                    list.map { if (it.id == draft.id) draft else it }.let { mapped ->
                        if (mapped.any { it.id == draft.id }) mapped else mapped + draft
                    }
                }
                when (scope) {
                    FilterScope.Global -> TextFilters.merge(withDraft(global), groups, local)
                    FilterScope.Groups -> TextFilters.merge(global, withDraft(groups), local)
                    FilterScope.Local -> TextFilters.merge(global, groups, withDraft(local))
                }
            }
        }
        return TextFilters.apply(sample, rules)
    }

    fun currentBlockSample(): String {
        val doc = rawDoc ?: _ui.value.doc ?: return ""
        val locus = _ui.value.locus
        val chapter = doc.chapters.getOrNull(locus.chapterIndex) ?: return ""
        return chapter.blocks.getOrNull(locus.blockIndex)?.text.orEmpty()
    }

    private suspend fun persistGlobal(rules: List<FilterRule>) {
        flow.settings.setGlobalFilters(rules)
    }

    private suspend fun persistGroups(rules: List<FilterRule>) {
        flow.settings.setGroupFilters(rules)
    }

    private suspend fun persistLocal(rules: List<FilterRule>) {
        if (bookId.isBlank()) return
        if (rules.isEmpty()) {
            flow.db.bookFilters().delete(bookId)
        } else {
            flow.db.bookFilters().upsert(
                BookFiltersEntity(bookId = bookId, rulesJson = TextFilters.encodeRules(rules)),
            )
        }
    }

    private fun nextOrder(rules: List<FilterRule>): Int =
        (rules.maxOfOrNull { it.order } ?: -1) + 1

    fun jumpToChapter(chapterIndex: Int) {
        val doc = _ui.value.doc ?: return
        val ci = chapterIndex.coerceIn(0, doc.chapters.lastIndex)
        jumpTo(Locus(ci, 0, 0))
    }

    fun jumpTo(locus: Locus) {
        onLocus(locus)
        tts.jumpTo(locus)
    }

    fun onLocus(locus: Locus) {
        _ui.value = _ui.value.copy(locus = locus)
        persist(locus)
    }

    /** Flush current UI locus using app scope so it survives ViewModel teardown. */
    fun persistNow() {
        persist(_ui.value.locus)
    }

    private fun persist(locus: Locus) {
        val id = bookId
        if (id.isBlank()) return
        flow.appScope.launch {
            val row = flow.db.progress().get(id) ?: return@launch
            flow.db.progress().upsert(
                row.copy(
                    chapterIndex = locus.chapterIndex,
                    blockIndex = locus.blockIndex,
                    charOffset = locus.charOffset,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onCleared() {
        persistNow()
        if (!suppressPauseOnClear) {
            tts.pause()
        }
        super.onCleared()
    }
}

enum class FilterPreviewMode {
    Original,
    ThisRule,
    AllEnabled,
    ;

    val label: String
        get() = when (this) {
            Original -> "Original"
            ThisRule -> "This rule"
            AllEnabled -> "All enabled"
        }
}
