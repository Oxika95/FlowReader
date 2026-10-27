package com.personal.flowreader.plugin.sync

/**
 * Whether a sync should be an initial full pull (site overwrites local lists) vs progressive
 * three-way merge. Pure so JVM tests cover the gate without Android.
 */
object SyncPullKind {
    /**
     * Initial pull when no syncable list has a baseline yet and every syncable list is empty
     * locally. If the user already has local memberships without baselines, progressive union
     * applies instead (does not wipe local-only stories).
     */
    fun needsInitialPull(
        syncableListIds: Collection<String>,
        baselineListIds: Set<String>,
        localWorkCounts: Map<String, Int>,
    ): Boolean {
        if (syncableListIds.isEmpty()) return false
        if (syncableListIds.any { it in baselineListIds }) return false
        return syncableListIds.all { (localWorkCounts[it] ?: 0) == 0 }
    }
}
