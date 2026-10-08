package com.personal.flowreader.plugin.updates

import com.personal.flowreader.plugin.api.PluginChapterRef

/** Pure rules for the background new-chapter check. */
object UpdateDiff {
    /**
     * Chapters in [newToc] the host did not have, by URL. Re-titled, reordered or removed chapters
     * are not new. When no old URL survives (the site changed its URL scheme), only chapters past
     * the old ToC length count.
     */
    fun newChapters(oldUrls: List<String>, newToc: List<PluginChapterRef>): List<PluginChapterRef> {
        if (oldUrls.isEmpty()) return emptyList()
        val old = oldUrls.toHashSet()
        if (newToc.none { it.url in old }) return newToc.drop(oldUrls.size)
        return newToc.filter { it.url !in old }
    }

    /** Effective bell state: the stored choice, else on while the story is on a syncable list. */
    fun notifyOn(stored: Boolean?, listedIn: Set<String>, syncableLists: Set<String>): Boolean =
        stored ?: listedIn.any { it in syncableLists }

    /** Notification title: "3 new chapters" / "New chapter". */
    fun headline(count: Int): String = if (count == 1) "New chapter" else "$count new chapters"
}
