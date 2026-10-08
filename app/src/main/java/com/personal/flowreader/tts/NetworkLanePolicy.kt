package com.personal.flowreader.tts

/** Network an Edge race attempt is sent on; [DEFAULT] follows the system default network. */
enum class NetLane { DEFAULT, WIFI, CELL }

enum class NetTransport { NONE, WIFI, CELL, OTHER }

enum class NetMode { NORMAL, OVERLAP, CELL_FIRST, OFFLINE }

data class LaneAttempt(val startMs: Long, val lane: NetLane)

/** Outcome of one Edge race, fed back into [NetworkLanePolicy]. */
data class RaceReport(
    /** Lane of the winning attempt; null when the race failed. */
    val winner: NetLane?,
    /** Winner's first audio byte, ms after the race started. */
    val firstAudioMs: Long?,
    /** Planned start offset of the winner; > 0 means a delayed backup had to win. */
    val winnerStartMs: Long? = null,
    /** Winner's first audio byte, ms after that attempt itself started. */
    val winnerLatencyMs: Long? = null,
    val totalMs: Long,
    val started: Int,
    /** Lanes of attempts that errored (not cancelled losers). */
    val failed: List<NetLane>,
    /** Edge refused the text; says nothing about the network. */
    val contentError: Boolean = false,
)

data class NetSnapshot(
    /** Transport of the system default network. */
    val transport: NetTransport = NetTransport.NONE,
    /** Default network passed Android's internet validation. */
    val validated: Boolean = false,
    /** A Wi-Fi network with internet is connected (bindable). */
    val wifiUp: Boolean = false,
    /** A cellular network with internet is up (bindable). */
    val cellUp: Boolean = false,
    /** Wi-Fi RSSI in dBm. */
    val wifiRssi: Int? = null,
    /** Cellular signal level 0 (none) … 4 (great). */
    val cellLevel: Int? = null,
)

/**
 * Chooses which network each Edge race attempt uses.
 *
 * - [NetMode.NORMAL]: staggered attempts on the default network.
 * - [NetMode.OVERLAP]: weak default signal; Wi-Fi and cellular start together.
 * - [NetMode.CELL_FIRST]: repeated stalls on Wi-Fi (or Wi-Fi without internet); cellular first,
 *   with an overlap probe every [Thresholds.probeEvery] races so Wi-Fi can win its way back.
 * - [NetMode.OFFLINE]: no usable network; the caller waits instead of sending.
 *
 * Backups start after [hedgeDelayMs]: the 90th percentile of recent first-audio latencies, so a
 * healthy link rarely sends more than one request. A stall is a race that failed or that a
 * delayed backup had to win.
 *
 * Not thread-safe; callers serialize access.
 */
class NetworkLanePolicy(private val t: Thresholds = Thresholds()) {
    data class Thresholds(
        val overlapEnterRssi: Int = -75,
        val overlapExitRssi: Int = -68,
        val warmRssi: Int = -70,
        val weakCellLevel: Int = 1,
        val stallsToCellFirst: Int = 2,
        val wifiWinsToRecover: Int = 3,
        val probeEvery: Int = 4,
        val hedgeDefaultMs: Long = 2_000L,
        val hedgeMinMs: Long = 1_000L,
        val hedgeMaxMs: Long = 3_000L,
        val hedgePercentile: Double = 0.9,
        val hedgeWindow: Int = 20,
        val hedgeMinSamples: Int = 5,
        val attempts: Int = 3,
        val cellLingerMs: Long = 30_000L,
    )

    var mobileDataAllowed: Boolean = true
        set(value) {
            field = value
            if (!value) {
                cellFirst = false
                cellWantedUntil = 0L
            }
            recompute()
        }

    var mode: NetMode = NetMode.NORMAL
        private set

    private var snap = NetSnapshot()
    private var weakWifi = false
    private var cellFirst = false
    private var stalls = 0
    private var wifiWins = 0
    private var cellFirstRaces = 0
    private var cellWantedUntil = 0L
    private val latencies = ArrayDeque<Long>()

    /** Delay before each backup attempt. */
    fun hedgeDelayMs(): Long {
        if (latencies.size < t.hedgeMinSamples) return t.hedgeDefaultMs
        val sorted = latencies.sorted()
        val idx = ((sorted.size - 1) * t.hedgePercentile).toInt()
        return sorted[idx].coerceIn(t.hedgeMinMs, t.hedgeMaxMs)
    }

