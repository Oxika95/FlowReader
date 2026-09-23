package com.personal.flowreader.ui.common

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.ProgressEntity
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val RoyalRoadSourceId = "royalroad"

/** Shared cover remember helper for library cards, reader banner, and splashes. */
@Composable
fun rememberBookCover(book: ProgressEntity?, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = null, book?.bookId, book?.storedPath, book?.sourceKind, maxEdge) {
        value = book?.let { row ->
            withContext(Dispatchers.IO) {
                loadBookCoverBitmap(row, maxEdge)?.asImageBitmap()
            }
        }
    }

/** Cover from a stored book path (sidecar first, then EPUB embedded). */
@Composable
fun rememberBookCover(storedPath: String, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = null, storedPath, maxEdge) {
        value = if (storedPath.isBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                loadBookCoverBitmap(storedPath, maxEdge)?.asImageBitmap()
            }
        }
    }

fun loadBookCoverBitmap(book: ProgressEntity, maxEdge: Int): android.graphics.Bitmap? {
    if (book.sourceKind == RoyalRoadSourceId || book.bookId.startsWith("rr:")) {
        val sidecar = sidecarCoverFile(File(book.storedPath).parentFile)
        if (sidecar != null) return decodeScaled(sidecar, maxEdge)
    }
    return EpubCover.loadBitmap(File(book.storedPath), maxEdge)
}

fun loadBookCoverBitmap(storedPath: String, maxEdge: Int): android.graphics.Bitmap? {
    if (storedPath.isBlank()) return null
    val file = File(storedPath)
    val sidecar = sidecarCoverFile(file.parentFile)
    if (sidecar != null) return decodeScaled(sidecar, maxEdge)
    return EpubCover.loadBitmap(file, maxEdge)
}

private fun sidecarCoverFile(dir: File?): File? =
    listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
        .mapNotNull { name -> dir?.let { File(it, name) } }
        .firstOrNull { it.exists() && it.length() > 0L }

private fun decodeScaled(file: File, maxEdge: Int): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    while (longest / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}
