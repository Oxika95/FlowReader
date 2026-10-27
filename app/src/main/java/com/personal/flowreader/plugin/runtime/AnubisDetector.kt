package com.personal.flowreader.plugin.runtime

/**
 * Detects Anubis bot-check HTML (Techaro) from a response body. Matches the challenge node and
 * soft markers used when the script id is missing or the page is partially rendered.
 */
object AnubisDetector {
    fun isChallenge(html: String): Boolean {
        if (html.isEmpty()) return false
        if (html.contains("id=\"anubis_challenge\"") || html.contains("id='anubis_challenge'")) return true
        // Soft markers: challenge title / PoW copy without requiring the script id.
        val lower = html.lowercase()
        if (lower.contains("making sure you're not a bot") || lower.contains("making sure youre not a bot")) {
            return true
        }
        if (lower.contains("proof of work") && (lower.contains("anubis") || lower.contains("techaro"))) {
            return true
        }
        return false
    }
}
