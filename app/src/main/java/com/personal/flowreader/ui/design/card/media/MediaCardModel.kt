package com.personal.flowreader.ui.design.card.media

/**
 * Everything a [FlowMediaCard] shows, as plain data. Local files and plugin stories fill this
 * through adapters ([FileMediaCardAdapter], [PluginMediaCardAdapter]); the card layout is fixed
 * and empty slots are hidden. Cover art is passed to the card separately (it is a bitmap).
 */
data class MediaCardModel(
    val title: String,
    /** Author / source line under the title. */
    val subtitle: String = "",
    val stats: List<MediaStat> = emptyList(),
    /** Short pills ("Ongoing", "Linked"). */
    val badges: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val synopsis: String = "",
    /** Reading progress 0..1 as a bar on the band; null hides it. */
    val progress: Float? = null,
    /** Per-segment strip (chapters cached / locus); null hides it. */
    val segments: MediaSegments? = null,
    /** Accessibility label for a long press on [segments]; blank when it has none. */
    val segmentsLongPressLabel: String = "",
    /** Host-owned status line under the band ("Last read: Chapter 12 · 12 / 40"). */
    val status: String = "",
    val error: String = "",
    val links: List<MediaLink> = emptyList(),
    /** Circular controls on the cover's top-end rail, top to bottom. */
    val rail: List<MediaAction> = emptyList(),
    /** Footer actions; [MediaActionKind.Primary] renders as the full-width main button. */
    val footer: List<MediaAction> = emptyList(),
    val titleMaxLines: Int = 2,
)

/** Icon token (see FlowIcons) + value, e.g. `star` / `4.61`. */
data class MediaStat(val icon: String?, val value: String, val label: String = "")

data class MediaLink(val label: String, val url: String)

/**
 * Segment strip: [count] segments, [locus] marked in the saturated accent, [filled] drawn
 * gray behind the locus and desaturated accent ahead of it; others are empty track.
 */
data class MediaSegments(val count: Int, val locus: Int, val filled: Set<Int>)

enum class MediaActionKind {
    /** The single full-width footer button. */
    Primary,
    Secondary,
    /** Secondary styled with the error color. */
    Destructive,
    /** Rail toggle with on/off state. */
    Toggle,
    /** Rail action without state (Share). */
    Icon,
}

enum class MediaActionOwner {
    /** Implemented by the app; always present, fixed order. */
    Host,
    /** Declared by a plugin (`card.actions`); tapping calls `cardAction`. */
    Plugin,
}

data class MediaAction(
    val id: String,
    val label: String,
    val kind: MediaActionKind,
    val icon: String? = null,
    val on: Boolean = false,
    val enabled: Boolean = true,
    val owner: MediaActionOwner = MediaActionOwner.Host,
    /** Footer button also answers a long press (`onLongAction`). */
    val longPress: Boolean = false,
)

/** Host action ids shared by adapters and handlers. */
object MediaActionIds {
    const val READ = "read"
    const val OPEN = "open"
    const val DOWNLOAD = "download"
    const val REFRESH = "refresh"
    const val DELETE = "delete"
    const val REMOVE = "remove"
    const val SHARE = "share"
    const val NOTIFY = "notify"
    const val LIST_PREFIX = "list:"

    fun list(listId: String): String = LIST_PREFIX + listId
    fun listIdOf(actionId: String): String? = actionId.removePrefix(LIST_PREFIX).takeIf { actionId.startsWith(LIST_PREFIX) }
}

/** Drop host actions by id from the rail and footer; plugin actions are never removed. */
fun MediaCardModel.withoutHostActions(ids: Set<String>): MediaCardModel {
    if (ids.isEmpty()) return this
    fun MediaAction.hidden() = owner == MediaActionOwner.Host && id in ids
    return copy(rail = rail.filterNot { it.hidden() }, footer = footer.filterNot { it.hidden() })
}
