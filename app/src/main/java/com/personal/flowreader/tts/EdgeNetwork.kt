package com.personal.flowreader.tts

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import kotlin.math.abs

/** Picks Edge race lanes from live network state ([NetworkMonitor]) and race history ([NetworkLanePolicy]). */
class EdgeNetwork(context: Context, base: OkHttpClient) {
    private val monitor = NetworkMonitor(context, base)
    private val policy = NetworkLanePolicy()
    private val lock = Any()
    private var lastSnap = NetSnapshot()
    private var loggedMode: NetMode? = null
    private var loggedRssi: Int? = null
    private var loggedCell: Int? = null

    fun start() = monitor.start()

    fun stop() = monitor.stop()

    fun setMobileDataAllowed(allowed: Boolean) {
        synchronized(lock) {
            policy.mobileDataAllowed = allowed
            refreshLocked()
        }
    }

    /** Drop the cellular request (playback paused or stopped). */
    fun releaseCellular() = monitor.setCellularWanted(false)

    /** Blocking binder reads; call off the main thread. */
    fun lanes(): List<RaceLane> {
        val plan = synchronized(lock) {
            refreshLocked()
            policy.plan()
        }
        return plan.map { RaceLane(it.startMs, it.lane, monitor.clientFor(it.lane)) }
    }

    fun report(r: RaceReport) {
        synchronized(lock) {
            policy.onRaceResult(r)
            logStateLocked(lastSnap, "race")
        }
    }

    fun isOffline(): Boolean = synchronized(lock) {
        refreshLocked()
        policy.mode == NetMode.OFFLINE
    }

    /** Suspends until some network can carry Edge traffic; rechecks on every callback and every few seconds. */
    suspend fun awaitUsable() {
        while (isOffline()) {
            val seen = monitor.changes.value
            withTimeoutOrNull(RECHECK_MS) { monitor.changes.first { it != seen } }
        }
    }

    private fun refreshLocked() {
        val now = SystemClock.elapsedRealtime()
        val snap = monitor.snapshot()
        lastSnap = snap
        policy.onSnapshot(snap, now)
        monitor.setCellularWanted(policy.wantCellular(now))
        logStateLocked(snap, "poll")
    }

    private fun logStateLocked(s: NetSnapshot, why: String) {
        val mode = policy.mode
        val rssi = s.wifiRssi
        val rssiMoved = rssi != loggedRssi &&
            (rssi == null || loggedRssi == null || abs(rssi - (loggedRssi ?: 0)) >= RSSI_LOG_STEP)
        if (mode == loggedMode && !rssiMoved && s.cellLevel == loggedCell) return
        loggedMode = mode
        loggedRssi = rssi
        loggedCell = s.cellLevel
        SynthDebugLog.append(
            "net $why mode=$mode default=${s.transport.name.lowercase()} validated=${s.validated} " +
                "wifiUp=${s.wifiUp} rssi=${rssi ?: "-"} cellUp=${s.cellUp} cellLevel=${s.cellLevel ?: "-"} " +
                "cellHeld=${monitor.cellularHeld} mobileData=${policy.mobileDataAllowed}",
        )
    }

    private companion object {
        const val RECHECK_MS = 5_000L
        const val RSSI_LOG_STEP = 3
    }
}
