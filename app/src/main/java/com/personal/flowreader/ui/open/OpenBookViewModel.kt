package com.personal.flowreader.ui.open

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.EpubIngest
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TxtIngest
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class OpenUi(
    val theme: ThemeMode = ThemeMode.Oled,
    val accentHue: Float = AccentHue.DEFAULT,
    val fontScale: Float = 1f,
    val fontFamily: ReaderFont = ReaderFont.Sans,
    val lineSpacing: Float = 1f,
    val orientation: ReaderOrientation = ReaderOrientation.Auto,
    val lastTitle: String? = null,
    val lastId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val ready: Boolean = false,
)

class OpenBookViewModel(app: Application) : AndroidViewModel(app) {
    private val flow = app as FlowApp
    private val _ui = MutableStateFlow(OpenUi())
    val ui: StateFlow<OpenUi> = _ui

    init {
        viewModelScope.launch {
            val prefs = flow.settings.readerOnce()
            val last = flow.db.progress().latest()
            _ui.value = _ui.value.copy(
                theme = prefs.theme,
                accentHue = prefs.accentHue,
                fontScale = prefs.fontScale,
                fontFamily = prefs.fontFamily,
                lineSpacing = prefs.lineSpacing,
                orientation = prefs.orientation,
                lastTitle = last?.title,
                lastId = last?.bookId,
                ready = true,
            )
        }
    }

    fun setTheme(mode: ThemeMode) {
        _ui.value = _ui.value.copy(theme = mode)
        viewModelScope.launch { flow.settings.setTheme(mode) }
    }

    fun setAccentHue(hue: Float) {
        val value = hue.coerceIn(AccentHue.MIN, AccentHue.MAX)
        _ui.value = _ui.value.copy(accentHue = value)
        viewModelScope.launch { flow.settings.setAccentHue(value) }
    }

    fun setFontScale(scale: Float) {
        val value = scale.coerceIn(0.85f, 1.75f)
        _ui.value = _ui.value.copy(fontScale = value)
        viewModelScope.launch { flow.settings.setFontScale(value) }
    }

    fun setFontFamily(font: ReaderFont) {
        _ui.value = _ui.value.copy(fontFamily = font)
        viewModelScope.launch { flow.settings.setFontFamily(font) }
    }

    fun setLineSpacing(spacing: Float) {
        val value = spacing.coerceIn(0.85f, 1.8f)
        _ui.value = _ui.value.copy(lineSpacing = value)
        viewModelScope.launch { flow.settings.setLineSpacing(value) }
    }

    fun setOrientation(orientation: ReaderOrientation) {
        _ui.value = _ui.value.copy(orientation = orientation)
        viewModelScope.launch { flow.settings.setOrientation(orientation) }
    }

    fun openUri(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                val id = withContext(Dispatchers.IO) { import(uri) }
                val last = flow.db.progress().get(id)
                _ui.value = _ui.value.copy(
                    busy = false,
                    lastId = id,
                    lastTitle = last?.title,
                )
                onDone(id)
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(busy = false, error = t.message ?: "Could not open file")
            }
        }
    }

    private suspend fun import(uri: Uri): String {
        val cr = getApplication<Application>().contentResolver
        val tmp = File(flow.cacheDir, "import-${UUID.randomUUID()}")
        cr.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalArgumentException("Could not read file")
        val id = sha256(tmp)
        val destDir = File(flow.booksDir, id).apply { mkdirs() }
        val ext = if (looksLikeTxt(tmp)) "txt" else "epub"
        val dest = File(destDir, "book.$ext")
        tmp.copyTo(dest, overwrite = true)
        tmp.delete()
        val doc = if (ext == "txt") TxtIngest.read(dest) else EpubIngest.read(dest)
        val existing = flow.db.progress().get(id)
        flow.db.progress().upsert(
            ProgressEntity(
                bookId = id,
                title = doc.title,
                storedPath = dest.absolutePath,
                chapterIndex = existing?.chapterIndex ?: 0,
                blockIndex = existing?.blockIndex ?: 0,
                charOffset = existing?.charOffset ?: 0,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    private fun looksLikeTxt(file: File): Boolean {
        val head = ByteArray(4)
        file.inputStream().use { it.read(head) }
        val asText = head.decodeToString()
        return !asText.startsWith("PK") && head[0] != 0.toByte()
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
