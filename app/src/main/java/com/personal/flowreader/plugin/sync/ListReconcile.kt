package com.personal.flowreader.plugin.sync

/** What one sync run does to one list. [baseline] is the state both sides hold once it is applied. */
data class ListPlan(
    val addLocal: Set<String>,
    val removeLocal: Set<String>,
    val pushAdd: Set<String>,
    val pushRemove: Set<String>,
    val baseline: Set<String>,
    /** Remote removals were skipped because the site list looked truncated. */
    val guarded: Boolean,
)

object ListReconcile {
    /** The "under half" guard needs at least this many synced stories; empty always trips it. */
    const val GUARD_MIN_BASELINE = 4

    /**
     * Three-way merge of story ids. Without a [baseline] (first sync) the result is the union and
     * nothing is removed. A side's removal applies only to ids that were in the baseline.
     * [pendingAdd] / [pendingRemove] are app changes still queued for the site; the site's old
     * state of those ids is not applied back.
     */
    fun plan(
        local: Set<String>,
        remote: Set<String>,
        baseline: Set<String>?,
        pendingAdd: Set<String> = emptySet(),
        pendingRemove: Set<String> = emptySet(),
    ): ListPlan {
        if (baseline == null) {
            val addLocal = remote - local - pendingRemove
            return ListPlan(addLocal, emptySet(), local - remote, emptySet(), local + addLocal, guarded = false)
        }
        val guarded = looksTruncated(remote, baseline)
        val addLocal = (remote - baseline) - local - pendingRemove
        val removeLocal = if (guarded) emptySet() else ((baseline - remote) intersect local) - pendingAdd
        val pushAdd = (local - baseline) - remote
        val pushRemove = (baseline - local) intersect remote
        return ListPlan(
            addLocal = addLocal,
            removeLocal = removeLocal,
            pushAdd = pushAdd,
            pushRemove = pushRemove,
            baseline = (local + addLocal) - removeLocal,
            guarded = guarded,
        )
    }

    fun looksTruncated(remote: Set<String>, baseline: Set<String>): Boolean {
        if (baseline.isEmpty()) return false
        if (remote.isEmpty()) return true
        return baseline.size >= GUARD_MIN_BASELINE && remote.size * 2 < baseline.size
    }
}
