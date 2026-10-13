package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeLocusTest {
    private val hrefs = listOf("a", "b", "c")
    private val indexOf = { href: String -> hrefs.indexOf(href) }

    private fun resume(position: ReadingPosition, count: Int = hrefs.size, readable: (Int) -> Boolean = { true }) =
        ResumeLocus.of(position, count, indexOf, readable)

    @Test
    fun hrefWinsOverDriftedIndex() {
        assertEquals(Locus(2, 4, 7), resume(ReadingPosition(chapterIndex = 0, chapterHref = "c", blockIndex = 4, charOffset = 7)))
        assertEquals(Locus(1, 0, 0), resume(ReadingPosition(chapterIndex = 1, chapterHref = "gone")))
        assertEquals(Locus(2, 0, 0), resume(ReadingPosition(chapterIndex = 9)))
    }

    @Test
    fun finishedChapterResumesAtTheNextOnceItExists() {
        val finished = ReadingPosition(chapterIndex = 1, chapterHref = "b", blockIndex = 5, charOffset = 3, chapterFinished = true)
        assertEquals(Locus(2, 0, 0), resume(finished))
        assertEquals(Locus(2, 5, 3), resume(finished.copy(chapterIndex = 2, chapterHref = "c")))
        assertEquals(Locus(1, 5, 3), resume(finished, count = 2))
    }

    @Test
    fun finishedChapterStaysWhenTheNextIsLocked() {
        val finished = ReadingPosition(chapterIndex = 1, chapterHref = "b", blockIndex = 5, chapterFinished = true)
        assertEquals(Locus(1, 5, 0), resume(finished) { it != 2 })
    }

    @Test
    fun unreadableSavedChapterFallsBackToTheNearestReadable() {
        assertEquals(Locus(0, 0, 0), resume(ReadingPosition(chapterIndex = 2, chapterHref = "c", blockIndex = 3)) { it == 0 })
        assertEquals(Locus(2, 0, 0), resume(ReadingPosition(chapterIndex = 0, chapterHref = "a")) { it == 2 })
    }
}
