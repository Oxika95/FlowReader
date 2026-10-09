package com.personal.flowreader.ui.plugin

import androidx.compose.runtime.Composable
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowTextAction

/** Library card badge for a story with a [PositionConflictCard] waiting. */
internal const val POSITION_CONFLICT_BADGE = "Position conflict"

/**
 * Both the app and the site moved this story's reading chapter since the last sync: the user
 * picks one. "Later" hides it until the story card opens again.
 */
@Composable
internal fun PositionConflictCard(
    ui: PluginTabUi,
    onUseRemote: () -> Unit,
    onKeepMine: () -> Unit,
    onLater: () -> Unit,
) {
    val story = ui.story ?: return
    val (local, remote) = story.positionConflict ?: return
    fun chapter(i: Int): String = story.toc.getOrNull(i)?.title?.trim()?.ifBlank { null } ?: "Chapter ${i + 1}"
    val site = ui.manifest.name
    FlowConfirmCard(
        visible = ui.conflictDismissedFor != story.bookId,
        title = "Reading position",
        message = "You read on in both places since the last sync.\n\n" +
            "Flow Reader: ${chapter(local)}\n$site: ${chapter(remote)}",
        confirmLabel = "Use $site",
        onConfirm = { if (!ui.busy) onUseRemote() },
        onDismiss = onLater,
        dismissLabel = "Later",
        extraActions = {
            FlowTextAction("Keep mine", onKeepMine, enabled = !ui.busy)
        },
    )
}
