package com.personal.flowreader.tts

object SpeechText {
    /** Separators (`* * *`), lone punctuation, or text a TTS-only filter emptied: nothing to say. */
    fun isSpeakable(text: String): Boolean = text.any { it.isLetterOrDigit() }

    /** Drops code points XML 1.0 forbids; Edge rejects SSML that contains them. */
    fun xmlSafe(text: String): String {
        if (text.all { isXmlChar(it) && !it.isSurrogate() }) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                ch.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> {
                    out.append(ch).append(text[i + 1])
                    i++
                }
                ch.isSurrogate() -> Unit
                isXmlChar(ch) -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }

    private fun isXmlChar(ch: Char): Boolean =
        ch == '\t' || ch == '\n' || ch == '\r' || (ch >= ' ' && ch != '\uFFFE' && ch != '\uFFFF')
}
