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
        /** Present when the plugin has `locusSync`; ignored for chapter-only plugins. */
        val blockIndex: Int = 0,
        val charOffset: Int = 0,
        val anchorText: String = "",
        val fraction: Float = 0f,
        val positionAt: Long = 0L,
    ) : SyncOp {
        override val key: String get() = "pos:$workId"
        val hasLocus: Boolean
            get() = blockIndex > 0 || charOffset > 0 || anchorText.isNotEmpty() || fraction > 0f || positionAt > 0L
    }
}

/** Both the app and the site moved this story's reading chapter since the last sync. */
data class PositionConflict(
    val workId: String,
    val localUrl: String,
    val remoteUrl: String,
    val localLabel: String = "",
    val remoteLabel: String = "",
)

/**
 * The site's reading chapter for a story whose saved ToC doesn't have it yet (never opened, or
 * older than the chapter). Applied when the ToC is next stored; [overwrite] skips the merge.
 */
data class PendingPosition(
    val workId: String,
    val chapterUrl: String,
    val chapterTitle: String,
    val overwrite: Boolean = false,
    val blockIndex: Int = 0,
    val charOffset: Int = 0,
    val anchorText: String = "",
    val fraction: Float = 0f,
    val updatedAt: Long = 0L,
)

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
    val pending: Map<String, PendingPosition> = emptyMap(),
) {
    fun enqueue(op: SyncOp): SyncState = copy(outbox = outbox.filter { it.key != op.key } + op)

    /**
     * Queues a position push and clears the story's open conflict. The baseline stays put until
     * the push succeeds ([settle]); saving it now would let a dropped push read as a site change.
     */
    fun enqueuePosition(op: SyncOp.Position): SyncState = copy(conflicts = conflicts - op.workId).enqueue(op)

    /**
     * Drops [op] unless a newer op replaced it meanwhile. A pushed position means the site now
     * holds that chapter, so it becomes the baseline even if a newer op replaced the queued one.
     */
    fun settle(op: SyncOp): SyncState {
        val settled = copy(outbox = outbox.filter { it != op })
        return if (op is SyncOp.Position) {
            settled.copy(positions = settled.positions + (op.workId to PositionBaseline.encode(op)))
        } else {
            settled
        }
    }

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

    /**
     * The story was removed from this device only. Its list baselines and queued list changes go
     * too, so the next sync reads the site's lists as unchanged instead of pushing a removal.
     */
    fun forgetStoryLists(workId: String): SyncState = copy(
        lists = lists.mapValues { (_, works) -> works - workId },
        outbox = outbox.filterNot { it is SyncOp.Membership && it.workId == workId },
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("lists", JSONObject().apply { lists.forEach { (id, works) -> put(id, JSONArray(works.sorted())) } })
        put("positions", JSONObject(positions))
        put("outbox", JSONArray(outbox.map(::opJson)))
        put(
            "conflicts",
            JSONArray(
                conflicts.values.map {
                    JSONObject()
                        .put("id", it.workId)
                        .put("local", it.localUrl)
                        .put("remote", it.remoteUrl)
                        .put("localLabel", it.localLabel)
                        .put("remoteLabel", it.remoteLabel)
                },
            ),
        )
        put("lastSyncedAt", lastSyncedAt)
        put("guardedLists", JSONArray(guardedLists.sorted()))
        put(
            "pending",
            JSONArray(
                pending.values.map {
                    JSONObject()
                        .put("id", it.workId)
                        .put("url", it.chapterUrl)
                        .put("title", it.chapterTitle)
                        .put("overwrite", it.overwrite)
                        .put("blockIndex", it.blockIndex)
                        .put("charOffset", it.charOffset)
                        .put("anchorText", it.anchorText)
                        .put("fraction", it.fraction.toDouble())
                        .put("updatedAt", it.updatedAt)
                },
            ),
        )
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
                if (id.isBlank()) {
                    null
                } else {
                    PositionConflict(
                        id,
                        c.optString("local"),
                        c.optString("remote"),
                        c.optString("localLabel"),
                        c.optString("remoteLabel"),
                    )
                }
            }.associateBy { it.workId }
            val pending = objects(o.optJSONArray("pending")).mapNotNull { p ->
                val id = p.optString("id").ifBlank { return@mapNotNull null }
                val url = p.optString("url").ifBlank { return@mapNotNull null }
                PendingPosition(
                    id,
                    url,
                    p.optString("title"),
                    p.optBoolean("overwrite"),
                    p.optInt("blockIndex"),
                    p.optInt("charOffset"),
                    p.optString("anchorText"),
                    p.optDouble("fraction", 0.0).toFloat(),
                    p.optLong("updatedAt"),
                )
            }.associateBy { it.workId }
            return SyncState(
                lists = lists,
                positions = positions,
                outbox = outbox,
                conflicts = conflicts,
                lastSyncedAt = o.optLong("lastSyncedAt"),
                guardedLists = strings(o.optJSONArray("guardedLists")).toSet(),
                pending = pending,
            )
        }

        private fun opJson(op: SyncOp): JSONObject = when (op) {
            is SyncOp.Membership -> JSONObject().put("kind", "list").put("work", op.workId)
                .put("list", op.listId).put("on", op.on).put("attempts", op.attempts)
            is SyncOp.Position -> JSONObject().put("kind", "pos").put("work", op.workId)
                .put("url", op.chapterUrl).put("title", op.chapterTitle).put("attempts", op.attempts)
                .put("blockIndex", op.blockIndex).put("charOffset", op.charOffset)
                .put("anchorText", op.anchorText).put("fraction", op.fraction.toDouble())
                .put("positionAt", op.positionAt)
        }

        private fun parseOp(o: JSONObject): SyncOp? {
            val work = o.optString("work").ifBlank { return null }
            return when (o.optString("kind")) {
                "list" -> SyncOp.Membership(work, o.optString("list").ifBlank { return null }, o.optBoolean("on"), o.optInt("attempts"))
                "pos" -> SyncOp.Position(
                    work,
                    o.optString("url").ifBlank { return null },
                    o.optString("title"),
                    o.optInt("attempts"),
                    o.optInt("blockIndex"),
                    o.optInt("charOffset"),
                    o.optString("anchorText"),
                    o.optDouble("fraction", 0.0).toFloat(),
                    o.optLong("positionAt"),
                )
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

/**
 * Baseline stored in [SyncState.positions]: a bare chapter URL for chapter-only plugins, or a
 * JSON object with locus fields when the plugin has `locusSync`. Reading always yields the URL.
 */
object PositionBaseline {
    fun encode(op: SyncOp.Position): String =
        if (!op.hasLocus) {
            op.chapterUrl
        } else {
            JSONObject()
                .put("url", op.chapterUrl)
                .put("blockIndex", op.blockIndex)
                .put("charOffset", op.charOffset)
                .put("anchorText", op.anchorText)
                .put("fraction", op.fraction.toDouble())
                .put("positionAt", op.positionAt)
                .toString()
        }

    fun encode(
        chapterUrl: String,
        blockIndex: Int = 0,
        charOffset: Int = 0,
        anchorText: String = "",
        fraction: Float = 0f,
        positionAt: Long = 0L,
    ): String = encode(
        SyncOp.Position(
            workId = "",
            chapterUrl = chapterUrl,
            chapterTitle = "",
            blockIndex = blockIndex,
            charOffset = charOffset,
            anchorText = anchorText,
            fraction = fraction,
            positionAt = positionAt,
        ),
    )

    fun url(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return trimmed
        return runCatching { JSONObject(trimmed).optString("url").trim().ifBlank { null } }.getOrNull()
    }

    fun locus(raw: String?): SyncOp.Position? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) {
            return SyncOp.Position(workId = "", chapterUrl = trimmed, chapterTitle = "")
        }
        return runCatching {
            val o = JSONObject(trimmed)
            val url = o.optString("url").trim().ifBlank { return null }
            SyncOp.Position(
                workId = "",
                chapterUrl = url,
                chapterTitle = "",
                blockIndex = o.optInt("blockIndex"),
                charOffset = o.optInt("charOffset"),
                anchorText = o.optString("anchorText"),
                fraction = o.optDouble("fraction", 0.0).toFloat(),
                positionAt = o.optLong("positionAt"),
            )
        }.getOrNull()
    }
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
