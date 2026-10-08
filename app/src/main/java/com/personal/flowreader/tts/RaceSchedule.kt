package com.personal.flowreader.tts

/**
 * Staggered (hedged) Edge race: attempt `i` may start at `startMs[i]` after the race began, in
 * order. Backups (`startMs > 0`) only start while no in-flight attempt is receiving audio; when
 * nothing is in flight (every started attempt failed) the next attempt starts immediately.
 */
object RaceSchedule {
    /**
     * Indices of attempts to start now.
     * @param startMs per-attempt start offsets, ascending.
     * @param started attempts already started (always a prefix).
     * @param inFlight started attempts that have not finished.
     * @param audioInFlight true when an in-flight attempt has received audio.
     */
    fun due(
        startMs: List<Long>,
        elapsedMs: Long,
        started: Int,
        inFlight: Int,
        audioInFlight: Boolean,
    ): List<Int> {
        val out = ArrayList<Int>((startMs.size - started).coerceAtLeast(0))
        var running = inFlight
        val audio = audioInFlight && inFlight > 0
        var i = started
        while (i < startMs.size) {
            val at = startMs[i]
            if (at > 0L && audio) break
            if (at > elapsedMs && running > 0) break
            out += i
            running++
            i++
        }
        return out
    }

    /** Offset of the next unstarted attempt, or null when all have started. */
    fun nextStartMs(startMs: List<Long>, started: Int): Long? = startMs.getOrNull(started)

    /** Normal plan offsets: one request, backups after [delayMs] and 2 × [delayMs]. */
    fun staggered(attempts: Int, delayMs: Long): List<Long> = List(attempts) { it * delayMs }
}
