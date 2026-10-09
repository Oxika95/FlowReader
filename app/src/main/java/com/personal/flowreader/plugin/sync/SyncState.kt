package com.personal.flowreader.plugin.sync

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A change made in the app that still has to reach the site. */
sealed interface SyncOp {
    val workId: String
    val attempts: Int

    /** Outbox key: a newer op for the same key replaces the older one. */
    val key: String

    data class Membership(
        override val workId: String,
        val listId: String,
        val on: Boolean,
        override val attempts: Int = 0,
    ) : SyncOp {
        override val key: String get() = "list:$listId:$workId"
    }

    data class Position(
        override val workId: String,
        val chapterUrl: String,
        val chapterTitle: String,
        override val attempts: Int = 0,
    ) : SyncOp {
        override val key: String get() = "pos:$workId"
    }
}

/** Both the app and the site moved this story's reading chapter since the last sync. */
data class PositionConflict(val workId: String, val localUrl: String, val remoteUrl: String)

/**
 * One plugin's two-way sync memory (`{dataDir}/sync.json`). [lists] and [positions] are the
 * baseline both sides agreed on at the last sync; a list missing from [lists] has never synced.
 */
data class SyncState(
    val lists: Map<String, Set<String>> = emptyMap(),
    val positions: Map<String, String> = emptyMap(),
    val outbox: List<SyncOp> = emptyList(),
    val conflicts: Map<String, PositionConflict> = emptyMap(),
    val lastSyncedAt: Long = 0L,
    /** Lists whose site removals were skipped at the last sync (truncated-list guard). */
    val guardedLists: Set<String> = emptySet(),
) {
    fun enqueue(op: SyncOp): SyncState = copy(outbox = outbox.filter { it.key != op.key } + op)

    /** Drops [op] unless a newer op replaced it meanwhile. */
    fun settle(op: SyncOp): SyncState = copy(outbox = outbox.filter { it != op })

    fun retry(op: SyncOp, maxAttempts: Int): SyncState = copy(
        outbox = outbox.mapNotNull {
            when {
                it != op -> it
                op.attempts + 1 >= maxAttempts -> null
                else -> it.bumped()
            }
        },
    )

    /**
     * Baseline for [listId] after a sync planned [planned]: pushes still queued are left out, so
     * the next run tries them again instead of reading them as changes on the site.
     */
    fun listBaseline(listId: String, planned: Set<String>): Set<String> {
        val pending = outbox.filterIsInstance<SyncOp.Membership>().filter { it.listId == listId }
        return planned - pending.filter { it.on }.map { it.workId }.toSet() +
            pending.filterNot { it.on }.map { it.workId }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("lists", JSONObject().apply { lists.forEach { (id, works) -> put(id, JSONArray(works.sorted())) } })
        put("positions", JSONObject(positions))
        put("outbox", JSONArray(outbox.map(::opJson)))
        put("conflicts", JSONArray(conflicts.values.map { JSONObject().put("id", it.workId).put("local", it.localUrl).put("remote", it.remoteUrl) }))
        put("lastSyncedAt", lastSyncedAt)
        put("guardedLists", JSONArray(guardedLists.sorted()))
    }

    companion object {
        fun parse(o: JSONObject): SyncState {
            val lists = o.optJSONObject("lists")?.let { obj ->
                obj.keys().asSequence().associateWith { id -> strings(obj.optJSONArray(id)).toSet() }
            }.orEmpty()
            val positions = o.optJSONObject("positions")?.let { obj ->
                obj.keys().asSequence().associateWith { obj.optString(it) }.filterValues { it.isNotBlank() }
            }.orEmpty()
            val outbox = objects(o.optJSONArray("outbox")).mapNotNull(::parseOp)
            val conflicts = objects(o.optJSONArray("conflicts")).mapNotNull { c ->
                val id = c.optString("id")
                if (id.isBlank()) null else PositionConflict(id, c.optString("local"), c.optString("remote"))
            }.associateBy { it.workId }
            return SyncState(
                lists = lists,
                positions = positions,
                outbox = outbox,
                conflicts = conflicts,
                lastSyncedAt = o.optLong("lastSyncedAt"),
                guardedLists = strings(o.optJSONArray("guardedLists")).toSet(),
            )
        }

        private fun opJson(op: SyncOp): JSONObject = when (op) {
            is SyncOp.Membership -> JSONObject().put("kind", "list").put("work", op.workId)
                .put("list", op.listId).put("on", op.on).put("attempts", op.attempts)
            is SyncOp.Position -> JSONObject().put("kind", "pos").put("work", op.workId)
                .put("url", op.chapterUrl).put("title", op.chapterTitle).put("attempts", op.attempts)
        }

        private fun parseOp(o: JSONObject): SyncOp? {
            val work = o.optString("work").ifBlank { return null }
            return when (o.optString("kind")) {
                "list" -> SyncOp.Membership(work, o.optString("list").ifBlank { return null }, o.optBoolean("on"), o.optInt("attempts"))
                "pos" -> SyncOp.Position(work, o.optString("url").ifBlank { return null }, o.optString("title"), o.optInt("attempts"))
                else -> null
            }
        }

        private fun strings(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }

        private fun objects(a: JSONArray?): List<JSONObject> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
}

private fun SyncOp.bumped(): SyncOp = when (this) {
    is SyncOp.Membership -> copy(attempts = attempts + 1)
    is SyncOp.Position -> copy(attempts = attempts + 1)
}

/** `sync.json` per plugin; every change is a locked read-modify-write. */
object SyncStateStore {
    private val locks = HashMap<String, Any>()

    private fun lock(dataDir: File): Any = synchronized(locks) { locks.getOrPut(dataDir.absolutePath) { Any() } }

    fun file(dataDir: File): File = File(dataDir, "sync.json")

    fun read(dataDir: File): SyncState = synchronized(lock(dataDir)) { load(dataDir) }

    fun update(dataDir: File, change: (SyncState) -> SyncState): SyncState = synchronized(lock(dataDir)) {
        val next = change(load(dataDir))
        val f = file(dataDir)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(next.toJson().toString())
        if (!tmp.renameTo(f)) {
            f.delete()
            tmp.renameTo(f)
        }
        next
    }

    fun clear(dataDir: File) = synchronized(lock(dataDir)) { file(dataDir).delete() }

    private fun load(dataDir: File): SyncState {
        val f = file(dataDir)
        if (!f.exists()) return SyncState()
        return runCatching { SyncState.parse(JSONObject(f.readText())) }.getOrDefault(SyncState())
    }
}
