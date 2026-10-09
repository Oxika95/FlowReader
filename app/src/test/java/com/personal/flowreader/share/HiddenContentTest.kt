package com.personal.flowreader.share

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenContentTest {
    private fun paragraphs(html: String): Pair<Int, List<String>> {
        val doc = Jsoup.parse(html)
        val removed = HiddenContent.strip(doc)
        return removed to HtmlParagraphs.of(doc.body())
    }

    @Test
    fun royalRoadStyleNoticesAreRemoved() {
        val (removed, paras) = paragraphs(
            """
            <html><head><style>
              .cjNkODUzODExNzk4OTRiODhiMTU2ZDY0MDcwNWJiMWU5 { display: none; speak: never; }
            </style></head><body><div class="chapter-inner">
              <p class="cnAbc123">First.</p>
              <p class="cjNkODUzODExNzk4OTRiODhiMTU2ZDY0MDcwNWJiMWU5">If you spot this on Amazon, report it.</p>
              <p class="cnDef456">Second.<span class="cjNkODUzODExNzk4OTRiODhiMTU2ZDY0MDcwNWJiMWU5">Stolen.</span> Third.</p>
            </div></body></html>
            """.trimIndent(),
        )
        assertEquals(2, removed)
        assertEquals(listOf("First.", "Second. Third."), paras)
    }

    @Test
    fun speakNeverAloneAndMediaBlocksAndSelectorLists() {
        val (_, paras) = paragraphs(
            """
            <html><head><style>
              /* .kept { display: none } */
              .a { speak: never }
              @media screen { .b, p#c { visibility: hidden !important } }
            </style></head><body>
              <p>Kept.</p><p class="kept">Also kept.</p><p class="a">A</p><p class="b">B</p><p id="c">C</p>
            </body></html>
            """.trimIndent(),
        )
        assertEquals(listOf("Kept.", "Also kept."), paras)
    }

    @Test
    fun inlineStyleAndHiddenAttribute() {
        val (_, paras) = paragraphs(
            """
            <body><p>Kept.</p><p style="color: red; DISPLAY:None">X</p><p hidden>Y</p>
            <p style="display: block">Shown.</p></body>
            """.trimIndent(),
        )
        assertEquals(listOf("Kept.", "Shown."), paras)
    }

    @Test
    fun pseudoInvalidAndBodySelectorsAreIgnored() {
        val (removed, paras) = paragraphs(
            """
            <html><head><style>
              .menu:hover { display: none }
              p[class { display: none }
              body, html { display: none }
            </style></head><body><p class="menu">Kept.</p></body></html>
            """.trimIndent(),
        )
        assertEquals(0, removed)
        assertEquals(listOf("Kept."), paras)
    }

    @Test
    fun hidesOnlyMatchesHidingValues() {
        assertTrue(HiddenContent.hides("display:none"))
        assertTrue(HiddenContent.hides("color: red; visibility: collapse"))
        assertFalse(HiddenContent.hides("display: inline-block; visibility: visible"))
        assertFalse(HiddenContent.hides("font-size: 0"))
    }
}
