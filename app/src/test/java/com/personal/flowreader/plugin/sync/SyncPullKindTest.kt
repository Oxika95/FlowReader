package com.personal.flowreader.plugin.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPullKindTest {
    private val lists = listOf("follow", "favorite", "readlater")

    @Test
    fun emptyLocalAndNoBaselinesIsInitialPull() {
        assertTrue(
            SyncPullKind.needsInitialPull(
                syncableListIds = lists,
                baselineListIds = emptySet(),
                localWorkCounts = lists.associateWith { 0 },
            ),
        )
    }

    @Test
    fun anyBaselineMeansProgressive() {
        assertFalse(
            SyncPullKind.needsInitialPull(
                syncableListIds = lists,
                baselineListIds = setOf("follow"),
                localWorkCounts = lists.associateWith { 0 },
            ),
        )
    }

    @Test
    fun localMembershipsWithoutBaselineStayProgressive() {
        assertFalse(
            SyncPullKind.needsInitialPull(
                syncableListIds = lists,
                baselineListIds = emptySet(),
                localWorkCounts = mapOf("follow" to 3, "favorite" to 0, "readlater" to 0),
            ),
        )
    }

    @Test
    fun noSyncableListsIsNotInitialPull() {
        assertFalse(
            SyncPullKind.needsInitialPull(
                syncableListIds = emptyList(),
                baselineListIds = emptySet(),
                localWorkCounts = emptyMap(),
            ),
        )
    }
}
