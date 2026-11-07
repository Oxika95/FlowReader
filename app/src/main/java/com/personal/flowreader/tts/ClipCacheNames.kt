package com.personal.flowreader.tts

/**
 * Names and sweep rules for the on-disk clip cache (clips, word-timing sidecars, `.part` temp files).
 * Pure: no Android, no file access.
 *
 * File name: `b<bookKey>_s<index>_<voiceKey>_t<textKey>.<ext>`. `textKey` fingerprints the sentence
 * text, so a clip is only reused for the text it was made from; the index alone is not enough because
 * a re-split, a table-of-contents change or an edited chapter can put different text at the same index.
 * The speech filters are already part of `voiceKey`.
 */
internal object ClipCacheNames {
    enum class Kind { CLIP, WORDS, PART }

    data class Entry(val bookKey: String, val index: Int, val textKey: String, val kind: Kind)

    private val NAME = Regex("""^b([A-Za-z0-9.-]*)_s(\d+)_.+_t([0-9a-f]+-[0-9a-f]+)\.(mp3|wav|words\.json)(\.part)?$""")

    /** Text hash plus length: two different texts at one index need to match on both to collide. */
    fun textKey(text: String): String =
        Integer.toHexString(text.hashCode()) + "-" + Integer.toHexString(text.length)

    private val BOOK_KEY_UNSAFE = Regex("[^A-Za-z0-9.-]")
    private val VOICE_KEY_UNSAFE = Regex("[^A-Za-z0-9._-]")

    /** Book id plus chapter origin, reduced to the characters [NAME] accepts. */
    fun bookKey(bookId: String, origin: Int): String = bookId.replace(BOOK_KEY_UNSAFE, "-") + ".o" + origin

    fun voiceKey(raw: String): String = raw.replace(VOICE_KEY_UNSAFE, "_")

    fun base(bookKey: String, index: Int, voiceKey: String, text: String): String =
        "b${bookKey}_s${index}_${voiceKey}_t${textKey(text)}"

    fun parse(name: String): Entry? {
        val m = NAME.matchEntire(name) ?: return null
        val index = m.groupValues[2].toIntOrNull() ?: return null
        val kind = when {
            m.groupValues[5].isNotEmpty() -> Kind.PART
            m.groupValues[4] == "words.json" -> Kind.WORDS
            else -> Kind.CLIP
        }
        return Entry(m.groupValues[1], index, m.groupValues[3], kind)
    }

    /**
     * True when [name] can never serve the attached book's current window: not a cache name, another
     * book, an index outside [window], or text that no longer matches the sentence at that index
     * ([textAt] is null when there is no such sentence). Word-timing sidecars follow their clip.
     * `.part` files of the attached book are left alone: the synthesis that owns one deletes it.
     */
    fun isStale(name: String, bookKey: String, window: IntRange, textAt: (Int) -> String?): Boolean {
        val e = parse(name) ?: return true
        if (e.bookKey != bookKey) return true
        if (e.kind == Kind.PART) return false
        if (e.index !in window) return true
        val text = textAt(e.index) ?: return true
        return e.textKey != textKey(text)
    }
}
