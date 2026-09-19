package com.personal.flowreader.ui.reader

import android.app.SearchManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus

/**
 * Floating ActionMode toolbar for reader selection: Copy, Share, Web search, Select all.
 * Selected text is obtained via Compose's copy callback + clipboard (SelectionContainer).
 */
internal class ReaderTextToolbar(
    private val view: View,
    private val onSelectionUiChanged: (Boolean) -> Unit = {},
) : TextToolbar {
    private var actionMode: ActionMode? = null
    private var menuRect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        this.menuRect = rect
        this.onCopyRequested = onCopyRequested
        this.onSelectAllRequested = onSelectAllRequested
        val mode = actionMode
        if (mode == null) {
            actionMode = view.startActionMode(callback, ActionMode.TYPE_FLOATING)
        } else {
            mode.invalidateContentRect()
            mode.invalidate()
        }
        status = TextToolbarStatus.Shown
        onSelectionUiChanged(true)
    }

    override fun hide() {
        actionMode?.finish()
        actionMode = null
        status = TextToolbarStatus.Hidden
        onCopyRequested = null
        onSelectAllRequested = null
        onSelectionUiChanged(false)
    }

    private fun clipboardText(): String {
        val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return cm.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(view.context)
            ?.toString()
            .orEmpty()
    }

    private fun copyThen(consume: (String) -> Unit) {
        onCopyRequested?.invoke()
        val text = clipboardText().trim()
        if (text.isNotEmpty()) consume(text)
    }

    private fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        view.context.startActivity(Intent.createChooser(send, null))
    }

    private fun webSearch(text: String) {
        val search = Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(SearchManager.QUERY, text)
        }
        try {
            view.context.startActivity(search)
        } catch (_: Throwable) {
            view.context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=${Uri.encode(text)}"),
                ),
            )
        }
    }

    private val callback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(0, ID_COPY, 0, android.R.string.copy)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, ID_SHARE, 1, "Share")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            menu.add(0, ID_WEB_SEARCH, 2, "Web search")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            if (onSelectAllRequested != null) {
                menu.add(0, ID_SELECT_ALL, 3, android.R.string.selectAll)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                ID_COPY -> onCopyRequested?.invoke()
                ID_SHARE -> copyThen { share(it) }
                ID_WEB_SEARCH -> copyThen { webSearch(it) }
                ID_SELECT_ALL -> onSelectAllRequested?.invoke()
                else -> return false
            }
            mode.finish()
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null
            status = TextToolbarStatus.Hidden
            onSelectionUiChanged(false)
        }

        override fun onGetContentRect(mode: ActionMode, view: View, outRect: android.graphics.Rect) {
            outRect.set(
                menuRect.left.toInt(),
                menuRect.top.toInt(),
                menuRect.right.toInt(),
                menuRect.bottom.toInt(),
            )
        }
    }

    companion object {
        private const val ID_COPY = 1
        private const val ID_SHARE = 2
        private const val ID_WEB_SEARCH = 3
        private const val ID_SELECT_ALL = 4
    }
}
