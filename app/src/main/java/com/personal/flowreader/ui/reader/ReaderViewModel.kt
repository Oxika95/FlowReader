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
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadHtml
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadPlugin
import com.personal.flowreader.library.plugin.royalroad.RoyalRoadReadSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ReaderUi(
    val title: String = "",
    val doc: BookDoc? = null,
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
    /** Full ToC titles (RR uses saved session ToC; otherwise [doc] chapters). */
    val tocTitles: List<String> = emptyList(),
    /** Absolute ToC index for the current locus highlight. */
    val tocIndex: Int = 0,
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
    private var storedPath: String = ""
    private var rrSession: RoyalRoadReadSession? = null
    private val appendMutex = Mutex()
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
            storedPath = row.storedPath
            val doc = withContext(Dispatchers.IO) {
                if (RoyalRoadHtml.isPluginBookId(bookId)) {
                    loadRoyalRoad(row.sourceUri)
                } else {
                    val file = flow.catalog.materialize(row)
                    if (file.extension.equals("txt", true)) TxtIngest.read(file) else EpubIngest.read(file)
                }
            }
            rawDoc = doc
            val global = flow.settings.globalFiltersOnce()
            val groups = flow.settings.groupFiltersOnce()
            val local = loadLocalFilters()
            // Auto-advance starts each Que document from the beginning so a shared
            // file that was already finished does not immediately re-emit bookFinished.
            val locus = if (autoPlay) {
                Locus()
            } else if (rrSession != null) {
                // Progress stores absolute ToC chapter; BookDoc chapters are relative to startIndex.
                val rel = (row.chapterIndex - rrSession!!.startIndex).coerceAtLeast(0)
                Locus(rel, row.blockIndex, row.charOffset)
            } else {
                Locus(row.chapterIndex, row.blockIndex, row.charOffset)
            }
            applyFilters(global, groups, local, locus, invalidateCache = false)
            if (RoyalRoadHtml.isPluginBookId(bookId)) {
                tts.setMoreProvider(bookId) { appendRoyalRoadChapter() }
                viewModelScope.launch { appendRoyalRoadChapter() }
            }
            if (autoPlay && !sentencesEmpty()) {
                tts.play()
            }
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false)
        }
    }

    private suspend fun loadRoyalRoad(sourceUri: String): BookDoc {
        val session = runCatching { flow.royalRoad.resumeRead(bookId) }.getOrElse {
            val url = sourceUri.takeIf { it.isNotBlank() }
                ?: throw it
            val detail = flow.royalRoad.loadWork(url)
            flow.royalRoad.startReading(detail, 0)
        }
        rrSession = session
        persistPluginSnapshot(session)
        session.toc.getOrNull(session.startIndex)?.url?.let { url ->
            runCatching { flow.royalRoad.syncProgress(session.bookId, url) }
        }
        return BookDoc(session.title, session.chapters)
    }

    private suspend fun persistPluginSnapshot(session: RoyalRoadReadSession) {
        // Keep book.txt to the stream window so it does not grow without bound.
        val window = (session.keepBehind + 1 + session.prefetchAhead).coerceAtLeast(1)
        val slice = session.chapters.takeLast(window)
        val text = slice.joinToString("\n\n") { chapter ->
            buildString {
                if (chapter.title.isNotBlank()) {
                    append(chapter.title)
                    append("\n\n")
                }
                append(chapter.blocks.joinToString("\n\n") { it.text })
            }
        }
        flow.catalog.upsertPluginBook(
            bookId = session.bookId,
            title = session.title,
            sourceUri = session.fictionUrl,
            sourceKind = RoyalRoadPlugin.ID,
            text = text.ifBlank { session.title },
        )
    }

    suspend fun appendRoyalRoadChapter(): Boolean = appendMutex.withLock {
        val session = rrSession ?: return false
        val next = withContext(Dispatchers.IO) { flow.royalRoad.appendNext(session) } ?: return false
        rrSession = next
        val addedRaw = next.chapters.last()
        val raw = rawDoc ?: return false
        rawDoc = raw.copy(chapters = raw.chapters + addedRaw)
        val merged = TextFilters.merge(
            _ui.value.filtersGlobal,
            _ui.value.filtersGroups,
            _ui.value.filtersLocal,
        )
        val filtered = TextFilters.applyVisual(rawDoc!!, merged)
        val addedFiltered = filtered.doc.chapters.takeLast(1)
        tts.extend(addedFiltered)
        val tocTitles = tocTitlesFor(next, filtered.doc)
        _ui.value = _ui.value.copy(
            doc = filtered.doc,
            replacedRangesByBlockId = filtered.replacedRangesByBlockId,
            tocTitles = tocTitles,
            tocIndex = tocIndexFor(next, _ui.value.locus, tocTitles.size),
        )
        withContext(Dispatchers.IO) {
            persistPluginSnapshot(next)
            next.toc.getOrNull(next.loadedThrough)?.url?.let { url ->
                flow.royalRoad.syncProgress(next.bookId, url)
            }
            runCatching {
                flow.royalRoad.maintainChapterCache(next.bookId, next.loadedThrough)
            }
        }
        true
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
        val filtered = TextFilters.applyVisual(raw, merged)
        val clamped = clampLocus(locus, filtered.doc)
        if (invalidateCache) tts.invalidateEdgeCache()
        tts.attach(bookId, filtered.doc, clamped, speechFilters = merged.filter { it.ttsOnly })
        val tocTitles = tocTitlesFor(rrSession, filtered.doc)
        _ui.value = ReaderUi(
            title = filtered.doc.title,
            doc = filtered.doc,
            locus = clamped,
            loading = false,
            storedPath = storedPath,
            replacedRangesByBlockId = filtered.replacedRangesByBlockId,
            filtersGlobal = global,
            filtersGroups = groups,
            filtersLocal = local,
            tocTitles = tocTitles,
            tocIndex = tocIndexFor(rrSession, clamped, tocTitles.size),
        )
    }

    private fun tocTitlesFor(session: RoyalRoadReadSession?, doc: BookDoc): List<String> {
        if (session != null && session.toc.isNotEmpty()) {
            return session.toc.mapIndexed { i, link ->
                link.title.ifBlank { "Chapter ${i + 1}" }
            }
        }
        return doc.chapters.mapIndexed { i, ch ->
            ch.title.ifBlank { "Chapter ${i + 1}" }
        }
    }

    private fun tocIndexFor(
        session: RoyalRoadReadSession?,
        locus: Locus,
        tocSize: Int,
    ): Int {
        if (tocSize <= 0) return 0
        return if (session != null) {
            (session.startIndex + locus.chapterIndex).coerceIn(0, tocSize - 1)
        } else {
            locus.chapterIndex.coerceIn(0, tocSize - 1)
        }
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

    suspend fun jumpToChapter(chapterIndex: Int) {
        val session = rrSession
        if (session != null) {
            seekRoyalRoadChapter(chapterIndex)
            return
        }
        val doc = _ui.value.doc ?: return
        val ci = chapterIndex.coerceIn(0, doc.chapters.lastIndex)
        jumpTo(Locus(ci, 0, 0))
    }

    private suspend fun seekRoyalRoadChapter(absoluteIndex: Int) {
        val loaded = withContext(Dispatchers.IO) {
            flow.royalRoad.seekToChapter(bookId, absoluteIndex)
        }
        rrSession = loaded
        rawDoc = BookDoc(loaded.title, loaded.chapters)
        withContext(Dispatchers.IO) {
            persistPluginSnapshot(loaded)
            loaded.toc.getOrNull(loaded.startIndex)?.url?.let { url ->
                runCatching { flow.royalRoad.syncProgress(loaded.bookId, url) }
            }
        }
        applyFilters(
            _ui.value.filtersGlobal,
            _ui.value.filtersGroups,
            _ui.value.filtersLocal,
            locus = Locus(0, 0, 0),
            invalidateCache = true,
        )
        // Persist absolute progress at the seek target.
        persist(_ui.value.locus)
    }

    fun jumpTo(locus: Locus) {
        onLocus(locus)
        tts.jumpTo(locus)
    }

    fun onLocus(locus: Locus) {
        val tocTitles = _ui.value.tocTitles
        _ui.value = _ui.value.copy(
            locus = locus,
            tocIndex = tocIndexFor(rrSession, locus, tocTitles.size),
        )
        persist(locus)
    }

    /** Flush current UI locus using app scope so it survives ViewModel teardown. */
    fun persistNow() {
        persist(_ui.value.locus)
    }

    private fun persist(locus: Locus) {
        val id = bookId
        if (id.isBlank()) return
        val doc = _ui.value.doc
        val items = doc?.items.orEmpty()
        val readingProgress = when {
            items.size <= 1 -> 0f
            else -> locus.flatIndex(doc!!).toFloat() / items.lastIndex
        }.coerceIn(0f, 1f)
        // RR sessions start at an absolute ToC index; store absolute chapter for cache/splash.
        val session = rrSession
        val absoluteChapter = if (session != null) {
            (session.startIndex + locus.chapterIndex).coerceIn(0, (session.toc.size - 1).coerceAtLeast(0))
        } else {
            locus.chapterIndex
        }
        flow.appScope.launch {
            val row = flow.db.progress().get(id) ?: return@launch
            flow.db.progress().upsert(
                row.copy(
                    chapterIndex = absoluteChapter,
                    blockIndex = locus.blockIndex,
                    charOffset = locus.charOffset,
                    readingProgress = readingProgress,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            if (RoyalRoadHtml.isPluginBookId(id)) {
                runCatching {
                    flow.royalRoad.maintainChapterCache(id, absoluteChapter)
                }
            }
        }
    }

    override fun onCleared() {
        persistNow()
        tts.setMoreProvider(bookId, null)
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
