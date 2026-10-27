package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class PluginResponseProbeTest {
    @Test
    fun summarizesLoggedInFollowsShell() {
        val html =
            """<!doctype html><html><head><title>Follows | Royal Road</title></head><body>""" +
                """<script>window.royalroad.userId = 12345; window.royalroad.username = "Oxika95";</script>""" +
                """<a href="/account/logout">Log out</a><div class="fiction-list"></div></body></html>"""
        val s = PluginResponseProbe.summarize(html, "text/html")
        assertTrue(s.contains("html bytes="))
        assertTrue(s.contains("""title="Follows | Royal Road""""))
        assertTrue(s.contains("logged-in"))
        assertTrue(s.contains("user=Oxika95"))
        assertTrue(s.contains("fiction-list-shell"))
    }

    @Test
    fun summarizesBotCheck() {
        val html =
            """<html><head><title>Making sure you're not a bot!</title>""" +
                """<script id="anubis_challenge" type="application/json">{}</script></head><body></body></html>"""
        val s = PluginResponseProbe.summarize(html)
        assertTrue(s.contains("bot-check"))
        assertTrue(s.contains("Making sure"))
    }

    @Test
    fun summarizesJson() {
        val s = PluginResponseProbe.summarize("""{"data":[]}""", "application/json")
        assertTrue(s.startsWith("json"))
        assertTrue(s.contains("bytes="))
    }

    @Test
    fun countsFictionListItems() {
        val html =
            """<div class="fiction-list-item"></div><div class="fiction-list-item"></div>""" +
                """<a href="/account/logout">out</a>"""
        val s = PluginResponseProbe.summarize(html)
        assertTrue(s.contains("fiction-list-item≈2"))
    }
}
