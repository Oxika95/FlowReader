package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginList
import com.personal.flowreader.plugin.api.PluginManifest

/** Sync choice that imports every syncable list in one pass. */
const val SYNC_ALL_LISTS = "*"

/** Lists a sync of [listId] imports: one list, or every syncable list for [SYNC_ALL_LISTS]. */
fun syncTargets(manifest: PluginManifest, listId: String): List<PluginList> =
    if (listId == SYNC_ALL_LISTS) manifest.lists.filter { it.syncable }
    else listOfNotNull(manifest.list(listId))

/** Sync choice offered after sign-in and in the account sheet: all lists when there are several. */
fun defaultSyncChoice(manifest: PluginManifest): String? {
    val syncable = manifest.lists.filter { it.syncable }
    return when {
        syncable.size > 1 -> SYNC_ALL_LISTS
        else -> syncable.firstOrNull()?.id
    }
}

/** "Follow", "Follow and Favorite", "Follow, Favorite and Read Later". */
fun syncTitle(lists: List<PluginList>): String {
    val titles = lists.map { it.title }
    return when (titles.size) {
        0 -> ""
        1 -> titles[0]
        else -> titles.dropLast(1).joinToString(", ") + " and " + titles.last()
    }
}
