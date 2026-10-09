package com.personal.flowreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.ChapterSource
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.LocusAnchor
import com.personal.flowreader.data.PositionDomain
import com.personal.flowreader.data.PositionSource
import com.personal.flowreader.data.ReaderRow
import com.personal.flowreader.data.ReadingPosition
import com.personal.flowreader.data.ReadingSession
import com.personal.flowreader.data.ReadingSessionId
import com.personal.flowreader.data.SentenceTable
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.tts.SynthDebugLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
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
    /** Block id â†’ ranges in filtered text tinted as replacements. */
    val replacedRangesByBlockId: Map<String, List<IntRange>> = emptyMap(),
    val filtersGlobal: List<FilterRule> = emptyList(),
    val filtersGroups: List<FilterRule> = emptyList(),
    val filtersLocal: List<FilterRule> = emptyList(),
    /** ToC row labels. */
    val tocTitles: List<String> = emptyList(),
    /** ToC row for the current locus highlight. */
    val tocIndex: Int = 0,
    /** Whole-book progress 0â€“1 at [locus]. */
    val fraction: Float = 0f,
    /** Indent level per ToC row (Queue: 0 = item, 1 = chapter inside it); empty = all 0. */
    val tocLevels: List<Int> = emptyList(),
    /** Reading the Queue as one document. */
    val queue: Boolean = false,
    /** Book under the locus (Queue: the current item); its card, cover and Local filters. */
    val currentBookId: String = "",
    /** Header line under the title, replacing the chapter name (Queue position). */
    val headerChapter: String? = null,
    /** Queue mode: the Contents card's items, live with the Queue table. */
    val queueItems: List<QueueTocItem> = emptyList(),
    /** Queue mode: the Queue row under the locus. */
    val currentQueId: String = "",
)

class ReaderViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val bookId: String = savedStateHandle["bookId"] ?: ""
    val queId: String? = savedStateHandle["queId"]
    val tts = flow.tts

    /** Opened from the Queue (or the now-playing card while the Queue plays): one stream of all rows. */
    private val isQueue = queId != null || bookId == QueueBook.ID

    /** Session, locator and TTS id: the book, or [QueueBook.ID] for the Queue stream. */
    private val sessionId = if (isQueue) QueueBook.ID else bookId

    private val _ui = MutableStateFlow(ReaderUi(queue = isQueue, currentBookId = bookId))
    val ui: StateFlow<ReaderUi> = _ui

    private val queue: ReaderQueueMode? = if (isQueue) ReaderQueueMode(flow, queId, _ui) else null
    private var book: ReaderBook? = null
    private var session: ReadingSession? = null

    /** Single book: its table and row (Library or plugin), set on load. Unused in Queue mode. */
    private var bookSessionId: ReadingSessionId? = null
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
        if (queue != null) {
            viewModelScope.launch { flow.queue.updates.collect { onQueueUpdate(queue, it) } }
        }
    }

    private suspend fun load() {
        if (queue != null) {
            loadQueue(queue)
            return
        }
        try {
            val row = withContext(Dispatchers.IO) { flow.catalog.readerRow(bookId) }
                ?: throw IllegalArgumentException("Book not found")
            val opened = withContext(Dispatchers.IO) { openBook(row) }
            if (opened.chapterCount == 0) throw IllegalStateException("No readable chapters")
            book = opened
            bookSessionId = ReadingSessionId(row.session.domain, row.session.key)
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            val local = TextFilters.decodeRules(row.localFilters.ifBlank { null })
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
                spoken != null -> show(Locus(spoken.chapterIndex, spoken.blockIndex, spoken.start), rules, reuse)
                else -> show(savedLocus(row.position, opened), rules, reuse, anchor = row.position.anchorText)
            }
            if (flow.pendingSharePlay == bookId) {
                flow.pendingSharePlay = null
                tts.play()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false)
        }
    }

    private suspend fun loadQueue(mode: ReaderQueueMode) {
        try {
            val start = mode.open(::savedLocus)
            book = mode.book?.reader
            show(start.locus, emptyList(), start.reuse, anchor = start.anchor)
            val playing = _ui.value.currentQueId
            if (playing.isNotEmpty() && flow.pendingSharePlay == playing) {
                flow.pendingSharePlay = null
                tts.play()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false, queue = true)
        }
    }

    /** The Queue table changed under the open stream: refresh in place or follow the rebuild. */
    private suspend fun onQueueUpdate(mode: ReaderQueueMode, update: QueueUpdate) {
        applyQueueAction(mode, mode.onUpdate(update, _ui.value.locus))
    }

    /** Contents opened: pick up Queue rows added, done or edited since the stream was built. */
    fun refreshQueue() {
        val mode = queue ?: return
        viewModelScope.launch {
            try {
                applyQueueAction(mode, mode.refresh(session, _ui.value.locus))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                SynthDebugLog.appendError("Queue: ${t.message ?: "could not refresh"}")
            }
        }
    }

    private suspend fun applyQueueAction(mode: ReaderQueueMode, action: ReaderQueueMode.UpdateAction?) {
        when (action) {
            null -> Unit
            ReaderQueueMode.UpdateAction.InPlace -> {
                book = mode.book?.reader
                _ui.update { it.copy(tocIndex = book?.tocRowOf(it.locus.chapterIndex) ?: it.tocIndex) }
            }
            is ReaderQueueMode.UpdateAction.Show -> {
                book = mode.book?.reader
                show(action.locus, emptyList(), action.session)
            }
            is ReaderQueueMode.UpdateAction.Reopen -> {
                book = mode.book?.reader
                show(action.locus, emptyList(), reuse = null)
            }
            ReaderQueueMode.UpdateAction.Empty -> {
                session?.let { releaseFocus(it) }
                session = null
                book = null
                _ui.value = ReaderUi(error = "Queue is empty", loading = false, queue = true)
            }
        }
    }

    fun reorderQueue(ids: List<String>) {
        val mode = queue ?: return
        viewModelScope.launch { mode.reorder(ids) }
    }

    fun removeQueue(ids: Set<String>) {
        val mode = queue ?: return
        viewModelScope.launch { mode.remove(ids) }
    }

    private suspend fun openBook(row: ReaderRow): ReaderBook =
        if (row.session.domain == PositionDomain.Plugin) {
            val story = flow.pluginBooks.openStory(bookId)
            ReaderBook.plugin(flow.pluginBooks, story)
        } else {
            ReaderBook.local(ChapterSource.open(flow.catalog.materialize(row), row.title))
        }

    /** Saved position in current chapter indices (the stored href wins over a drifted index). */
    private fun savedLocus(position: ReadingPosition, book: ReaderBook): Locus {
        val byHref = book.indexOfHref(position.chapterHref)
        val chapter = if (byHref >= 0) byHref else position.chapterIndex
        return Locus(chapter.coerceIn(0, book.chapterCount - 1), position.blockIndex, position.charOffset)
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
        val ref = queue?.ref
        val clip = tts.state.value
        val s = reuse?.takeIf { it.window.value.chapters.containsKey(start.chapterIndex) }
            ?: if (ref != null) {
                QueueStreams.open(flow, ref, start.chapterIndex)
            } else {
                val id = bookSessionId ?: return@withLock
                ReaderSessions.open(
                    bookId,
                    ReadingSessionId(id.domain, id.key),
                    b,
                    start.chapterIndex,
                    rules,
                    clip.clipTargetChars,
                    clip.clipFlexChars,
                )
            }
        val window = s.window.value
        val anchored = window.chapters[start.chapterIndex]
            ?.let { LocusAnchor.resolve(it.chapter, start, anchor) }
            ?: start
        val locus = ReaderSessions.readable(window, anchored)
        if (invalidateCache) tts.invalidateEdgeCache()
        session?.takeIf { it !== s }?.let { releaseFocus(it) }
        session = s
        viewport = null
        ref?.book?.let { queue?.applySegment(it.segmentIndexOf(locus.chapterIndex)) }
        _ui.value.let { tts.setSpeechFilters(it.filtersGlobal, it.filtersGroups, it.filtersLocal) }
        tts.attach(s, locus)
        ref?.let { flow.queue.attach(it, s) }
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

    private suspend fun reapply(
        global: List<FilterRule> = _ui.value.filtersGlobal,
        groups: List<FilterRule> = _ui.value.filtersGroups,
        local: List<FilterRule> = _ui.value.filtersLocal,
    ) {
        _ui.update { it.copy(filtersGlobal = global, filtersGroups = groups, filtersLocal = local) }
        queue?.let { mode ->
            mode.withFilters(_ui.value.locus, global, groups, local)
            book = mode.book?.reader
        }
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

    /** Local rules belong to the open row: the book, or the Queue item under the locus. */
    private suspend fun persistLocal(rules: List<FilterRule>) {
        val json = if (rules.isEmpty()) "" else TextFilters.encodeRules(rules)
        val (domain, key) = if (queue != null) {
            PositionDomain.Queue to _ui.value.currentQueId
        } else {
            val id = bookSessionId ?: return
            id.domain to id.key
        }
        if (key.isBlank()) return
        withContext(Dispatchers.IO) { flow.catalog.setLocalFilters(domain, key, json) }
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

    /** TTS moved on; follows only when playback reads this reader's own session. */
    fun onSpoken(locus: Locus) {
        val s = session ?: return
        if (tts.attachedSession() !== s) return
        onLocus(locus)
    }

    fun onLocus(locus: Locus) {
        positionMoved = true
        val b = book
        val window = session?.window?.value
        queue?.onLocus(locus)
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
        if (!ReaderProgressPolicy.mayPersist(sessionId, ui.doc != null, ui.loading, ui.error, positionMoved)) return
        val update = session?.position(locus, PositionSource.Reader) ?: return
        flow.progress.submit(update, flush = flush)
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