    fun onSnapshot(s: NetSnapshot, nowMs: Long) {
        snap = s
        val rssi = s.wifiRssi
        weakWifi = s.transport == NetTransport.WIFI && rssi != null &&
            if (weakWifi) rssi <= t.overlapExitRssi else rssi < t.overlapEnterRssi
        if (s.transport != NetTransport.WIFI) cellFirst = false
        recompute()
        val wantNow = mobileDataAllowed && s.transport == NetTransport.WIFI && (
            mode == NetMode.OVERLAP ||
                mode == NetMode.CELL_FIRST ||
                stalls > 0 ||
                !s.validated ||
                (rssi != null && rssi < t.warmRssi)
            )
        if (wantNow) cellWantedUntil = nowMs + t.cellLingerMs
    }

    fun onRaceResult(r: RaceReport) {
        if (r.contentError) return
        r.winnerLatencyMs?.let {
            latencies.addLast(it)
            while (latencies.size > t.hedgeWindow) latencies.removeFirst()
        }
        val stalled = r.winner == null || (r.winnerStartMs ?: 0L) > 0L
        when (mode) {
            NetMode.NORMAL, NetMode.OVERLAP -> {
                if (!stalled) {
                    stalls = 0
                } else {
                    stalls++
                    if (stalls >= t.stallsToCellFirst &&
                        mobileDataAllowed &&
                        snap.transport == NetTransport.WIFI
                    ) {
                        cellFirst = true
                        wifiWins = 0
                        cellFirstRaces = 0
                    }
                }
            }
            NetMode.CELL_FIRST -> if (cellFirst) {
                cellFirstRaces++
                if (r.winner == NetLane.WIFI) {
                    wifiWins++
                    if (wifiWins >= t.wifiWinsToRecover) {
                        cellFirst = false
                        stalls = 0
                    }
                } else if (NetLane.WIFI in r.failed) {
                    wifiWins = 0
                }
            }
            NetMode.OFFLINE -> Unit
        }
        recompute()
    }

    /** Hold a cellular network request (warm-up, then linger after Wi-Fi recovers). */
    fun wantCellular(nowMs: Long): Boolean = mobileDataAllowed && nowMs < cellWantedUntil

    fun plan(): List<LaneAttempt> {
        val d = hedgeDelayMs()
        return when (mode) {
            NetMode.NORMAL, NetMode.OFFLINE -> normalPlan()
            NetMode.OVERLAP -> when {
                snap.transport == NetTransport.CELL -> listOf(
                    LaneAttempt(0L, NetLane.CELL),
                    LaneAttempt(0L, NetLane.WIFI),
                    LaneAttempt(d, NetLane.CELL),
                )
                snap.cellUp -> listOf(
                    LaneAttempt(0L, NetLane.WIFI),
                    LaneAttempt(0L, NetLane.CELL),
                    LaneAttempt(d, NetLane.CELL),
                )
                else -> normalPlan()
            }
            NetMode.CELL_FIRST -> when {
                !snap.cellUp -> normalPlan()
                cellFirst && snap.wifiUp && (cellFirstRaces + 1) % t.probeEvery == 0 -> listOf(
                    LaneAttempt(0L, NetLane.WIFI),
                    LaneAttempt(0L, NetLane.CELL),
                    LaneAttempt(d, NetLane.CELL),
                )
                else -> listOf(
                    LaneAttempt(0L, NetLane.CELL),
                    LaneAttempt(d, NetLane.WIFI),
                    LaneAttempt(2 * d, NetLane.CELL),
                )
            }
        }
    }

    private fun normalPlan(): List<LaneAttempt> =
        RaceSchedule.staggered(t.attempts, hedgeDelayMs()).map { LaneAttempt(it, NetLane.DEFAULT) }

    private fun recompute() {
        val s = snap
        val online = s.transport != NetTransport.NONE && s.validated
        val weakCellWithWifi = s.transport == NetTransport.CELL &&
            s.wifiUp &&
            (s.cellLevel ?: Int.MAX_VALUE) <= t.weakCellLevel
        mode = when {
            !mobileDataAllowed -> if (online) NetMode.NORMAL else NetMode.OFFLINE
            !online -> if (s.transport == NetTransport.WIFI && s.cellUp) NetMode.CELL_FIRST else NetMode.OFFLINE
            cellFirst -> NetMode.CELL_FIRST
            weakWifi || weakCellWithWifi -> NetMode.OVERLAP
            else -> NetMode.NORMAL
        }
    }
}
