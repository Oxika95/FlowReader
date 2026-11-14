package com.personal.flowreader.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.personal.flowreader.data.EpubCover
import com.personal.flowreader.data.BookItem
import com.personal.flowreader.data.ScaledBitmap
import com.personal.flowreader.net.HttpClients
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Shared cover remember helper for library cards, reader banner, and splashes. */
@Composable
fun rememberBookCover(book: BookItem?, maxEdge: Int): androidx.compose.runtime.State<ImageBitmap?> =
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

fun loadBookCoverBitmap(book: BookItem, maxEdge: Int): android.graphics.Bitmap? {
    val file = File(book.storedPath)
    // Plugin stories keep a downloaded cover next to their text snapshot.
    if (!file.name.endsWith(".epub", ignoreCase = true)) {
        val sidecar = sidecarCoverFile(file.parentFile)
        if (sidecar != null) return ScaledBitmap.decodeFile(sidecar, maxEdge)
    }
    return EpubCover.loadBitmap(file, maxEdge)
}

fun loadBookCoverBitmap(storedPath: String, maxEdge: Int): android.graphics.Bitmap? {
    if (storedPath.isBlank()) return null
    val file = File(storedPath)
    val sidecar = sidecarCoverFile(file.parentFile)
    if (sidecar != null) return ScaledBitmap.decodeFile(sidecar, maxEdge)
    return EpubCover.loadBitmap(file, maxEdge)
}

/** Remote cover (plugin search results) with an in-memory LRU; null while loading or on failure. */
@Composable
fun rememberRemoteCover(
    url: String,
    maxEdge: Int,
    bearerToken: String? = null,
): androidx.compose.runtime.State<ImageBitmap?> =
    produceState(initialValue = RemoteCovers.cached(url, maxEdge, bearerToken), url, maxEdge, bearerToken) {
        if (value == null && (url.startsWith("http://") || url.startsWith("https://"))) {
            value = withContext(Dispatchers.IO) { RemoteCovers.load(url, maxEdge, bearerToken) }
        }
    }

private object RemoteCovers {
    private const val MAX_ENTRIES = 64
    private const val MAX_BYTES = 4L * 1024 * 1024
    private val cache = object : LinkedHashMap<String, ImageBitmap>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?) = size > MAX_ENTRIES
    }
    private val http by lazy { HttpClients.create(connectSeconds = 15, readSeconds = 20, callSeconds = 40) }

    private fun key(url: String, maxEdge: Int, bearer: String?) = "$maxEdge|${bearer?.take(8).orEmpty()}|$url"

    fun cached(url: String, maxEdge: Int, bearer: String? = null): ImageBitmap? =
        synchronized(cache) { cache[key(url, maxEdge, bearer)] }

    fun load(url: String, maxEdge: Int, bearer: String? = null): ImageBitmap? = runCatching {
        val builder = Request.Builder().url(url)
        if (!bearer.isNullOrBlank()) builder.header("Authorization", "Bearer $bearer")
        http.newCall(builder.build()).execute().use { resp ->
            val body = resp.body ?: return null
            if (!resp.isSuccessful || body.contentLength() > MAX_BYTES) return null
            val bmp = ScaledBitmap.decodeBytes(body.bytes(), maxEdge) ?: return null
            bmp.asImageBitmap().also { synchronized(cache) { cache[key(url, maxEdge, bearer)] = it } }
        }
    }.getOrNull()
}

private fun sidecarCoverFile(dir: File?): File? =
    listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")
        .mapNotNull { name -> dir?.let { File(it, name) } }
        .firstOrNull { it.exists() && it.length() > 0L }