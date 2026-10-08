package com.personal.flowreader.ui.plugin

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.design.card.media.FlowMediaCard
import com.personal.flowreader.ui.design.card.media.MediaActionIds
import com.personal.flowreader.ui.design.card.media.MediaActionKind
import com.personal.flowreader.ui.design.card.media.MediaActionOwner
import com.personal.flowreader.ui.design.card.media.PluginMediaCardAdapter
import com.personal.flowreader.ui.design.card.media.PluginMediaInfo
import com.personal.flowreader.ui.design.card.media.withoutHostActions
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Story media card shared by every plugin. Host actions (list toggles, new-chapter bell, Share, Download,
 * Refresh, Delete, Read) are always present; plugins add stats, badges, links and
 * up to 2 rail + 2 footer actions, routed to `cardAction`.
 */
@Composable
internal fun StoryMediaCard(
    visible: Boolean,
    ui: PluginTabUi,
    onDismiss: () -> Unit,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onDownloadOptions: () -> Unit,
    onPosition: () -> Unit,
    onRefreshToc: () -> Unit,
    onDelete: () -> Unit,
    onToggleList: (String) -> Unit,
    onToggleNotify: () -> Unit,
    onPluginAction: (actionId: String, on: Boolean?) -> Unit,
    hiddenActions: Set<String> = emptySet(),
) {
    val story = ui.story
    val context = LocalContext.current
    val book = story?.let { s -> ui.books.find { it.bookId == s.bookId } }
    val cover by rememberBookCover(book, maxEdge = FlowTokens.CoverEdge.Hero)
    val overlayOpen = ui.showPartial || ui.showPosition
    val model = remember(story, ui.busy, ui.downloadProgress, ui.downloadBookId, ui.error, overlayOpen, hiddenActions) {
        story?.let { s ->
            val progress = ui.downloadProgress?.takeIf { ui.downloadBookId == s.bookId }
            PluginMediaCardAdapter.model(
                manifest = ui.manifest,
                info = PluginMediaInfo(
                    title = s.title,
                    author = s.author,
                    workUrl = s.workUrl,
                    synopsis = s.synopsis,
                    tags = s.tags,
                    chapterCount = s.chapterCount,
                    downloadedCount = s.downloadedCount,
                    cachedIndices = s.cachedIndices,
                    locus = s.chapterIndex,
                    cacheLevel = s.cacheLevel,
                    listedIn = s.listedIn,
                    card = s.card,
                    cleanup = s.cleanup,
                    notify = s.notify,
                ),
                busy = ui.busy,
                downloadProgress = progress,
                error = ui.error?.takeIf { !overlayOpen },
            ).withoutHostActions(hiddenActions)
        }
    }
    FlowMediaCard(
        visible = visible && story != null,
        model = model,
        art = cover,
        onDismiss = onDismiss,
        onLongAction = { action -> if (action.id == MediaActionIds.DOWNLOAD) onDownloadOptions() },
        onSegmentsLongPress = onPosition,
        onAction = { action ->
            val s = story ?: return@FlowMediaCard
            if (action.owner == MediaActionOwner.Plugin) {
                onPluginAction(action.id, if (action.kind == MediaActionKind.Toggle) !action.on else null)
                return@FlowMediaCard
            }
            MediaActionIds.listIdOf(action.id)?.let { onToggleList(it); return@FlowMediaCard }
            when (action.id) {
                MediaActionIds.SHARE -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, s.title)
                        putExtra(Intent.EXTRA_TEXT, s.workUrl)
                    }
                    context.startActivity(Intent.createChooser(send, "Share story"))
                }
                MediaActionIds.NOTIFY -> onToggleNotify()
                MediaActionIds.DOWNLOAD -> onDownload()
                MediaActionIds.REFRESH -> onRefreshToc()
                MediaActionIds.DELETE -> onDelete()
                MediaActionIds.READ -> onRead()
            }
        },
    )
}
