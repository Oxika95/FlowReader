package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginSitePage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteOpenUrlTest {
    @Test
    fun declaredSitePagesIncludeListId() {
        val m = PluginManifest.parse(
            """{"id":"royalroad","name":"Royal Road","version":"1","apiVersion":5,"kind":"source",
               "site":"https://www.royalroad.com","allowedHosts":["royalroad.com"],
               "lists":[{"id":"follow","title":"Follow","syncable":true}],
               "auth":{"web":{"url":"https://www.royalroad.com/account/login","doneCookie":"x"}},
               "sitePages":[
                 {"title":"Sign in","url":"https://www.royalroad.com/account/login"},
                 {"title":"Follows","url":"https://www.royalroad.com/my/follows","listId":"follow"}
               ]}""",
        )
        assertEquals(
            listOf(
                PluginSitePage("Sign in", "https://www.royalroad.com/account/login"),
                PluginSitePage("Follows", "https://www.royalroad.com/my/follows", "follow"),
            ),
            m.resolvedSitePages(),
        )
        assertEquals(1, m.listCrawlPages().size)
        assertEquals("follow", m.sitePageForUrl("https://www.royalroad.com/my/follows?page=2")?.listId)
        assertNull(m.sitePageForUrl("https://www.royalroad.com/account/login")?.listId)
    }

    @Test
    fun defaultIsSignInThenSite() {
        val m = PluginManifest.parse(
            """{"id":"patreon","name":"Patreon","version":"1","apiVersion":5,"kind":"source",
               "site":"https://www.patreon.com","allowedHosts":["patreon.com"],
               "auth":{"web":{"url":"https://www.patreon.com/login","doneCookie":"session_id"}}}""",
        )
        assertEquals(
            listOf(
                PluginSitePage("Sign in", "https://www.patreon.com/login"),
                PluginSitePage("Patreon", "https://www.patreon.com"),
            ),
            m.resolvedSitePages(),
        )
        assertEquals(emptyList<PluginSitePage>(), m.listCrawlPages())
    }

    @Test
    fun listCrawlPageUrlAppendsPage() {
        assertEquals(
            "https://www.royalroad.com/my/follows",
            listCrawlPageUrl("https://www.royalroad.com/my/follows", 1),
        )
        assertEquals(
            "https://www.royalroad.com/my/follows?page=2",
            listCrawlPageUrl("https://www.royalroad.com/my/follows?page=1", 2),
        )
    }

    @Test
    fun decodeJsStringUnwrapsJson() {
        assertEquals("<html></html>", decodeJsString("\"<html></html>\""))
        assertEquals("", decodeJsString("null"))
    }

    @Test
    fun decodeJsBase64HtmlRoundTrip() {
        val html = "<html><body><div class=\"fiction-list-item\">x</div></body></html>"
        val b64 = java.util.Base64.getEncoder().encodeToString(html.toByteArray(Charsets.UTF_8))
        assertEquals(html, decodeJsBase64Html("\"$b64\""))
        assertEquals("", decodeJsBase64Html("null"))
    }
}
