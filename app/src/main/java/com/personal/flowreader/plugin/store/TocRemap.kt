package com.personal.flowreader.plugin.store

/** Where saved chapter positions land when a story's ToC is replaced (chapters inserted or removed). */
object TocRemap {
    /** A saved position after the ToC changed; [sameChapter] = the saved chapter itself survived. */
    data class Moved(val chapterIndex: Int, val chapterHref: String, val sameChapter: Boolean)

    /**
     * New index for old chapter [oldIndex]: its URL's new place, else the next old chapter that
     * survived, else the last one before it. Null when [newUrls] is empty.
     */
    fun survivor(oldUrls: List<String>, newUrls: List<String>, oldIndex: Int): Int? {
        if (newUrls.isEmpty()) return null
        if (oldUrls.isEmpty()) return oldIndex.coerceIn(0, newUrls.lastIndex)
        val moves = chapterMoves(oldUrls, newUrls)
        val from = oldIndex.coerceIn(0, oldUrls.lastIndex)
        for (i in from..oldUrls.lastIndex) moves[i]?.let { return it }
        for (i in from - 1 downTo 0) moves[i]?.let { return it }
        return oldIndex.coerceIn(0, newUrls.lastIndex)
    }

    /**
     * A progress row saved as ([chapterHref], [chapterIndex]) against [oldUrls], moved to [newUrls].
     * Null when nothing changes. A removed chapter moves to the next surviving one.
     */
    fun position(chapterHref: String, chapterIndex: Int, oldUrls: List<String>, newUrls: List<String>): Moved? {
        if (newUrls.isEmpty()) return null
        val byHref = if (chapterHref.isBlank()) -1 else newUrls.indexOf(chapterHref)
        if (byHref >= 0) {
            return if (byHref == chapterIndex) null else Moved(byHref, chapterHref, sameChapter = true)
        }
        val oldByHref = if (chapterHref.isBlank()) -1 else oldUrls.indexOf(chapterHref)
        val old = if (oldByHref >= 0) oldByHref else chapterIndex
        val target = survivor(oldUrls, newUrls, old) ?: return null
        val same = oldUrls.getOrNull(old) == newUrls[target]
        if (same && target == chapterIndex && chapterHref == newUrls[target]) return null
        return Moved(target, newUrls[target], sameChapter = same)
    }

    /**
     * Where each old ToC index lands, matched by chapter URL (duplicate URLs pair up in order).
     * Null = the URL left the ToC.
     */
    internal fun chapterMoves(oldUrls: List<String>, newUrls: List<String>): Map<Int, Int?> {
        val slots = HashMap<String, ArrayDeque<Int>>()
        newUrls.forEachIndexed { i, url -> slots.getOrPut(url) { ArrayDeque() }.addLast(i) }
        val out = LinkedHashMap<Int, Int?>()
        oldUrls.forEachIndexed { i, url -> out[i] = slots[url]?.removeFirstOrNull() }
        return out
    }
}
