package com.personal.flowreader.plugin.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnubisDetectorTest {
    @Test
    fun detectsAnubisChallengeScriptId() {
        val fixture =
            """<!doctype html><html><head><title>Making sure you're not a bot!</title>""" +
                """<script id="anubis_challenge" type="application/json">{"method":"argon2id"}</script>""" +
                """</head><body></body></html>"""
        assertTrue(AnubisDetector.isChallenge(fixture))
        assertTrue(AnubisDetector.isChallenge("""<script id='anubis_challenge' type="application/json">{}</script>"""))
    }

    @Test
    fun ignoresNormalFictionHtml() {
        val fiction =
            """<html><body><h1>Demo</h1><div class="chapter-inner chapter-content"><p>Hello</p></div></body></html>"""
        assertFalse(AnubisDetector.isChallenge(fiction))
        assertFalse(AnubisDetector.isChallenge(""))
    }
}
