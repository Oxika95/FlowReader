package com.personal.flowreader.library.plugin.royalroad

import java.io.File

/** Royal Road personalized lists (Follow / Favorite / Read Later). */
enum class RoyalRoadListKind(
    val fileName: String,
    /** POST `type=` value for `/fictions/setbookmark/`. */
    val bookmarkType: String,
    val label: String,
) {
    Follow("follows_membership.txt", "follow", "Follow"),
    Favorite("favorites_membership.txt", "favorite", "Favorite"),
    ReadLater("readlater_membership.txt", "readlater", "Read Later"),
}

/** Cached list membership from sync and local bookmark actions. */
object RoyalRoadMembershipStore {
    fun file(root: File, kind: RoyalRoadListKind = RoyalRoadListKind.Follow): File =
        File(root, kind.fileName)

    fun write(root: File, works: List<FictionListItem>, kind: RoyalRoadListKind = RoyalRoadListKind.Follow) {
        root.mkdirs()
        file(root, kind).writeText(
            works.joinToString("\n") { item ->
                listOf(
                    escape(item.title),
                    item.url,
                    escape(item.author),
                    escape(item.latestChapter),
                    item.coverUrl,
                ).joinToString("\t")
            },
        )
    }

    fun read(root: File, kind: RoyalRoadListKind = RoyalRoadListKind.Follow): List<FictionListItem> {
        val f = file(root, kind)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapNotNull null
            FictionListItem(
                title = unescape(parts[0]),
                url = parts[1],
                author = parts.getOrNull(2)?.let { unescape(it) }.orEmpty(),
                latestChapter = parts.getOrNull(3)?.let { unescape(it) }.orEmpty(),
                coverUrl = parts.getOrNull(4).orEmpty(),
            )
        }
    }

    fun bookIds(root: File, kind: RoyalRoadListKind): Set<String> =
        read(root, kind).mapNotNull { item ->
            RoyalRoadHtml.fictionId(item.url)?.let { RoyalRoadHtml.bookIdFor(it) }
        }.toSet()

    /** Merge one fiction into the cached list (by fiction id). */
    fun upsert(root: File, item: FictionListItem, kind: RoyalRoadListKind = RoyalRoadListKind.Follow) {
        val id = RoyalRoadHtml.fictionId(item.url) ?: return
        val next = read(root, kind)
            .filter { RoyalRoadHtml.fictionId(it.url) != id }
            .toMutableList()
        next.add(0, item)
        write(root, next, kind)
    }

    fun remove(root: File, bookId: String, kind: RoyalRoadListKind) {
        val fictionId = bookId.removePrefix("rr:")
        val next = read(root, kind).filter { RoyalRoadHtml.fictionId(it.url) != fictionId }
        write(root, next, kind)
    }

    fun removeFromAll(root: File, bookId: String) {
        RoyalRoadListKind.entries.forEach { remove(root, bookId, it) }
    }

    /**
     * Seed Follow from the plugin catalog when the Follow file is missing OR empty
     * (legacy installs before multi-list membership, and wiped empty files).
     */
    fun migrateUnlistedToFollow(root: File, catalogBookIds: Collection<String>) {
        if (catalogBookIds.isEmpty()) return
        val followFile = file(root, RoyalRoadListKind.Follow)
        if (followFile.exists() && followFile.length() > 0L) return
        // Also skip if any other list already has membership.
        val anyListed = RoyalRoadListKind.entries.any { kind ->
            val f = file(root, kind)
            f.exists() && f.length() > 0L
        }
        if (anyListed && followFile.exists()) return
        val items = catalogBookIds.map { bookId ->
            val fictionId = bookId.removePrefix("rr:")
            FictionListItem(
                title = fictionId,
                url = "${RoyalRoadHtml.ORIGIN}/fiction/$fictionId",
                author = "",
                latestChapter = "",
                coverUrl = "",
            )
        }
        write(root, items, RoyalRoadListKind.Follow)
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\t", "\t").replace("\\\\", "\\")
}

enum class FollowsSyncMode {
    Merge,
    Overwrite,
}
