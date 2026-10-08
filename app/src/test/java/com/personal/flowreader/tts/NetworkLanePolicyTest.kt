package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkLanePolicyTest {
    private val wifiGood = NetSnapshot(
        transport = NetTransport.WIFI,
        validated = true,
        wifiUp = true,
        cellUp = true,
        wifiRssi = -55,
        cellLevel = 3,
    )

    private fun fast(lane: NetLane = NetLane.DEFAULT, latencyMs: Long = 400L) = RaceReport(
        winner = lane,
        firstAudioMs = latencyMs,
        winnerStartMs = 0L,
        winnerLatencyMs = latencyMs,
        totalMs = latencyMs + 300L,
        started = 1,
        failed = emptyList(),
    )

    /** A delayed backup had to win. */
    private fun slow(lane: NetLane = NetLane.DEFAULT) = RaceReport(
        winner = lane,
        firstAudioMs = 2_600L,
        winnerStartMs = 2_000L,
        winnerLatencyMs = 600L,
        totalMs = 2_900L,
        started = 2,
        failed = emptyList(),
    )

    private val failed =
        RaceReport(winner = null, firstAudioMs = null, totalMs = 6_000L, started = 3, failed = List(3) { NetLane.DEFAULT })

    private fun policy(s: NetSnapshot = wifiGood) = NetworkLanePolicy().apply { onSnapshot(s, nowMs = 0L) }

    private fun lanes(p: NetworkLanePolicy) = p.plan().map { it.startMs to it.lane }

    @Test
    fun goodWifiIsNormalStaggeredDefault() {
        val p = policy()
        assertEquals(NetMode.NORMAL, p.mode)
        assertEquals(
            listOf(0L to NetLane.DEFAULT, 2_000L to NetLane.DEFAULT, 4_000L to NetLane.DEFAULT),
            lanes(p),
        )
        assertFalse(p.wantCellular(0L))
    }

    @Test
    fun hedgeDelayDefaultsUntilEnoughSamples() {
        val p = policy()
        repeat(4) { p.onRaceResult(fast(latencyMs = 1_500L)) }
        assertEquals(2_000L, p.hedgeDelayMs())
        p.onRaceResult(fast(latencyMs = 1_500L))
        assertEquals(1_500L, p.hedgeDelayMs())
    }

    @Test
    fun hedgeDelayTracksNinetiethPercentile() {
        val p = policy()
        (1..20).forEach { p.onRaceResult(fast(latencyMs = 1_000L + it * 100L)) }
        assertEquals(2_800L, p.hedgeDelayMs())
        assertEquals(listOf(0L, 2_800L, 5_600L), p.plan().map { it.startMs })
    }

    @Test
    fun hedgeDelayClamped() {
        val quick = policy()
        repeat(10) { quick.onRaceResult(fast(latencyMs = 300L)) }
        assertEquals(1_000L, quick.hedgeDelayMs())
        val slowLink = policy()
        repeat(10) { slowLink.onRaceResult(fast(latencyMs = 5_000L)) }
        assertEquals(3_000L, slowLink.hedgeDelayMs())
    }

    @Test
    fun slowFirstAttemptThatWinsIsNotAStall() {
        val p = policy()
        repeat(5) { p.onRaceResult(fast(latencyMs = 2_200L)) }
        assertEquals(NetMode.NORMAL, p.mode)
        assertFalse(p.wantCellular(0L))
    }

    @Test
    fun weakWifiOverlapsWifiAndCellAtZero() {
        val p = policy(wifiGood.copy(wifiRssi = -80))
        assertEquals(NetMode.OVERLAP, p.mode)
        assertEquals(
            listOf(0L to NetLane.WIFI, 0L to NetLane.CELL, 2_000L to NetLane.CELL),
            lanes(p),
        )
        assertTrue(p.wantCellular(0L))
    }

    @Test
    fun overlapWithoutCellFallsBackToNormalPlan() {
        val p = policy(wifiGood.copy(wifiRssi = -80, cellUp = false))
        assertEquals(NetMode.OVERLAP, p.mode)
        assertTrue(p.plan().all { it.lane == NetLane.DEFAULT })
    }

    @Test
    fun rssiHysteresis() {
        val p = policy(wifiGood.copy(wifiRssi = -74))
        assertEquals(NetMode.NORMAL, p.mode)
        p.onSnapshot(wifiGood.copy(wifiRssi = -76), 0L)
        assertEquals(NetMode.OVERLAP, p.mode)
        p.onSnapshot(wifiGood.copy(wifiRssi = -70), 0L)
        assertEquals(NetMode.OVERLAP, p.mode)
        p.onSnapshot(wifiGood.copy(wifiRssi = -68), 0L)
        assertEquals(NetMode.OVERLAP, p.mode)
        p.onSnapshot(wifiGood.copy(wifiRssi = -67), 0L)
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun warmUpBelowWarmThresholdAndLinger() {
        val p = policy(wifiGood.copy(wifiRssi = -72))
        assertEquals(NetMode.NORMAL, p.mode)
        assertTrue(p.wantCellular(0L))
        p.onSnapshot(wifiGood, 10_000L)
        assertTrue(p.wantCellular(29_999L))
        assertFalse(p.wantCellular(30_000L))
    }

    @Test
    fun twoStallsSwitchToCellFirst() {
        val p = policy()
        p.onRaceResult(slow())
        assertEquals(NetMode.NORMAL, p.mode)
        p.onRaceResult(failed)
        assertEquals(NetMode.CELL_FIRST, p.mode)
        assertEquals(
            listOf(0L to NetLane.CELL, 2_000L to NetLane.WIFI, 4_000L to NetLane.CELL),
            lanes(p),
        )
    }

    @Test
    fun fastWinResetsStallCount() {
        val p = policy()
        p.onRaceResult(slow())
        p.onRaceResult(fast())
        p.onRaceResult(slow())
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun contentErrorsAreIgnored() {
        val p = policy()
        val content = failed.copy(contentError = true)
        p.onRaceResult(content)
        p.onRaceResult(content)
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun cellFirstProbesWifiEveryFourthRace() {
        val p = policy()
        p.onRaceResult(failed)
        p.onRaceResult(failed)
        repeat(3) {
            assertEquals(NetLane.CELL, p.plan().first().lane)
            p.onRaceResult(fast(NetLane.CELL))
        }
        assertEquals(
            listOf(0L to NetLane.WIFI, 0L to NetLane.CELL, 2_000L to NetLane.CELL),
            lanes(p),
        )
    }

    @Test
    fun threeWifiWinsRecoverFromCellFirst() {
        val p = policy()
        p.onRaceResult(failed)
        p.onRaceResult(failed)
        p.onRaceResult(fast(NetLane.WIFI))
        p.onRaceResult(fast(NetLane.CELL))
        p.onRaceResult(fast(NetLane.WIFI))
        assertEquals(NetMode.CELL_FIRST, p.mode)
        p.onRaceResult(fast(NetLane.WIFI))
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun wifiFailureResetsRecoveryCount() {
        val p = policy()
        p.onRaceResult(failed)
        p.onRaceResult(failed)
        p.onRaceResult(fast(NetLane.WIFI))
        p.onRaceResult(fast(NetLane.WIFI))
        p.onRaceResult(fast(NetLane.CELL).copy(failed = listOf(NetLane.WIFI)))
        p.onRaceResult(fast(NetLane.WIFI))
        assertEquals(NetMode.CELL_FIRST, p.mode)
    }

    @Test
    fun leavingWifiClearsCellFirst() {
        val p = policy()
        p.onRaceResult(failed)
        p.onRaceResult(failed)
        p.onSnapshot(NetSnapshot(NetTransport.CELL, validated = true, cellUp = true, cellLevel = 3), 0L)
        assertEquals(NetMode.NORMAL, p.mode)
        p.onSnapshot(wifiGood, 0L)
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun weakCellWithWifiAvailableOverlapsReversed() {
        val p = policy(
            NetSnapshot(NetTransport.CELL, validated = true, wifiUp = true, cellUp = true, cellLevel = 1),
        )
        assertEquals(NetMode.OVERLAP, p.mode)
        assertEquals(
            listOf(0L to NetLane.CELL, 0L to NetLane.WIFI, 2_000L to NetLane.CELL),
            lanes(p),
        )
        assertFalse(p.wantCellular(0L))
    }

    @Test
    fun weakCellWithoutWifiStaysNormal() {
        val p = policy(NetSnapshot(NetTransport.CELL, validated = true, cellUp = true, cellLevel = 0))
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun noNetworkIsOffline() {
        val p = policy(NetSnapshot())
        assertEquals(NetMode.OFFLINE, p.mode)
        p.onSnapshot(wifiGood, 0L)
        assertEquals(NetMode.NORMAL, p.mode)
    }

    @Test
    fun unvalidatedWifiUsesCellWhenUp() {
        val p = policy(wifiGood.copy(validated = false))
        assertEquals(NetMode.CELL_FIRST, p.mode)
        assertTrue(p.wantCellular(0L))
        val down = policy(wifiGood.copy(validated = false, cellUp = false))
        assertEquals(NetMode.OFFLINE, down.mode)
        assertTrue(down.wantCellular(0L))
    }

    @Test
    fun mobileDataOffOnlyNormalOrOffline() {
        val p = NetworkLanePolicy().apply { mobileDataAllowed = false }
        p.onSnapshot(wifiGood.copy(wifiRssi = -85), 0L)
        assertEquals(NetMode.NORMAL, p.mode)
        assertFalse(p.wantCellular(0L))
        p.onRaceResult(failed)
        p.onRaceResult(failed)
        assertEquals(NetMode.NORMAL, p.mode)
        p.onSnapshot(wifiGood.copy(validated = false), 0L)
        assertEquals(NetMode.OFFLINE, p.mode)
    }
}
