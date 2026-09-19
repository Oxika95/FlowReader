package com.personal.flowreader.ui.reader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.BookDoc
import com.personal.flowreader.data.EpubIngest
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.TxtIngest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReaderUi(
    val title: String = "",
    val doc: BookDoc? = null,
    val locus: Locus = Locus(),
    val error: String? = null,
    val loading: Boolean = true,
)

class ReaderViewModel(
    app: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    val bookId: String = savedStateHandle["bookId"] ?: ""
    val tts = flow.tts

    private val _ui = MutableStateFlow(ReaderUi())
    val ui: StateFlow<ReaderUi> = _ui

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val row = flow.db.progress().get(bookId)
                ?: throw IllegalArgumentException("Book not found")
            val file = File(row.storedPath)
            val doc = withContext(Dispatchers.IO) {
                if (file.extension.equals("txt", true)) TxtIngest.read(file) else EpubIngest.read(file)
            }
            val locus = Locus(row.chapterIndex, row.blockIndex, row.charOffset)
            tts.attach(doc, locus)
            _ui.value = ReaderUi(title = doc.title, doc = doc, locus = locus, loading = false)
        } catch (t: Throwable) {
            _ui.value = ReaderUi(error = t.message ?: "Failed to open", loading = false)
        }
    }

    fun jumpToChapter(chapterIndex: Int) {
        val doc = _ui.value.doc ?: return
        val ci = chapterIndex.coerceIn(0, doc.chapters.lastIndex)
        jumpTo(Locus(ci, 0, 0))
    }

    fun jumpTo(locus: Locus) {
        onLocus(locus)
        tts.jumpTo(locus)
    }

    fun onLocus(locus: Locus) {
        _ui.value = _ui.value.copy(locus = locus)
        persist(locus)
    }

    /** Flush current UI locus using app scope so it survives ViewModel teardown. */
    fun persistNow() {
        persist(_ui.value.locus)
    }

    private fun persist(locus: Locus) {
        val id = bookId
        if (id.isBlank()) return
        flow.appScope.launch {
            val row = flow.db.progress().get(id) ?: return@launch
            flow.db.progress().upsert(
                ProgressEntity(
                    bookId = row.bookId,
                    title = row.title,
                    storedPath = row.storedPath,
                    chapterIndex = locus.chapterIndex,
                    blockIndex = locus.blockIndex,
                    charOffset = locus.charOffset,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onCleared() {
        persistNow()
        tts.pause()
        super.onCleared()
    }
}
