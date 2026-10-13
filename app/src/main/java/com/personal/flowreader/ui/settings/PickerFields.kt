package com.personal.flowreader.ui.settings

import com.personal.flowreader.share.CssList
import com.personal.flowreader.share.ParseSelectors

/** Parser fields the picker reads and writes (raw editor text). */
data class PickerFields(
    val title: String = "",
    val body: String = "",
    val cover: String = "",
    val prev: String = "",
    val next: String = "",
    val remove: String = "",
) {
    operator fun get(field: PickField): String = when (field) {
        PickField.Title -> title
        PickField.Cover -> cover
        PickField.Body -> body
        PickField.Previous -> prev
        PickField.Next -> next
        PickField.Remove -> remove
    }

    fun with(field: PickField, value: String): PickerFields = when (field) {
        PickField.Title -> copy(title = value)
        PickField.Cover -> copy(cover = value)
        PickField.Body -> copy(body = value)
        PickField.Previous -> copy(prev = value)
        PickField.Next -> copy(next = value)
        PickField.Remove -> copy(remove = value)
    }

    /**
     * Applies a tapped element's [selector]. Remove collects entries; [replacing] is the entry the
     * same pick wrote before Wider/Narrower, so adjusting a pick doesn't pile up entries.
     */
    fun selectors(): ParseSelectors =
        ParseSelectors.of(title = title, body = body, cover = cover, prev = prev, next = next, remove = remove)

    fun withPick(field: PickField, selector: String, replacing: String? = null): PickerFields {
        if (field != PickField.Remove) return with(field, selector)
        val entries = CssList.split(remove).filterNot { it == replacing }
        return with(field, CssList.join((entries + selector).distinct()))
    }
}

enum class PickField(val label: String, val jsKey: String) {
    Title("Title", "title"),
    Cover("Cover", "cover"),
    Body("Body", "body"),
    Previous("Prev", "prev"),
    Next("Next", "next"),
    Remove("Remove", "remove"),
}
