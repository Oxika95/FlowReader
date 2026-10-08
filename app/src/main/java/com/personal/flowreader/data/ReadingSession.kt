package com.personal.flowreader.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One chapter ready to show and speak: filtered text, replacement tints, sentences. */
data class PreparedChapter(
    val index: Int,
    val raw: Chapter,
    val chapter: Chapter,
    val replaced: Map<String, List<IntRange>>,
    val sentences: List<Sentence>,
) {
    companion object {
        fun of(index: Int, raw: Chapter, visualRules: List<FilterRule>, targetChars: Int, flexChars: Int): PreparedChapter {
            val filtered = TextFilters.applyVisual(BookDoc("", listOf(raw)), visualRules)
            val chapter = filtered.doc.chapters.first()
            return PreparedChapter(
                index = index,
                raw = raw,
                chapter = chapter,
                replaced = filtered.replacedRangesByBlockId,
                sentences = SentenceSplitter.splitChapter(index, chapter, targetChars, flexChars),
            )
        }
    }
}

/**
 * The loaded part of a book: a contiguous run of chapters and their sentences. [doc] has one
 * entry per known chapter; chapters outside the window have no blocks.
 */
class ReadingWindow private constructor(
    val title: String,
    private val titles: List<String>,
    val chapters: Map<Int, PreparedChapter>,
    val table: SentenceTable,
    /** Chapter whose first sentence is [SentenceTable.ORIGIN]; sentence indices depend on it. */
    val origin: Int,
) {
    val loaded: IntRange? get() = table.chapters

    val doc: BookDoc by lazy {
        val count = maxOf(titles.size, (loaded?.last ?: -1) + 1)
        BookDoc(title, List(count) { i -> chapters[i]?.chapter ?: Chapter(titles.getOrElse(i) { "" }, emptyList()) })
    }

    val replacedRangesByBlockId: Map<String, List<IntRange>> by lazy {
        HashMap<String, List<IntRange>>().also { out -> chapters.values.forEach { out.putAll(it.replaced) } }
    }

    fun chapterTitle(index: Int): String =
        chapters[index]?.chapter?.title?.ifBlank { null } ?: titles.getOrElse(index) { "" }

    fun with(prepared: PreparedChapter): ReadingWindow = ReadingWindow(
        title,
        titles,
        chapters + (prepared.index to prepared),
        table.withChapter(prepared.index, prepared.sentences),
        origin,
    )

    /** Same chapters under a new title list (the book gained chapters after the window). */
    fun withTitles(titles: List<String>): ReadingWindow = ReadingWindow(title, titles, chapters, table, origin)

    fun retain(keep: IntRange): ReadingWindow {
        val range = loaded ?: return this
        if (keep.first <= range.first && keep.last >= range.last) return this
        val next = table.retain(keep)
        val kept = next.chapters ?: return this
        return ReadingWindow(title, titles, chapters.filterKeys { it in kept }, next, origin)
    }

    /** Same chapters re-split (clip band change); numbering restarts at the first chapter. */
    fun resplit(targetChars: Int, flexChars: Int): ReadingWindow {
        val prepared = chapters.values.map {
            it.copy(sentences = SentenceSplitter.splitChapter(it.index, it.chapter, targetChars, flexChars))
        }
        return of(title, titles, prepared, origin = prepared.minOfOrNull { it.index } ?: 0)
    }

    companion object {
        /** [prepared] must be contiguous; [origin]'s first sentence is [SentenceTable.ORIGIN]. */
        fun of(title: String, titles: List<String>, prepared: List<PreparedChapter>, origin: Int): ReadingWindow =
            ReadingWindow(
                title,
                titles,
                prepared.associateBy { it.index },
                SentenceTable.of(prepared.associate { it.index to it.sentences }, origin),
                origin,
            )
    }
}

/**
 * The open book shared by the reader and TTS (app-scoped: playback continues with the reader
 * closed). Grows one adjacent chapter at a time via [loader] and drops chapters far from every
 * focus (reader viewport, TTS playhead).
 */
class ReadingSession(
    val bookId: String,
    contentKey: Int,
    initial: ReadingWindow,
    private val chapterCount: () -> Int,
    private val loader: suspend (index: Int, targetChars: Int, flexChars: Int) -> PreparedChapter?,
    /** Chapters kept on each side of every focus. */
    private val keepRadius: Int = 1,
) {
    private val _window = MutableStateFlow(initial)
    val window: StateFlow<ReadingWindow> = _window
    private val mutex = Mutex()
    private val focus = HashMap<String, Int>()

    /** Filters + parser identity; a reader reopening with the same key reuses this session. */
    @Volatile
    var contentKey: Int = contentKey
        private set

    val count: Int get() = chapterCount()

    /**
     * The book behind [chapterCount] / loader changed without moving existing chapters (chapters
     * appended): new [titles] and identity, loaded chapters and sentence indices kept.
     */
    suspend fun updateSource(titles: List<String>, contentKey: Int) = mutex.withLock {
        this.contentKey = contentKey
        _window.value = _window.value.withTitles(titles)
    }

    /** Load [index] if it is directly before or after the window. */
    suspend fun loadAdjacent(index: Int, targetChars: Int, flexChars: Int): Boolean = mutex.withLock {
        val range = _window.value.loaded ?: return@withLock false
        if (index in range) return@withLock true
        if (index != range.last + 1 && index != range.first - 1) return@withLock false
        if (index < 0 || index >= chapterCount()) return@withLock false
        val prepared = loader(index, targetChars, flexChars) ?: return@withLock false
        _window.value = _window.value.with(prepared)
        trimLocked(also = index)
        true
    }

    suspend fun loadNext(targetChars: Int, flexChars: Int): Boolean =
        loadAdjacent((_window.value.loaded?.last ?: -1) + 1, targetChars, flexChars)

    /** [owner]'s position moved to [chapterIndex] (null: owner gone). */
    suspend fun setFocus(owner: String, chapterIndex: Int?) = mutex.withLock {
        if (chapterIndex == null) focus.remove(owner) else focus[owner] = chapterIndex
        trimLocked()
    }

    suspend fun resplit(targetChars: Int, flexChars: Int) = mutex.withLock {
        _window.value = _window.value.resplit(targetChars, flexChars)
    }

    /** Drop chapters far from every focus; [also] (a chapter just requested) is kept. */
    private fun trimLocked(also: Int? = null) {
        if (focus.isEmpty()) return
        val lo = minOf(focus.values.min() - keepRadius, also ?: Int.MAX_VALUE)
        val hi = maxOf(focus.values.max() + keepRadius, also ?: Int.MIN_VALUE)
        _window.value = _window.value.retain(lo..hi)
    }

    companion object {
        /** First and last chapter visible in the reader. */
        const val FOCUS_READER = "reader"
        const val FOCUS_READER_END = "reader-end"
        const val FOCUS_TTS = "tts"
    }
}
