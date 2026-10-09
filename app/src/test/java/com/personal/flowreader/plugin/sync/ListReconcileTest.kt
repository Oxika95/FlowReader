package com.personal.flowreader.plugin.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListReconcileTest {
    private fun set(vararg ids: String) = ids.toSet()

    @Test
    fun firstSyncIsAUnionWithoutRemovals() {
        val p = ListReconcile.plan(local = set("a", "b"), remote = set("b", "c"), baseline = null)
        assertEquals(set("c"), p.addLocal)
        assertEquals(set("a"), p.pushAdd)
        assertTrue(p.removeLocal.isEmpty())
        assertTrue(p.pushRemove.isEmpty())
        assertEquals(set("a", "b", "c"), p.baseline)
    }

    @Test
    fun siteAdditionsAndRemovalsApplyLocally() {
        val p = ListReconcile.plan(local = set("a", "b"), remote = set("a", "c"), baseline = set("a", "b"))
        assertEquals(set("c"), p.addLocal)
        assertEquals(set("b"), p.removeLocal)
        assertTrue(p.pushAdd.isEmpty())
        assertTrue(p.pushRemove.isEmpty())
        assertEquals(set("a", "c"), p.baseline)
    }

    @Test
    fun appAdditionsAndRemovalsPushToTheSite() {
        val p = ListReconcile.plan(local = set("a", "c"), remote = set("a", "b"), baseline = set("a", "b"))
        assertEquals(set("c"), p.pushAdd)
        assertEquals(set("b"), p.pushRemove)
        assertTrue(p.addLocal.isEmpty())
        assertTrue(p.removeLocal.isEmpty())
        assertEquals(set("a", "c"), p.baseline)
    }

    @Test
    fun removedOnBothSidesIsSettled() {
        val p = ListReconcile.plan(local = set("a"), remote = set("a"), baseline = set("a", "b"))
        assertTrue(p.removeLocal.isEmpty() && p.pushRemove.isEmpty())
        assertEquals(set("a"), p.baseline)
    }

    @Test
    fun addedOnBothSidesIsSettled() {
        val p = ListReconcile.plan(local = set("a", "n"), remote = set("a", "n"), baseline = set("a"))
        assertTrue(p.addLocal.isEmpty() && p.pushAdd.isEmpty())
        assertEquals(set("a", "n"), p.baseline)
    }

    @Test
    fun emptyRemoteListSkipsRemovalsButKeepsPushes() {
        val p = ListReconcile.plan(local = set("a", "b", "c"), remote = emptySet(), baseline = set("a", "b"))
        assertTrue(p.guarded)
        assertTrue(p.removeLocal.isEmpty())
        assertEquals(set("c"), p.pushAdd)
        assertEquals(set("a", "b", "c"), p.baseline)
    }

    @Test
    fun remoteUnderHalfTheBaselineIsGuarded() {
        val base = set("a", "b", "c", "d", "e")
        val p = ListReconcile.plan(local = base, remote = set("a", "b"), baseline = base)
        assertTrue(p.guarded)
        assertTrue(p.removeLocal.isEmpty())
        assertEquals(base, p.baseline)
    }

    @Test
    fun smallListsMayLoseMostStories() {
        val base = set("a", "b", "c")
        val p = ListReconcile.plan(local = base, remote = set("a"), baseline = base)
        assertFalse(p.guarded)
        assertEquals(set("b", "c"), p.removeLocal)
    }

    @Test
    fun queuedAppChangesAreNotUndoneByTheSite() {
        val p = ListReconcile.plan(
            local = set("a", "x"),
            remote = set("a", "y"),
            baseline = set("a", "x"),
            pendingAdd = set("x"),
            pendingRemove = set("y"),
        )
        assertTrue(p.removeLocal.isEmpty())
        assertTrue(p.addLocal.isEmpty())
        val first = ListReconcile.plan(local = set("a"), remote = set("a", "y"), baseline = null, pendingRemove = set("y"))
        assertTrue(first.addLocal.isEmpty())
    }

    @Test
    fun emptyBaselineIsNeverGuarded() {
        assertFalse(ListReconcile.looksTruncated(remote = emptySet(), baseline = emptySet()))
    }
}
