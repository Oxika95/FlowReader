package com.personal.flowreader.ui.common

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.ProgressEntity
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
    val file = File(book.storedPath)
    // Plugin stories keep a downloaded cover next to their text snapshot.
    if (!file.name.endsWith(".epub", ignoreCase = true)) {
        val sidecar = sidecarCoverFile(file.parentFile)
        if (sidecar != null) return decodeScaled(sidecar, maxEdge)
    }
    return EpubCover.loadBitmap(file, maxEdge)
}

fun loadBookCoverBitmap(storedPath: String, maxEdge: Int): android.graphics.Bitmap? {
    if (storedPath.isBlank()) return null
    val file = File(storedPath)
    val sidecar = sidecarCoverFile(file.parentFile)
    if (sidecar != null) return decodeScaled(sidecar, maxEdge)
    return EpubCover.loadBitmap(file, maxEdge)
}

/** Remote cover (plugin search results) with an in-memory LRU; null while loading or on failure. */
@Composable
fun rememberRemoteCover(url: String, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = RemoteCovers.cached(url, maxEdge), url, maxEdge) {
        if (value == null && url.startsWith("http")) {
            value = withContext(Dispatchers.IO) { RemoteCovers.load(url, maxEdge) }
        }
    }

private object RemoteCovers {
    private const val MAX_ENTRIES = 64
    private const val MAX_BYTES = 4L * 1024 * 1024
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_ENTRIES
    }
    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun key(url: String, maxEdge: Int) = "$maxEdge|$url"

    fun cached(url: String, maxEdge: Int): ImageBitmap? = synchronized(cache) { cache[key(url, maxEdge)] }

    fun load(url: String, maxEdge: Int): ImageBitmap? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val body = resp.body ?: return null
            if (!resp.isSuccessful || body.contentLength() > MAX_BYTES) return null
            val bytes = body.bytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxEdge) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            bmp.asImageBitmap().also { synchronized(cache) { cache[key(url, maxEdge)] = it } }
        }
    }.getOrNull()
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
