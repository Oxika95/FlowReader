package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionLogFormatTest {
    @Test
    fun positionLineNamesSourceSessionAndRow() {
        val update = ProgressUpdate(
            session = ReadingSessionId(PositionDomain.Queue, "", openedAt = 1000L),
            rowKey = "q1",
            chapterIndex = 2,
            blockIndex = 5,
            charOffset = 9,
            fraction = 0.123456f,
            at = 2000L,
            chapterHref = "ch2",
            source = PositionSource.Tts,
        )
        val fields = PositionLogFormat.position(update, written = true, now = 0L).split('\t')
        assertEquals(
            listOf("POS", "Tts", "queue@1000", "queue:q1", "c=2 b=5 o=9", "f=0.1235", "href=ch2", "written"),
            fields.drop(1),
        )
    }

    @Test
    fun missingRowIsMarked() {
        val update = ProgressUpdate(ReadingSessionId(PositionDomain.Library, "b", 1L), "b", 0, 0, 0, 0f, 1L)
        val fields = PositionLogFormat.position(update, written = false, now = 0L).split('\t')
        assertEquals("library:b@1", fields[3])
        assertEquals("noRow", fields.last())
    }

    @Test
    fun eventFieldsStayOnOneLine() {
        val line = PositionLogFormat.event("QUEUE_ADD", listOf("title" to "a\tb\nc", "via" to null), now = 0L)
        assertEquals(listOf("QUEUE_ADD", "title=a b c", "via=null"), line.split('\t').drop(1))
    }
}
