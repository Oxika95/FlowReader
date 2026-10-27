package com.personal.flowreader.plugin.runtime

/**
 * Short fingerprint of a plugin fetch body for the Plugins debug channel — enough to tell login
 * walls, bot checks, empty lists, and markup misses apart without dumping HTML into the ring.
 */
object PluginResponseProbe {
    private val TITLE = Regex("""<title[^>]*>([^<]{1,120})""", RegexOption.IGNORE_CASE)
    private val RR_USER = Regex("""window\.royalroad\.username\s*=\s*"((?:[^"\\]|\\.)*)"""")
    private val RR_USER_ID = Regex("""window\.royalroad\.userId\s*=\s*(\d+)""")

    fun summarize(text: String, contentType: String? = null): String {
        if (text.isEmpty()) return "empty-body"
        val ct = contentType?.lowercase().orEmpty()
        if (ct.contains("json") || text.trimStart().startsWith("{") || text.trimStart().startsWith("[")) {
            return "json bytes=${text.length} head=${text.take(80).replace('\n', ' ')}"
        }
        val title = TITLE.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .replace(Regex("""\s+"""), " ")
        val markers = mutableListOf<String>()
        when {
            AnubisDetector.isChallenge(text) -> markers += "bot-check"
            text.contains("form-login-details", ignoreCase = true) &&
                !text.contains("/account/logout", ignoreCase = true) -> markers += "login-page"
            text.contains("/account/logout", ignoreCase = true) ||
                RR_USER_ID.containsMatchIn(text) -> markers += "logged-in"
        }
        RR_USER.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let {
            markers += "user=$it"
        } ?: RR_USER_ID.find(text)?.groupValues?.getOrNull(1)?.let { markers += "userId=$it" }
        val listItems = count(text, "fiction-list-item")
        if (listItems > 0) markers += "fiction-list-item≈$listItems"
        else if (text.contains("fiction-list", ignoreCase = true)) markers += "fiction-list-shell"
        if (text.contains("chapter-inner", ignoreCase = true)) markers += "chapter"
        if (
            Regex(
                """not following any|don'?t have any|no fictions|haven'?t (favorited|bookmarked|added)|nothing (here|to show)|your list is empty""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(text)
        ) {
            markers += "empty-list-copy"
        }
        val titlePart = if (title.isNotEmpty()) """ title="$title"""" else ""
        val markPart = if (markers.isEmpty()) " markers=none" else " markers=${markers.joinToString(",")}"
        return "html bytes=${text.length}$titlePart$markPart"
    }

    private fun count(haystack: String, needle: String): Int {
        if (needle.isEmpty()) return 0
        var n = 0
        var i = 0
        while (true) {
            val at = haystack.indexOf(needle, i, ignoreCase = true)
            if (at < 0) return n
            n++
            i = at + needle.length
        }
    }
}
