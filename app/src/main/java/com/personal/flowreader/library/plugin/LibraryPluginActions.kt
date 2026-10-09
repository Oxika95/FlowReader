package com.personal.flowreader.library.plugin

/** Bridge from a plugin tab into the library host. Does not alter Files/Queue UI. */
interface LibraryPluginActions {
    fun openBook(bookId: String)
    fun setBusy(busy: Boolean)
    fun showMessage(text: String)
    fun showError(text: String)
}
