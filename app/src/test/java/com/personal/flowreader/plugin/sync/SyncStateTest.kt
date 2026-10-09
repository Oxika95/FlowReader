package com.personal.flowreader.plugin.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStateTest {
    private val add = SyncOp.Membership("1", "follow", on = true)
    private val remove = SyncOp.Membership("2", "follow", on = false)
    private val pos = SyncOp.Position("3", "https://s/c/9", "Chapter 9")

    @Test
    fun jsonRoundTrips() {
        val state = SyncState(
            lists = mapOf("follow" to setOf("1", "2"), "favorite" to emptySet()),
            positions = mapOf("3" to "https://s/c/8"),
            outbox = listOf(add, remove, pos.copy(attempts = 2)),
            conflicts = mapOf("4" to PositionConflict("4", "https://s/c/1", "https://s/c/5")),
            lastSyncedAt = 1234L,
            guardedLists = setOf("readlater"),
            pending = mapOf(
                "5" to PendingPosition("5", "https://s/c/42", "Chapter 42"),
                "6" to PendingPosition("6", "https://s/c/7", "", overwrite = true),
            ),
        )
        assertEquals(state, SyncState.parse(state.toJson()))
    }

    @Test
    fun newerOpForTheSameKeyReplacesTheOlder() {
        val s = SyncState().enqueue(add).enqueue(pos).enqueue(add.copy(on = false))
        assertEquals(listOf(pos, add.copy(on = false)), s.outbox)
    }

    @Test
    fun settleKeepsAReplacementQueuedMeanwhile() {
        val newer = pos.copy(chapterUrl = "https://s/c/10")
        val s = SyncState().enqueue(pos).enqueue(newer)
        assertEquals(listOf(newer), s.settle(pos).outbox)
        assertEquals(emptyList<SyncOp>(), s.settle(newer).outbox)
    }

    @Test
    fun retryCountsAttemptsThenDrops() {
        val once = SyncState().enqueue(pos).retry(pos, maxAttempts = 3)
        assertEquals(listOf(pos.copy(attempts = 1)), once.outbox)
        val last = pos.copy(attempts = 2)
        assertEquals(emptyList<SyncOp>(), SyncState(outbox = listOf(last)).retry(last, maxAttempts = 3).outbox)
    }

    @Test
    fun queuedPushesStayOutOfTheBaseline() {
        val s = SyncState(outbox = listOf(add, remove, SyncOp.Membership("5", "favorite", on = true)))
        assertEquals(setOf("2", "9"), s.listBaseline("follow", planned = setOf("1", "9")))
    }
}
