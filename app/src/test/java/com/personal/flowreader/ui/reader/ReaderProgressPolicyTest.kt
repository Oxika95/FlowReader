package com.personal.flowreader.ui.reader

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
}
