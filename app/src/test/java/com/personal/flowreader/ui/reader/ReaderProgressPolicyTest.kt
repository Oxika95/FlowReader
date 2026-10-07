package com.personal.flowreader.ui.reader

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.Locus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderProgressPolicyTest {
    @Test
    fun neverPersistsWhileLoadingOrFailedOrUnmoved() {
        assertFalse(ReaderProgressPolicy.mayPersist("b", hasDoc = false, loading = true, error = null, positionMoved = true))
        assertFalse(ReaderProgressPolicy.mayPersist("b", hasDoc = true, loading = true, error = null, positionMoved = true))
        assertFalse(ReaderProgressPolicy.mayPersist("b", hasDoc = false, loading = false, error = "x", positionMoved = true))
        assertFalse(ReaderProgressPolicy.mayPersist("b", hasDoc = true, loading = false, error = null, positionMoved = false))
        assertFalse(ReaderProgressPolicy.mayPersist("", hasDoc = true, loading = false, error = null, positionMoved = true))
        assertTrue(ReaderProgressPolicy.mayPersist("b", hasDoc = true, loading = false, error = null, positionMoved = true))
    }

    @Test
    fun fractionIsBlockPositionOverWholeDoc() {
        val doc = BookDoc(
            "t",
            listOf(
                Chapter("a", List(3) { Block("a$it", BlockKind.Paragraph, "x") }),
                Chapter("b", List(2) { Block("b$it", BlockKind.Paragraph, "x") }),
            ),
        )
        assertEquals(0f, ReaderProgressPolicy.fraction(doc, Locus(0, 0, 0)), 0f)
        assertEquals(0.75f, ReaderProgressPolicy.fraction(doc, Locus(1, 0, 0)), 0f)
        assertEquals(1f, ReaderProgressPolicy.fraction(doc, Locus(1, 1, 0)), 0f)
    }
}
