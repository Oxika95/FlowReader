package com.personal.flowreader.plugin.runtime

/** Detects Anubis bot-check HTML (Techaro) from a response body. */
object AnubisDetector {
    fun isChallenge(html: String): Boolean =
        html.contains("id=\"anubis_challenge\"") || html.contains("id='anubis_challenge'")
}
