package com.personal.flowreader.plugin.runtime

import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Jsoup elements exposed to JS as integer handles. Cleared after every plugin call, so a
 * plugin cannot hold nodes across calls.
 */
class HtmlHandles {
    private val nodes = ArrayList<Element>()

    fun clear() {
        nodes.clear()
    }

    fun parse(html: String, baseUrl: String?): Int =
        put(Jsoup.parse(html, baseUrl.orEmpty()))

    fun select(id: Int, css: String): String {
        val el = get(id) ?: return "[]"
        val out = JSONArray()
        runCatching { el.select(css) }.getOrNull()?.forEach { out.put(put(it)) }
        return out.toString()
    }

    fun selectFirst(id: Int, css: String): Int {
        val el = get(id) ?: return -1
        val found = runCatching { el.selectFirst(css) }.getOrNull() ?: return -1
        return put(found)
    }

    fun text(id: Int): String = get(id)?.text().orEmpty()

    fun ownText(id: Int): String = get(id)?.ownText().orEmpty()

    fun html(id: Int): String = get(id)?.html().orEmpty()

    fun outerHtml(id: Int): String = get(id)?.outerHtml().orEmpty()

    fun data(id: Int): String = get(id)?.data().orEmpty()

    fun attr(id: Int, name: String): String = get(id)?.attr(name).orEmpty()

    fun hasClass(id: Int, name: String): Boolean = get(id)?.hasClass(name) == true

    fun children(id: Int): String {
        val el = get(id) ?: return "[]"
        val out = JSONArray()
        el.children().forEach { out.put(put(it)) }
        return out.toString()
    }

    fun parent(id: Int): Int {
        val p = get(id)?.parent() ?: return -1
        return put(p)
    }

    fun contains(ancestorId: Int, id: Int): Boolean {
        val ancestor = get(ancestorId) ?: return false
        val el = get(id) ?: return false
        return el.parents().any { it === ancestor }
    }

    fun remove(id: Int) {
        get(id)?.remove()
    }

    private fun get(id: Int): Element? = nodes.getOrNull(id)

    private fun put(el: Element): Int {
        if (nodes.size >= MAX_HANDLES) throw IllegalStateException("Too many HTML handles in one call")
        nodes += el
        return nodes.lastIndex
    }

    private companion object {
        const val MAX_HANDLES = 200_000
    }
}
