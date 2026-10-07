package com.personal.flowreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.BookFiltersEntity
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.LocusAnchor
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.SentenceTable
import com.personal.flowreader.data.TextFilters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderUi(
    val title: String = "",
    /** Loaded chapters only; chapters outside the reading window have no blocks. */
    val doc: BookDoc? = null,
    /** Sentences of the loaded chapters (stable indices, see [SentenceTable]). */
    val sentences: SentenceTable = SentenceTable.EMPTY,
    val locus: Locus = Locus(),
    val error: String? = null,
    val loading: Boolean = true,
    /** Absolute path of the materialized book file (for cover underlay). */
    val storedPath: String = "",
    /** Block id → ranges in filtered text tinted as replacements. */
    val replacedRangesByBlockId: Map<String, List<IntRange>> = emptyMap(),
    val filtersGlobal: List<FilterRule> = emptyList(),
    val filtersGroups: List<FilterRule> = emptyList(),
    val filtersLocal: List<FilterRule> = emptyList(),
    /** ToC row labels. */
    val tocTitles: List<String> = emptyList(),
    /** ToC row for the current locus highlight. */
    val tocIndex: Int = 0,
    /** Whole-book progress 0–1 at [locus]. */
    val fraction: Float = 0f,
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

    private var book: ReaderBook? = null
    private var session: ReadingSession? = null
    private var windowJob: Job? = null
    private val showLock = Mutex()
    private var viewport: IntRange? = null

    /**
     * True once the user or TTS moved the position after load. The loaded (possibly clamped)
     * locus is never written back, so an interrupted or failed load cannot overwrite progress.
     */
    private var positionMoved = false

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val row = flow.db.progress().get(bookId)
                ?: throw IllegalArgumentException("Book not found")
            val opened = withContext(Dispatchers.IO) { openBook(row) }
            if (opened.chapterCount == 0) throw IllegalStateException("No readable chapters")
            book = opened
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            val local = loadLocalFilters()
            _ui.update {
                it.copy(
                    title = opened.title,
                    storedPath = row.storedPath,
                    filtersGlobal = global,
                    filtersGroups = groups,
                    filtersLocal = local,
                    tocTitles = opened.toc.map { (_, label) -> label },
                )
            }
            val rules = TextFilters.merge(global, groups, local)
            val reuse = tts.attachedSession()
                ?.takeIf { it.bookId == bookId && it.contentKey == ReaderSessions.contentKey(rules) }
            // Playback may have continued after the reader closed; it is newer than the saved row.
            val spoken = tts.state.value.takeIf { it.bookId == bookId }?.sentence
            when {
                // Auto-advance starts each Que document from the beginning so a shared
                // file that was already finished does not immediately re-emit bookFinished.
                autoPlay -> show(Locus(), rules, reuse = null)
                spoken != null -> show(Locus(spoken.chapterIndex, spoken.blockIndex, spoken.start), rules, reuse)
                else -> show(savedLocus(row, opened), rules, reuse, anchor = row.anchorText)
            }
            if (autoPlay && _ui.value.sentences.isNotEmpty()) tts.play()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false)
        }
    }

    private suspend fun openBook(row: ProgressEntity): ReaderBook =
        if (flow.pluginBooks.isPluginBook(bookId)) {
            val story = flow.pluginBooks.openStory(bookId)
            ReaderBook.plugin(flow.pluginBooks, story)
        } else {
            ReaderBook.local(ChapterSource.open(flow.catalog.materialize(row), row.title))
        }

    /** Saved position in current chapter indices; pre-chapter-source rows are mapped once. */
    private suspend fun savedLocus(row: ProgressEntity, book: ReaderBook): Locus {
        val stored = Locus(row.chapterIndex, row.blockIndex, row.charOffset)
        val locus = if (row.locusVersion < ProgressEntity.LOCUS_CURRENT) {
            val mapped = withContext(Dispatchers.IO) { book.legacyLocus(stored) }
            flow.db.progress().upsert(
                row.copy(
                    chapterIndex = mapped.chapterIndex,
                    blockIndex = mapped.blockIndex,
                    charOffset = mapped.charOffset,
                    locusVersion = ProgressEntity.LOCUS_CURRENT,
                    chapterHref = book.href(mapped.chapterIndex),
                ),
            )
            mapped
        } else {
            val byHref = book.indexOfHref(row.chapterHref)
            if (byHref >= 0) stored.copy(chapterIndex = byHref) else stored
        }
        return locus.copy(chapterIndex = locus.chapterIndex.coerceIn(0, book.chapterCount - 1))
    }

    /**
     * Show [start]: reuse [reuse] when it already holds that chapter, else open a session around
     * it. TTS attaches to the same session; [anchor] re-finds a saved position whose indices drifted.
     */
    private suspend fun show(
        start: Locus,
        rules: List<FilterRule>,
        reuse: ReadingSession?,
        anchor: String = "",
        invalidateCache: Boolean = false,
    ) = showLock.withLock {
        val b = book ?: return@withLock
        val clip = tts.state.value
        val s = reuse?.takeIf { it.window.value.chapters.containsKey(start.chapterIndex) }
            ?: ReaderSessions.open(bookId, b, start.chapterIndex, rules, clip.clipTargetChars, clip.clipFlexChars)
        val window = s.window.value
        val anchored = window.chapters[start.chapterIndex]
            ?.let { LocusAnchor.resolve(it.chapter, start, anchor) }
            ?: start
        val locus = ReaderSessions.readable(window, anchored)
        if (invalidateCache) tts.invalidateEdgeCache()
        session?.takeIf { it !== s }?.let { releaseFocus(it) }
        session = s
        viewport = null
        flow.progress.setLocator(bookId, ReaderSessions.locator(bookId, b, s))
        _ui.value.let { tts.setSpeechFilters(it.filtersGlobal, it.filtersGroups, it.filtersLocal) }
        tts.attach(s, locus)
        s.setFocus(ReadingSession.FOCUS_READER, locus.chapterIndex)
        s.setFocus(ReadingSession.FOCUS_READER_END, locus.chapterIndex)
        _ui.update {
            it.copy(
                doc = window.doc,
                sentences = window.table,
                replacedRangesByBlockId = window.replacedRangesByBlockId,
                locus = locus,
                loading = false,
                error = null,
                tocIndex = b.tocRowOf(locus.chapterIndex),
                fraction = ReaderSessions.fraction(b, window, locus),
            )
        }
        collectWindow(s)
        viewModelScope.launch {
            s.loadAdjacent(locus.chapterIndex + 1, clip.clipTargetChars, clip.clipFlexChars)
            s.loadAdjacent(locus.chapterIndex - 1, clip.clipTargetChars, clip.clipFlexChars)
        }
    }

    private fun collectWindow(s: ReadingSession) {
        windowJob?.cancel()
        windowJob = viewModelScope.launch {
            s.window.collect { w ->
                _ui.update {
                    it.copy(doc = w.doc, sentences = w.table, replacedRangesByBlockId = w.replacedRangesByBlockId)
                }
            }
        }
    }

    /**
     * Chapters [first]..[last] are on screen: keep them and one neighbour each side loaded,
     * drop the rest (unless TTS is reading there).
     */
    fun onViewport(first: Int, last: Int) {
        val s = session ?: return
        val visible = first..last
        if (visible == viewport) return
        viewport = visible
        viewModelScope.launch {
            s.setFocus(ReadingSession.FOCUS_READER, first)
            s.setFocus(ReadingSession.FOCUS_READER_END, last)
            val clip = tts.state.value
            val range = s.window.value.loaded ?: return@launch
            if (last >= range.last) s.loadAdjacent(range.last + 1, clip.clipTargetChars, clip.clipFlexChars)
            if (first <= range.first) s.loadAdjacent(range.first - 1, clip.clipTargetChars, clip.clipFlexChars)
        }
    }

    private fun releaseFocus(s: ReadingSession) {
        flow.appScope.launch {
            s.setFocus(ReadingSession.FOCUS_READER, null)
            s.setFocus(ReadingSession.FOCUS_READER_END, null)
        }
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

    private suspend fun reapply(
        global: List<FilterRule> = _ui.value.filtersGlobal,
        groups: List<FilterRule> = _ui.value.filtersGroups,
        local: List<FilterRule> = _ui.value.filtersLocal,
    ) {
        _ui.update { it.copy(filtersGlobal = global, filtersGroups = groups, filtersLocal = local) }
        if (book == null) return
        show(_ui.value.locus, TextFilters.merge(global, groups, local), reuse = null, invalidateCache = true)
    }

    private fun editFilters(scope: FilterScope, edit: (List<FilterRule>) -> List<FilterRule>) {
        viewModelScope.launch {
            val ui = _ui.value
            when (scope) {
                FilterScope.Global -> edit(ui.filtersGlobal).let { flow.settings.setGlobalFilters(it); reapply(global = it) }
                FilterScope.Groups -> edit(ui.filtersGroups).let { flow.settings.setGroupFilters(it); reapply(groups = it) }
                FilterScope.Local -> edit(ui.filtersLocal).let { persistLocal(it); reapply(local = it) }
            }
        }
    }

    fun addFilter(scope: FilterScope, rule: FilterRule) =
        editFilters(scope) { it + rule.copy(order = nextOrder(it)) }

    fun updateFilter(scope: FilterScope, rule: FilterRule) =
        editFilters(scope) { list -> list.map { if (it.id == rule.id) rule else it } }

    fun setFilterEnabled(scope: FilterScope, id: String, enabled: Boolean) =
        editFilters(scope) { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    fun deleteFilters(scope: FilterScope, ids: Set<String>) =
        editFilters(scope) { list -> list.filterNot { it.id in ids } }

    fun reorderFilters(scope: FilterScope, ids: List<String>) =
        editFilters(scope) { list -> TextFilters.reorder(list, ids) }

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
        val locus = _ui.value.locus
        val chapter = session?.window?.value?.chapters?.get(locus.chapterIndex)?.raw ?: return ""
        return chapter.blocks.getOrNull(locus.blockIndex)?.text.orEmpty()
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

    /** Jump to ToC row [row]; chapters outside the window open a new one around them. */
    suspend fun jumpToChapter(row: Int) {
        val b = book ?: return
        val chapter = (b.toc.getOrNull(row)?.first ?: row).coerceIn(0, b.chapterCount - 1)
        val loaded = session?.window?.value?.chapters?.get(chapter)
        if (loaded != null && loaded.chapter.blocks.isNotEmpty()) {
            jumpTo(Locus(chapter, 0, 0))
            return
        }
        val ui = _ui.value
        show(Locus(chapter, 0, 0), TextFilters.merge(ui.filtersGlobal, ui.filtersGroups, ui.filtersLocal), session)
        positionMoved = true
        persist(_ui.value.locus)
    }

    fun jumpTo(locus: Locus) {
        onLocus(locus)
        tts.jumpTo(locus)
    }

    fun onLocus(locus: Locus) {
        positionMoved = true
        val b = book
        val window = session?.window?.value
        _ui.update {
            it.copy(
                locus = locus,
                tocIndex = b?.tocRowOf(locus.chapterIndex) ?: it.tocIndex,
                fraction = if (b != null && window != null) ReaderSessions.fraction(b, window, locus) else it.fraction,
            )
        }
        persist(locus)
    }

    /** Flush current UI locus using app scope so it survives ViewModel teardown. */
    fun persistNow() {
        persist(_ui.value.locus, flush = true)
    }

    private fun persist(locus: Locus, flush: Boolean = false) {
        val ui = _ui.value
        if (!ReaderProgressPolicy.mayPersist(bookId, ui.doc != null, ui.loading, ui.error, positionMoved)) return
        flow.progress.submit(flow.progress.locate(bookId, locus, System.currentTimeMillis()), flush = flush)
    }

    /** Playback keeps running after the reader closes; the Library now-playing card controls it. */
    override fun onCleared() {
        persistNow()
        session?.let { releaseFocus(it) }
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
