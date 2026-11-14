package com.personal.flowreader.plugin.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocusReconcileTest {
    private fun p(block: Int, char: Int = 0, fraction: Float = 0f) =
        LocusReconcile.Point(blockIndex = block, charOffset = char, fraction = fraction)

    @Test
    fun noBaselineTakesFurther() {
        val local = p(1, 0, 0.2f)
        val remote = p(3, 0, 0.5f)
        assertEquals(LocusReconcile.Action.ApplyRemote(remote), LocusReconcile.decide(local, remote, null))
        // App further than site → push the app locus.
        assertEquals(LocusReconcile.Action.PushLocal(remote), LocusReconcile.decide(remote, local, null))
    }

    @Test
    fun oneSideMoved() {
        val baseline = p(1)
        val local = p(2)
        val remote = p(1)
        assertEquals(LocusReconcile.Action.PushLocal(local), LocusReconcile.decide(local, remote, baseline))
        assertEquals(LocusReconcile.Action.ApplyRemote(local), LocusReconcile.decide(remote, local, baseline))
    }

    @Test
    fun bothMovedIsConflict() {
        val baseline = p(1)
        val local = p(2)
        val remote = p(3)
        val action = LocusReconcile.decide(local, remote, baseline)
        assertTrue(action is LocusReconcile.Action.Conflict)
    }

    @Test
    fun sameIsNoOp() {
        val point = p(2, 5, 0.3f)
        assertEquals(LocusReconcile.Action.NoOp, LocusReconcile.decide(point, point, point))
    }
}
