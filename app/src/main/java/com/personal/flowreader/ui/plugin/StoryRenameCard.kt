package com.personal.flowreader.ui.plugin

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowTextField

/** Rename a story on this device (Rename on the story media card). Kept across refresh and sync. */
@Composable
internal fun RenameStoryCard(
    visible: Boolean,
    story: PluginStory?,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(story?.bookId, visible) { mutableStateOf(story?.title.orEmpty()) }
    FlowFullscreenCard(
        visible = visible && story != null,
        onDismiss = onDismiss,
        title = "Rename story",
        variant = FlowCardVariant.Compact,
        footer = {
            FlowActionRow(
                start = { FlowTextAction("Reset", { onSave("") }) },
            ) {
                FlowTextAction("Cancel", onDismiss)
                FlowTextAction("Save", { onSave(draft) }, enabled = draft.isNotBlank())
            }
        },
    ) {
        FlowTextField(
            value = draft,
            onValueChange = { draft = it },
            label = "Title",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) onSave(draft) }),
        )
        FlowHint("Shown in the library, reader and queue on this device. Reset uses the site's title.")
        error?.let { FlowHint(it, error = true) }
    }
}
