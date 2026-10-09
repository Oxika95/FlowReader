package com.personal.flowreader.ui.reader

import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ReadingPosition
import com.personal.flowreader.data.ReadingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * The reader's Queue mode: which stream it shows, the item under the locus (header, card, Local
 * filters), live Queue changes and Queue edits from the Contents card.
 */
internal class ReaderQueueMode(
    private val flow: FlowApp,
    private val queId: String?,
    private val ui: MutableStateFlow<ReaderUi>,
) {
    /** Where the reader opens: [locus] (re-found by [anchor]), in [reuse] when it holds it. */
    class Start(val locus: Locus, val reuse: ReadingSession?, val anchor: String = "")

    var ref: QueueStreamRef? = null
        private set

    val book: QueueBook? get() = ref?.book

    /**
     * Build the stream and open at the tapped row's saved position. When that row is the one TTS
     * is reading (or no row was given), start at the spoken sentence instead. A stream TTS is
     * still attached to is reused with its holder, so live changes keep reaching it.
     */
    suspend fun open(savedLocus: (ReadingPosition, ReaderBook) -> Locus): Start {
        val global = flow.settings.globalFiltersOnce()
        val groups = flow.settings.groupFiltersOnce()
        val live = flow.queue.current()
        val fresh = QueueStreams.build(flow, reuse = live?.first?.book)
        if (fresh.segments.isEmpty()) throw IllegalStateException("Queue is empty")
        val reused = live?.takeIf { (r, _) ->
            r.global == global && r.groups == groups && r.contentKey == fresh.contentKey(global, groups)
        }
        val r = reused?.first ?: QueueStreamRef(fresh, global, groups)
        ref = r
        val queue = r.book
        val spoken = spokenLocus(queue)
        val last = if (queId == null) withContext(Dispatchers.IO) { flow.db.queue().state()?.currentQueId } else null
        val tapped = (queId ?: last)?.let(queue::indexOfQue)?.takeIf { it >= 0 }
        val useSpoken = spoken != null && (tapped == null || tapped == queue.segmentIndexOf(spoken.chapterIndex))
        val startSeg = when {
            useSpoken -> queue.segmentIndexOf(spoken!!.chapterIndex)
            tapped != null -> tapped
            else -> 0
        }
        val segment = queue.segments[startSeg]
        ui.update { it.copy(filtersGlobal = global, filtersGroups = groups, filtersLocal = segment.local) }
        refreshToc()
        applySegment(startSeg)
        if (useSpoken) return Start(spoken!!, reused?.second)
        val item = withContext(Dispatchers.IO) { flow.catalog.getQue(segment.queId) }
            ?: throw IllegalArgumentException("Queue item not found")
        return Start(queue.toGlobal(startSeg, savedLocus(item.position, segment.book)), reused?.second, item.position.anchorText)
    }

    /** The sentence TTS is reading in the Queue stream, mapped into [queue] (rows may have moved). */
    private fun spokenLocus(queue: QueueBook): Locus? {
        val state = flow.tts.state.value
        if (state.bookId != QueueBook.ID) return null
        val playing = flow.queue.stream ?: return null
        val sentence = state.sentence ?: return null
        val locus = Locus(sentence.chapterIndex, sentence.blockIndex, sentence.start)
        if (playing === queue) return locus
        val seg = playing.segmentIndexOf(locus.chapterIndex)
        if (queue.indexOfQue(playing.segments.getOrNull(seg)?.queId ?: return null) < 0) return null
        return queue.remap(locus, playing)
    }

    /** ToC rows and Queue items of the current stream. */
    fun refreshToc() {
        val queue = book ?: return
        ui.update {
            it.copy(
                tocTitles = queue.reader.toc.map { (_, label) -> label },
                tocLevels = queue.tocLevels,
                queueItems = queue.tocItems,
            )
        }
    }

    /** Header, cover, card and Local filters follow the Queue item at [segment]. */
    fun applySegment(segment: Int) {
        val queue = book ?: return
        val s = queue.segments.getOrNull(segment) ?: return
        ui.update {
            it.copy(
                title = s.title,
                storedPath = s.storedPath,
                currentBookId = s.bookId,
                filtersLocal = s.local,
                headerChapter = "Queue · ${segment + 1} of ${queue.segments.size}"
                    .takeIf { s.book.toc.size <= 1 },
                currentQueId = s.queId,
            )
        }
    }

    fun onLocus(locus: Locus) {
        val queue = book ?: return
        val seg = queue.segmentIndexOf(locus.chapterIndex)
        if (queue.segments.getOrNull(seg)?.queId != ui.value.currentQueId) applySegment(seg)
    }

    /** New filters: a new holder (the session identity changes), Local rules on the current item. */
    fun withFilters(locus: Locus, global: List<FilterRule>, groups: List<FilterRule>, local: List<FilterRule>) {
        val queue = book ?: return
        val seg = queue.segmentIndexOf(locus.chapterIndex)
        ref = QueueStreamRef(if (seg >= 0) queue.withLocal(seg, local) else queue, global, groups)
    }

    /** What the reader does after a live change. */
    sealed interface UpdateAction {
        /** Same session: header and ToC refreshed. */
        data object InPlace : UpdateAction

        /** Rebuilt stream: show [locus] in [session]. */
        class Show(val locus: Locus, val session: ReadingSession) : UpdateAction

        data object Empty : UpdateAction
    }

    /** Apply [update] when it is about the stream on screen; null when it isn't. */
    fun onUpdate(update: QueueUpdate, locus: Locus): UpdateAction? {
        val mine = when (update.change) {
            QueueChange.Edited -> update.previous === book
            else -> update.ref === ref
        }
        if (!mine) return null
        if (update.change != QueueChange.Edited) {
            refreshToc()
            book?.let { applySegment(it.segmentIndexOf(locus.chapterIndex)) }
            return UpdateAction.InPlace
        }
        ref = update.ref
        val session = update.session ?: return UpdateAction.Empty
        refreshToc()
        val target = update.ref.book.remap(locus, update.previous) ?: return UpdateAction.Empty
        return UpdateAction.Show(target, session)
    }

    suspend fun reorder(ids: List<String>) = withContext(Dispatchers.IO) { flow.catalog.reorderQue(ids) }

    suspend fun remove(ids: Set<String>) = withContext(Dispatchers.IO) { ids.forEach { flow.catalog.removeQue(it) } }
}
