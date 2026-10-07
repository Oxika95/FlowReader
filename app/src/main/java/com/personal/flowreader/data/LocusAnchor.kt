package com.personal.flowreader.data

/**
 * Saved positions carry a short copy of the text they point at. If the indices no longer land on
 * that text (parser change, filter edit, edited linked file), the text is searched for in the
 * chapter instead.
 */
object LocusAnchor {
    const val LENGTH = 64

    /** Anchor text for [locus] in [chapter] ("" when the block isn't there). */
    fun of(chapter: Chapter, locus: Locus): String {
        val text = chapter.blocks.getOrNull(locus.blockIndex)?.text ?: return ""
        val start = locus.charOffset.coerceIn(0, text.length)
        return text.substring(start, (start + LENGTH).coerceAtMost(text.length)).trim()
    }

    /** [locus] if it still points at [anchor], else where [anchor] now is, else [locus] clamped. */
    fun resolve(chapter: Chapter, locus: Locus, anchor: String): Locus {
        val clamped = clamp(chapter, locus)
        if (anchor.isBlank() || chapter.blocks.isEmpty()) return clamped
        val text = chapter.blocks[clamped.blockIndex].text
        val near = text.indexOf(anchor, (clamped.charOffset - LENGTH).coerceAtLeast(0))
        if (near >= 0 && near <= clamped.charOffset + LENGTH) return clamped.copy(charOffset = near)
        // Nearest block first, so repeated phrases resolve to the closest occurrence.
        val order = chapter.blocks.indices.sortedBy { kotlin.math.abs(it - clamped.blockIndex) }
        for (bi in order) {
            val at = chapter.blocks[bi].text.indexOf(anchor)
            if (at >= 0) return Locus(locus.chapterIndex, bi, at)
        }
        return clamped
    }

    fun clamp(chapter: Chapter, locus: Locus): Locus {
        if (chapter.blocks.isEmpty()) return Locus(locus.chapterIndex, 0, 0)
        val bi = locus.blockIndex.coerceIn(0, chapter.blocks.lastIndex)
        val len = chapter.blocks[bi].text.length
        return Locus(locus.chapterIndex, bi, locus.charOffset.coerceIn(0, len))
    }
}
