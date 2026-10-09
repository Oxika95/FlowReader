package com.personal.flowreader.plugin.repo

import com.personal.flowreader.plugin.api.compareVersions

/** A newer repository version of an installed plugin. */
data class PluginVersionUpdate(val id: String, val name: String, val from: String, val to: String) {
    /** Remembered once notified so the same version does not notify again. */
    val key: String get() = "$id@$to"
}

object PluginVersionUpdates {
    /** Newest entry per plugin id across [repos] (repositories that failed to load are skipped). */
    fun latestById(repos: List<PluginRepo>): List<RepoPlugin> =
        repos.flatMap { it.index?.plugins.orEmpty() }
            .groupBy { it.id }
            .map { (_, entries) -> entries.maxWith { a, b -> compareVersions(a.version, b.version) } }

    /** Installed plugins ([installed]: id to version) with a newer, installable entry in [catalog]. */
    fun available(
        installed: Map<String, String>,
        catalog: List<RepoPlugin>,
        compatible: (RepoPlugin) -> Boolean,
    ): List<PluginVersionUpdate> = catalog.mapNotNull { entry ->
        val current = installed[entry.id] ?: return@mapNotNull null
        if (compareVersions(entry.version, current) <= 0 || !compatible(entry)) return@mapNotNull null
        PluginVersionUpdate(entry.id, entry.name, current, entry.version)
    }.sortedBy { it.name.lowercase() }

    /**
     * Notified keys still newer than what is installed. Installed or uninstalled versions are
     * forgotten; repositories that failed to load this run do not reset the rest.
     */
    fun stillPending(notified: Set<String>, installed: Map<String, String>): Set<String> = notified.filterTo(HashSet()) { key ->
        val current = installed[key.substringBefore('@')] ?: return@filterTo false
        compareVersions(key.substringAfter('@', ""), current) > 0
    }
}
