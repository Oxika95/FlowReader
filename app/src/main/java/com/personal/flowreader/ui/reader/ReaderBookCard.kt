package com.personal.flowreader.ui.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.ui.design.card.media.MediaActionIds
import com.personal.flowreader.ui.library.FilesBookSplash
import com.personal.flowreader.ui.plugin.PluginStoryOverlays
import com.personal.flowreader.ui.plugin.rememberPluginTabViewModel

/** The book is already open, so the library card's open and remove actions don't apply. */
private val ReaderHiddenActions = setOf(
    MediaActionIds.OPEN,
    MediaActionIds.READ,
    MediaActionIds.REMOVE,
    MediaActionIds.DELETE,
)

/**
 * Library media card for the open book (title card long-press). Each increment of [openRequests]
 * opens it; local files get the Files card, plugin stories the shared story card.
 */
@Composable
internal fun ReaderBookCard(bookId: String, openRequests: Int) {
    if (openRequests == 0) return
    val app = LocalContext.current.applicationContext as FlowApp
    val pluginId = remember(bookId) { app.pluginBooks.pluginIdFor(bookId) }
    if (pluginId != null) {
        val installed by app.pluginManager.installed.collectAsState()
        val plugin = installed.firstOrNull { it.id == pluginId } ?: return
        val vm = rememberPluginTabViewModel(plugin)
        LaunchedEffect(openRequests) { vm.openStory(bookId) }
        PluginStoryOverlays(vm = vm, onRead = vm::closeStory, hiddenActions = ReaderHiddenActions)
        return
    }
    var row by remember { mutableStateOf<BookItem?>(null) }
    LaunchedEffect(openRequests) { row = app.catalog.cardItem(bookId) }
    FilesBookSplash(
        book = row,
        busy = false,
        onDismiss = { row = null },
        onOpen = {},
        onRemove = {},
        hiddenActions = ReaderHiddenActions,
    )
}
