package com.personal.flowreader.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Size-capped image decode shared by covers, tab icons and notifications. The decoder reads the
 * image size first and subsamples by powers of two, so the full bitmap is never held in memory.
 */
object ScaledBitmap {
    /** Largest power-of-two divisor that keeps the longest edge at or under [maxEdge]; 1 when it already fits. */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxEdge) sample *= 2
        return sample
    }

    /** Null when the file is missing, not an image, or cannot be decoded. */
    fun decodeFile(file: File, maxEdge: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge) }
        BitmapFactory.decodeFile(file.absolutePath, opts)
    }.getOrNull()

    /** Null when the bytes are not an image or cannot be decoded. */
    fun decodeBytes(bytes: ByteArray, maxEdge: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxEdge) }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}
