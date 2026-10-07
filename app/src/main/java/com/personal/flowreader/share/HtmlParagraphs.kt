package com.personal.flowreader.share

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

/**
 * Visible text of an HTML subtree as paragraphs: every block element, `<br>` and `<pre>` line
 * ends a paragraph, so words on either side of a break never join. Text directly inside a
 * `<div>` counts too, and nested blocks are read once.
 */
object HtmlParagraphs {
    private val BLOCKS = setOf(
        "address", "article", "aside", "blockquote", "body", "center", "dd", "details", "dialog",
        "div", "dl", "dt", "fieldset", "figcaption", "figure", "footer", "form", "h1", "h2", "h3",
        "h4", "h5", "h6", "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section",
        "summary", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "ul",
    )
    private val SPACES = Regex("[ \\t\\x0B\\f\\r\\n\\u00A0]+")

    fun of(root: Element): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var preDepth = 0

        fun flush() {
            current.split('\n').forEach { line ->
                val text = line.replace(SPACES, " ").trim()
                if (text.isNotEmpty()) out += text
            }
            current.setLength(0)
        }

        NodeTraversor.traverse(
            object : NodeVisitor {
                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is TextNode -> {
                            val raw = node.wholeText
                            current.append(
                                if (preDepth > 0) raw.replace('\r', '\n') else raw.replace(SPACES, " "),
                            )
                        }
                        is Element -> when (val tag = node.normalName()) {
                            "br" -> current.append('\n')
                            "pre" -> { flush(); preDepth++ }
                            else -> if (tag in BLOCKS) flush()
                        }
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node !is Element) return
                    when (node.normalName()) {
                        "pre" -> { flush(); preDepth-- }
                        in BLOCKS -> flush()
                    }
                }
            },
            root,
        )
        flush()
        return out
    }
}
