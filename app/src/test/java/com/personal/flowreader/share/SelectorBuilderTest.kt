package com.personal.flowreader.share

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectorBuilderTest {
    private val html = """
        <html><body>
        <div id="main">
          <h1 class="title big">Chapter</h1>
          <div class="post"><div class="content"><p>a</p><p class="note">n1</p><p>b</p></div></div>
          <div class="post"><div class="content"><p class="note">n2</p></div></div>
          <p class="note">n3</p>
          <section><p>plain one</p><p>plain two</p></section>
        </div>
        </body></html>
    """.trimIndent()
    private val doc = Jsoup.parse(html)

    private fun pick(css: String, similar: Boolean = false) =
        SelectorBuilder.build(SelectorBuilder.pathOf(doc.selectFirst(css)!!), doc, similar)!!

    @Test
    fun usesIdWhenPresent() {
        assertEquals("#main", pick("#main").selector)
    }

    @Test
    fun usesUniqueClass() {
        val p = pick("h1")
        assertEquals("h1.title", p.selector)
        assertTrue(p.inRaw)
        assertEquals(1, p.rawMatches)
    }

    @Test
    fun singleSkipsAmbiguousClassForQualifiedOne() {
        val p = pick("section > p:nth-of-type(2)")
        assertEquals(1, doc.select(p.selector).size)
        assertEquals("plain two", doc.selectFirst(p.selector)!!.text())
    }

    @Test
    fun removeAllowsMatchingSimilarElements() {
        val p = pick("p.note", similar = true)
        assertEquals("p.note", p.selector)
        assertEquals(3, p.rawMatches)
    }

    @Test
    fun flagsElementMissingFromDownloadedHtml() {
        val live = SelectorBuilder.pathOf(doc.selectFirst("h1")!!).let { path ->
            path.dropLast(1) + PathNode("h2", classes = listOf("injected"))
        }
        val p = SelectorBuilder.build(live, doc, similar = false)!!
        assertFalse(p.inRaw)
        assertEquals(0, p.rawMatches)
        assertEquals("h2.injected", p.selector)
    }

    @Test
    fun withoutRawUsesFirstCandidate() {
        val p = SelectorBuilder.build(SelectorBuilder.pathOf(doc.selectFirst("h1")!!), null, similar = false)!!
        assertEquals("h1.title", p.selector)
        assertFalse(p.inRaw)
    }

    @Test
    fun skipsInvalidClassNames() {
        val path = listOf(PathNode("html"), PathNode("body"), PathNode("div", classes = listOf("md:flex", "1bad"), nthOfType = 1))
        assertTrue(SelectorBuilder.candidates(path, similar = false).none { "md:flex" in it || "1bad" in it })
    }

    @Test
    fun positionalAnchorsAtNearestId() {
        val path = SelectorBuilder.pathOf(doc.selectFirst("section > p:nth-of-type(2)")!!)
        val positional = SelectorBuilder.candidates(path, similar = false).last()
        assertEquals("#main > section:nth-of-type(1) > p:nth-of-type(2)", positional)
        assertEquals("plain two", doc.selectFirst(positional)!!.text())
    }
}
