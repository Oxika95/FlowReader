package com.personal.flowreader.tts

/** Interruptions longer than this stay paused: the listener has likely lost the thread. */
const val FOCUS_RESUME_MAX_MS = 5 * 60_000L

/** Outcome of regaining audio focus after a transient loss that paused playback. */
data class FocusGain(val elapsedMs: Long, val resume: Boolean)

/**
 * Tracks whether a transient audio focus loss paused playback, so focus gain can resume it.
 * Any user play/pause/stop must [clear] it.
 */
class FocusResume(private val maxMs: Long = FOCUS_RESUME_MAX_MS) {
    private var lostAtMs: Long? = null

    val armed: Boolean get() = lostAtMs != null

    /** Repeated losses (ring, then the call) keep the first timestamp. */
    fun onTransientLoss(wasPlaying: Boolean, nowMs: Long) {
        if (lostAtMs == null && wasPlaying) lostAtMs = nowMs
    }

    /** Null when no interruption paused playback. Always disarms. */
    fun onGain(nowMs: Long): FocusGain? {
        val at = lostAtMs ?: return null
        lostAtMs = null
        val elapsed = (nowMs - at).coerceAtLeast(0)
        return FocusGain(elapsed, resume = elapsed <= maxMs)
    }

    fun clear() {
        lostAtMs = null
    }
}
