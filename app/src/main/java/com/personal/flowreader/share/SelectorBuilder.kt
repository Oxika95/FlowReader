package com.personal.flowreader.share

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** One element on the path from `<html>` to a picked element, as seen in the live page. */
data class PathNode(
    val tag: String,
    val id: String = "",
    val classes: List<String> = emptyList(),
    /** 1-based position among same-tag siblings. */
    val nthOfType: Int = 1,
)

data class PickedSelector(
    val selector: String,
    /** Matches in the downloaded HTML the importer parses. */
    val rawMatches: Int,
    /** False when no candidate finds the picked element in the downloaded HTML. */
    val inRaw: Boolean,
)

/**
 * Builds a CSS selector for a picked element, preferring ones that also work on the downloaded
 * HTML (the live page may run scripts the importer never runs).
 */
object SelectorBuilder {
    private val IDENT = Regex("^-?[A-Za-z_][A-Za-z0-9_-]*$")

    /**
     * [similar] = Remove: a selector may match every element like the picked one.
     * Otherwise it must match exactly one element of the same tag.
     */
    fun build(path: List<PathNode>, raw: Document?, similar: Boolean): PickedSelector? {
        val target = path.lastOrNull() ?: return null
        val candidates = candidates(path, similar)
        if (raw == null) return PickedSelector(candidates.first(), 0, inRaw = false)
        for (css in candidates) {
            val hits = runCatching { raw.select(css) }.getOrNull() ?: continue
            if (hits.isEmpty() || hits.any { it.normalName() != target.tag }) continue
            if (!similar && hits.size != 1) continue
            return PickedSelector(css, hits.size, inRaw = true)
        }
        return PickedSelector(candidates.first(), countIn(raw, candidates.first()), inRaw = false)
    }

    fun countIn(raw: Document?, css: String): Int =
        if (raw == null || css.isBlank()) 0 else runCatching { raw.select(css).size }.getOrDefault(0)

    /** Ordered by preference: id, classes, class-qualified ancestor, positional path. */
    internal fun candidates(path: List<PathNode>, similar: Boolean): List<String> {
        val target = path.last()
        val tag = target.tag
        val classes = target.classes.filter { IDENT.matches(it) }
        val out = LinkedHashSet<String>()
        if (!similar && IDENT.matches(target.id)) out += "#${target.id}"
        classes.forEach { c -> out += "$tag.$c" }
        if (similar) classes.forEach { c -> out += ".$c" }
        for (i in classes.indices) for (j in i + 1 until classes.size) out += "$tag.${classes[i]}.${classes[j]}"
        val ancestors = path.dropLast(1).asReversed()
        ancestors.firstOrNull { IDENT.matches(it.id) }?.let { a ->
            classes.forEach { c -> out += "#${a.id} $tag.$c" }
            out += "#${a.id} $tag"
        }
        ancestors.firstOrNull { a -> a.classes.any(IDENT::matches) && a.tag != "html" && a.tag != "body" }?.let { a ->
            val ac = a.classes.first(IDENT::matches)
            classes.forEach { c -> out += "${a.tag}.$ac $tag.$c" }
            out += "${a.tag}.$ac > $tag"
            out += "${a.tag}.$ac $tag"
        }
        out += positional(path)
        return out.toList()
    }

    /** `body > div:nth-of-type(2) > p:nth-of-type(3)`, anchored at the nearest id. */
    private fun positional(path: List<PathNode>): String {
        val start = path.indexOfLast { IDENT.matches(it.id) }.takeIf { it >= 0 && it < path.lastIndex }
        val nodes = if (start != null) path.drop(start) else path.dropWhile { it.tag == "html" }
        return nodes.mapIndexed { i, n ->
            when {
                i == 0 && start != null -> "#${n.id}"
                n.tag == "body" -> "body"
                else -> "${n.tag}:nth-of-type(${n.nthOfType})"
            }
        }.joinToString(" > ")
    }

    /** Path of a Jsoup element (tests, and pages without a live DOM). */
    fun pathOf(el: Element): List<PathNode> =
        (el.parents().asReversed() + el).filter { it.normalName() != "#root" }.map { e ->
            PathNode(
                tag = e.normalName(),
                id = e.id(),
                classes = e.classNames().toList(),
                nthOfType = e.elementSiblingIndexOfType(),
            )
        }

    private fun Element.elementSiblingIndexOfType(): Int {
        val parent = parent() ?: return 1
        var n = 0
        for (sib in parent.children()) {
            if (sib.normalName() == normalName()) n++
            if (sib === this) return n
        }
        return 1
    }
}
