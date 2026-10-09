package com.personal.flowreader.ui.design.card.media

import com.personal.flowreader.plugin.api.PluginActionPlacement
import com.personal.flowreader.plugin.api.PluginCard
import com.personal.flowreader.plugin.api.PluginManifest

/** Local-file metadata for [FileMediaCardAdapter] (no Android types, so it is unit-testable). */
data class FileMediaInfo(
    val title: String,
    /** "Imported" / "Linked" / plugin name. */
    val sourceLabel: String,
    val linked: Boolean,
    /** Relative "last read" text. */
    val lastRead: String,
    val progress: Float,
    /** Lower-case file extension ("epub", "txt"); blank if unknown. */
    val format: String,
    val sizeBytes: Long?,
)

/** Files tab: Share, Remove, Open. */
object FileMediaCardAdapter {
    fun model(info: FileMediaInfo, busy: Boolean): MediaCardModel {
        val pct = (info.progress.coerceIn(0f, 1f) * 100f).toInt()
        val stats = buildList {
            add(MediaStat("pages", "$pct%", "Read"))
            if (info.format.isNotBlank()) add(MediaStat("list", info.format.uppercase(), "Format"))
            info.sizeBytes?.takeIf { it > 0 }?.let { add(MediaStat("download", formatBytes(it), "Size")) }
        }
        return MediaCardModel(
            title = info.title,
            subtitle = info.lastRead.takeIf { it.isNotBlank() }?.let { "Read $it" }.orEmpty(),
            stats = stats,
            badges = listOfNotNull(info.sourceLabel.takeIf { it.isNotBlank() }, "Linked".takeIf { info.linked }).distinct(),
            progress = info.progress,
            titleMaxLines = 3,
            footer = listOf(
                MediaAction(MediaActionIds.SHARE, "Share", MediaActionKind.Secondary, enabled = !busy),
                MediaAction(MediaActionIds.REMOVE, "Remove", MediaActionKind.Destructive, enabled = !busy),
                MediaAction(MediaActionIds.OPEN, "Open", MediaActionKind.Primary, enabled = !busy),
            ),
        )
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024 -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}

/** Plugin story state for [PluginMediaCardAdapter] (mirrors the cached splash + local cache). */
data class PluginMediaInfo(
    val title: String,
    val author: String,
    val workUrl: String,
    val synopsis: String,
    val tags: List<String>,
    val chapterCount: Int,
    val downloadedCount: Int,
    val cachedIndices: Set<Int>,
    /** Saved reading position (absolute ToC index). */
    val locus: Int,
    /** Chapters kept ahead of [locus]. */
    val cacheLevel: Int,
    val listedIn: Set<String>,
    /** Plugin card slots; null when the plugin fills none. */
    val card: PluginCard?,
    /** Chapters more than [cacheLevel] behind [locus] are deleted. */
    val cleanup: Boolean = false,
    /** New-chapter bell state; null hides the bell. */
    val notify: Boolean? = null,
    /** A reading position is saved (else the story is unread and [locus] is chapter 1). */
    val hasPosition: Boolean = false,
    /** Title of chapter [locus]. */
    val locusTitle: String = "",
)

/**
 * Plugin story: merges host-owned actions (list toggles, Share, Download, Refresh, Delete, Read —
 * always present, fixed order) with plugin-declared slots (stats, badges, links, capped actions).
 */
object PluginMediaCardAdapter {
    fun model(
        manifest: PluginManifest,
        info: PluginMediaInfo,
        busy: Boolean,
        downloadProgress: Pair<Int, Int>?,
        error: String?,
    ): MediaCardModel {
        val card = info.card?.capped()
        val stats = buildList {
            card?.stats?.forEach { add(MediaStat(it.icon, it.value, it.label)) }
            add(MediaStat("pages", info.chapterCount.toString(), "Chapters"))
        }
        val badges = card?.badges.orEmpty()
        val rail = buildList {
            manifest.lists.filter { it.membershipToggle }.forEach { list ->
                add(
                    MediaAction(
                        id = MediaActionIds.list(list.id),
                        label = list.title,
                        kind = MediaActionKind.Toggle,
                        icon = list.icon,
                        on = list.id in info.listedIn,
                        enabled = !busy,
                    ),
                )
            }
            info.notify?.let { on ->
                add(
                    MediaAction(
                        MediaActionIds.NOTIFY,
                        if (on) "New-chapter alerts on" else "New-chapter alerts off",
                        MediaActionKind.Toggle,
                        icon = "notifications",
                        on = on,
                        enabled = !busy,
                    ),
                )
            }
            add(
                MediaAction(
                    MediaActionIds.SHARE,
                    "Share",
                    MediaActionKind.Icon,
                    icon = "share",
                    enabled = info.workUrl.isNotBlank(),
                ),
            )
            card?.actions?.filter { it.placement == PluginActionPlacement.Rail }?.forEach { a ->
                add(
                    MediaAction(
                        id = a.id,
                        label = a.label,
                        kind = if (a.toggle) MediaActionKind.Toggle else MediaActionKind.Icon,
                        icon = a.icon,
                        on = a.on,
                        enabled = a.enabled && !busy,
                        owner = MediaActionOwner.Plugin,
                    ),
                )
            }
        }
        val canRead = !busy && info.chapterCount > 0
        val downloading = downloadProgress != null
        val footer = buildList {
            add(
                MediaAction(
                    MediaActionIds.DOWNLOAD,
                    if (downloading) "Cancel download" else "Download",
                    MediaActionKind.Secondary,
                    enabled = canRead || downloading,
                    longPress = !downloading,
                ),
            )
            add(MediaAction(MediaActionIds.REFRESH, "Refresh", MediaActionKind.Secondary, enabled = !busy && !downloading))
            add(
                MediaAction(
                    MediaActionIds.DELETE,
                    "Delete",
                    MediaActionKind.Destructive,
                    enabled = !busy && !downloading,
                ),
            )
            card?.actions?.filter { it.placement == PluginActionPlacement.Footer }?.forEach { a ->
                add(
                    MediaAction(
                        id = a.id,
                        label = a.label,
                        kind = MediaActionKind.Secondary,
                        icon = a.icon,
                        on = a.on,
                        enabled = a.enabled && !busy,
                        owner = MediaActionOwner.Plugin,
                    ),
                )
            }
            add(MediaAction(MediaActionIds.READ, "Read", MediaActionKind.Primary, enabled = canRead))
        }
        val status = when {
            downloadProgress != null -> "Downloading ${downloadProgress.first} / ${downloadProgress.second}"
            info.hasPosition && info.chapterCount > 0 -> {
                val chapter = (info.locus + 1).coerceIn(1, info.chapterCount)
                "Last read: ${info.locusTitle.ifBlank { "Chapter $chapter" }} · $chapter / ${info.chapterCount}"
            }
            else -> "Not started"
        }
        return MediaCardModel(
            title = info.title,
            subtitle = info.author,
            stats = stats,
            badges = badges,
            tags = info.tags,
            synopsis = info.synopsis,
            segments = MediaSegments(info.chapterCount, info.locus, info.cachedIndices),
            segmentsLongPressLabel = "Change reading position",
            status = status,
            error = error.orEmpty(),
            links = card?.links?.map { MediaLink(it.label, it.url) }.orEmpty(),
            rail = rail,
            footer = footer,
        )
    }
}
