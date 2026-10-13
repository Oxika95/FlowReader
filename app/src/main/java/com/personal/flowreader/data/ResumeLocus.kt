package com.personal.flowreader.data

/** Where reading resumes from a saved [ReadingPosition] (Library, Queue and plugin books). */
object ResumeLocus {
    /**
     * The saved chapter (its href wins over a drifted index), clamped to [count]. A finished
     * chapter resumes at the start of the next one once that exists and is [readable]; a saved
     * chapter that is no longer readable falls back to the nearest readable one before it.
     */
    fun of(
        position: ReadingPosition,
        count: Int,
        indexOfHref: (String) -> Int,
        readable: (Int) -> Boolean = { true },
    ): Locus {
        if (count <= 0) return Locus(0, 0, 0)
        val byHref = if (position.chapterHref.isBlank()) -1 else indexOfHref(position.chapterHref)
        val saved = (if (byHref >= 0) byHref else position.chapterIndex).coerceIn(0, count - 1)
        val next = saved + 1
        if (position.chapterFinished && next < count && readable(next)) return Locus(next, 0, 0)
        if (readable(saved)) return Locus(saved, position.blockIndex, position.charOffset)
        val fallback = (saved - 1 downTo 0).firstOrNull(readable)
            ?: (next until count).firstOrNull(readable)
            ?: saved
        return Locus(fallback, 0, 0)
    }
}
