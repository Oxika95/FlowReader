package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaceScheduleTest {
    private val normal = RaceSchedule.staggered(3, 1_000L)
    private val overlap = listOf(0L, 0L, 1_000L)

    @Test
    fun staggeredOffsets() {
        assertEquals(listOf(0L, 1_000L, 2_000L), normal)
    }

    @Test
    fun startsOnlyFirstAttemptAtZero() {
        assertEquals(listOf(0), RaceSchedule.due(normal, 0L, started = 0, inFlight = 0, audioInFlight = false))
    }

    @Test
    fun backupWaitsForItsDelay() {
        assertEquals(emptyList<Int>(), RaceSchedule.due(normal, 999L, 1, 1, false))
        assertEquals(listOf(1), RaceSchedule.due(normal, 1_000L, 1, 1, false))
    }

    @Test
    fun noBackupWhileAudioIsStreaming() {
        assertEquals(emptyList<Int>(), RaceSchedule.due(normal, 1_500L, 1, 1, audioInFlight = true))
        assertEquals(emptyList<Int>(), RaceSchedule.due(normal, 5_000L, 1, 1, audioInFlight = true))
    }

    @Test
    fun fastFailureStartsNextImmediately() {
        assertEquals(listOf(1), RaceSchedule.due(normal, 200L, started = 1, inFlight = 0, audioInFlight = false))
    }

    @Test
    fun audioFromFailedAttemptDoesNotGate() {
        assertEquals(listOf(2), RaceSchedule.due(normal, 1_200L, started = 2, inFlight = 0, audioInFlight = true))
    }

    @Test
    fun lateWakeStartsEveryOverdueAttempt() {
        assertEquals(listOf(1, 2), RaceSchedule.due(normal, 2_100L, 1, 1, false))
    }

    @Test
    fun overlapStartsBothAtZeroThenBackup() {
        assertEquals(listOf(0, 1), RaceSchedule.due(overlap, 0L, 0, 0, false))
        assertEquals(emptyList<Int>(), RaceSchedule.due(overlap, 500L, 2, 2, false))
        assertEquals(listOf(2), RaceSchedule.due(overlap, 1_000L, 2, 2, false))
    }

    @Test
    fun nothingAfterAllStarted() {
        assertEquals(emptyList<Int>(), RaceSchedule.due(normal, 9_000L, 3, 0, false))
        assertNull(RaceSchedule.nextStartMs(normal, 3))
        assertEquals(1_000L, RaceSchedule.nextStartMs(normal, 1))
    }
}
