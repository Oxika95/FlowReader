package com.personal.flowreader.share

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Removes elements a browser would hide via the page's own `<style>` rules, inline `style`, or the
 * `hidden` attribute. Sites (e.g. Royal Road) plant anti-scrape notices this way: a random class
 * styled `display: none; speak: never` in a `<style>` block. Must run before `<style>` is dropped.
 */
object HiddenContent {
    private val COMMENTS = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    /** Innermost `selectors { declarations }` blocks, so rules inside `@media { }` count too. */
    private val RULE = Regex("""([^{}]+)\{([^{}]*)\}""")
    private val KEEP = setOf("html", "head", "body")

    /** Number of elements removed. */
    fun strip(doc: Document): Int {
        val targets = LinkedHashSet<Element>()
        for (style in doc.select("style")) {
            val css = style.data().replace(COMMENTS, " ")
            for (rule in RULE.findAll(css)) {
                if (!hides(rule.groupValues[2])) continue
                for (selector in CssList.split(rule.groupValues[1])) {
                    if (selector.isEmpty() || selector.startsWith("@") || ':' in selector) continue
                    runCatching { doc.select(selector) }.getOrNull()?.let(targets::addAll)
                }
            }
        }
        doc.select("[style]").filterTo(targets) { hides(it.attr("style")) }
        targets += doc.select("[hidden]")
        var removed = 0
        for (el in targets) {
            if (el.normalName() in KEEP || el.ownerDocument() == null) continue
            el.remove()
            removed++
        }
        return removed
    }

    internal fun hides(declarations: String): Boolean = declarations.split(';').any { decl ->
        val name = decl.substringBefore(':', "").trim().lowercase()
        val value = decl.substringAfter(':', "").replace("!important", "", ignoreCase = true).trim().lowercase()
        when (name) {
            "display" -> value == "none"
            "visibility" -> value == "hidden" || value == "collapse"
            "speak" -> value == "never" || value == "none"
            else -> false
        }
    }
}
