package com.personal.flowreader.ui.plugin

/**
 * The Manage card's cache level field. Typing only edits the draft; the level is saved once on
 * commit (Done, focus lost, card closed). Saving each keystroke pruned chapters to "1" while "15"
 * was being typed.
 */
internal object CacheLevelDraft {
    private const val MAX_DIGITS = 4

    fun digits(raw: String): String = raw.filter { it.isDigit() }.take(MAX_DIGITS)

    /** The level to save for [draft], or null when it is blank or already the [saved] level. */
    fun toSave(draft: String, saved: Int): Int? = draft.toIntOrNull()?.takeIf { it != saved }
}
