package com.personal.flowreader.library.plugin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compile-time library plugin. Registers a tab and optionally acts as a web source.
 * Files and Queue stay core tabs; they are not plugins.
 */
interface LibrarySourcePlugin {
    val id: String
    val title: String
    val subtitle: String get() = ""
    val enabled: Boolean get() = true

    @Composable
    fun TabContent(actions: LibraryPluginActions, modifier: Modifier)
}

/** Bridge from a plugin tab into the library host. Does not alter Files/Queue UI. */
interface LibraryPluginActions {
    fun ingestAndOpen(title: String, text: String)
    fun ingestAndQueue(title: String, text: String)
    fun openBook(bookId: String)
    fun queueBook(bookId: String)
    fun setBusy(busy: Boolean)
    fun showMessage(text: String)
    fun showError(text: String)
}

data class SourceWork(
    val title: String,
    val url: String,
    val author: String = "",
    val latestChapter: String = "",
)

data class SourceChapterRef(
    val title: String,
    val url: String,
)

data class SourceWorkDetail(
    val title: String,
    val url: String,
    val author: String = "",
    val synopsis: String = "",
    val chapters: List<SourceChapterRef>,
    val fictionId: String = "",
)

data class SourceChapter(
    val title: String,
    val url: String,
    val html: String,
    val text: String,
)

interface SourceRepository {
    suspend fun search(query: String): List<SourceWork> =
        throw NotImplementedError("search is not implemented")

    suspend fun browse(page: Int, order: String?): List<SourceWork> =
        throw NotImplementedError("browse is not implemented")

    suspend fun loadWork(url: String): SourceWorkDetail =
        throw NotImplementedError("loadWork is not implemented")

    suspend fun loadChapter(url: String): SourceChapter =
        throw NotImplementedError("loadChapter is not implemented")

    suspend fun login(username: String, password: String): Unit =
        throw NotImplementedError("login is not implemented")

    suspend fun logout(): Unit =
        throw NotImplementedError("logout is not implemented")

    suspend fun follows(): List<SourceWork> =
        throw NotImplementedError("follows is not implemented")

    suspend fun syncProgress(workId: String, chapterUrl: String): Unit =
        throw NotImplementedError("syncProgress is not implemented")
}

class LibraryPluginRegistry(val available: List<LibrarySourcePlugin>) {
    val pluginIds: Set<String> = available.map { it.id }.toSet()

    fun get(id: String): LibrarySourcePlugin? = available.find { it.id == id }

    fun enabled(ids: Set<String>): List<LibrarySourcePlugin> =
        available.filter { it.id in ids }
}
