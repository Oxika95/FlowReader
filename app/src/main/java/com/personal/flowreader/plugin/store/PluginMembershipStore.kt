package com.personal.flowreader.plugin.store

import com.personal.flowreader.plugin.api.PluginJson
import com.personal.flowreader.plugin.api.PluginStat
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.plugin.api.objects
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local list membership (Follow / Favorite / ...) per plugin, in `{dataDir}/_lists/{listId}.txt`.
 * One work per line: `workId \t title \t url \t author \t cover \t subtitle \t badges \t stats \t group`
 * (badges and stats as JSON arrays; browse-list rows show them).
 */
object PluginMembershipStore {
    private val SAFE_LIST = Regex("[^A-Za-z0-9._-]")

    fun file(dataDir: File, listId: String): File =
        File(File(dataDir, "_lists"), "${listId.replace(SAFE_LIST, "_")}.txt")

    fun read(dataDir: File, listId: String): List<PluginWork> {
        val f = file(dataDir, listId)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split('\t')
            val id = p.getOrNull(0)?.let(PluginSessionStore::unescape)?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val (badges, tones) = PluginJson.badges(jsonArray(p.getOrNull(6)))
            PluginWork(
                id = id,
                title = p.getOrNull(1)?.let(PluginSessionStore::unescape).orEmpty().ifBlank { id },
                url = p.getOrNull(2)?.let(PluginSessionStore::unescape).orEmpty(),
                author = p.getOrNull(3)?.let(PluginSessionStore::unescape).orEmpty(),
                cover = p.getOrNull(4)?.let(PluginSessionStore::unescape).orEmpty(),
                subtitle = p.getOrNull(5)?.let(PluginSessionStore::unescape).orEmpty(),
                badges = badges,
                stats = jsonArray(p.getOrNull(7))?.objects().orEmpty().map {
                    PluginStat(it.optString("icon"), it.optString("value"), it.optString("label"))
                },
                badgeTones = tones,
                group = p.getOrNull(8)?.let(PluginSessionStore::unescape).orEmpty(),
            )
        }
    }

    private fun jsonArray(raw: String?): JSONArray? =
        raw?.let(PluginSessionStore::unescape)?.takeIf { it.isNotBlank() }?.let { runCatching { JSONArray(it) }.getOrNull() }

    fun write(dataDir: File, listId: String, works: List<PluginWork>) {
        val f = file(dataDir, listId)
        f.parentFile?.mkdirs()
        f.writeText(
            works.distinctBy { it.id }.joinToString("\n") { w ->
                val badges = if (w.badges.isEmpty()) "" else PluginJson.badgesToJson(w.badges, w.badgeTones).toString()
                val stats = if (w.stats.isEmpty()) {
                    ""
                } else {
                    JSONArray(w.stats.map { JSONObject().put("icon", it.icon).put("value", it.value).put("label", it.label) }).toString()
                }
                listOf(w.id, w.title, w.url, w.author, w.cover, w.subtitle, badges, stats, w.group)
                    .joinToString("\t") { PluginSessionStore.escape(it) }
            },
        )
    }

    fun workIds(dataDir: File, listId: String): Set<String> = read(dataDir, listId).map { it.id }.toSet()

    /** Put [work] first in the list (replacing an older row for the same id). */
    fun upsert(dataDir: File, listId: String, work: PluginWork) {
        write(dataDir, listId, listOf(work) + read(dataDir, listId).filter { it.id != work.id })
    }

    fun remove(dataDir: File, listId: String, workId: String) {
        val current = read(dataDir, listId)
        if (current.none { it.id == workId }) return
        write(dataDir, listId, current.filter { it.id != workId })
    }

    fun removeFromAll(dataDir: File, listIds: Collection<String>, workId: String) {
        listIds.forEach { remove(dataDir, it, workId) }
    }

    fun listsContaining(dataDir: File, listIds: Collection<String>, workId: String): Set<String> =
        listIds.filter { workId in workIds(dataDir, it) }.toSet()
}
