package com.personal.flowreader.tts

import java.io.File

/**
 * Owner of the on-disk TTS clip cache folder (`cacheDir/tts`): hit lookup, atomic writes, the sweep
 * of files that can no longer serve the attached book, and clearing. Names and the stale rule are in
 * [ClipCacheNames]. No Android types, so it is unit tested against a temporary folder.
 *
 * Callers stay responsible for what sits outside the folder: cancelling synthesis jobs, dropping
 * decoded audio and UI state. [sweep] reports each removed file so they can do that.
 */
internal class TtsClipCache(val dir: File) {
    init {
        dir.mkdirs()
    }

    /**
     * The existing clip for [base] (the engine's [preferredExt] first, then `mp3`, then `wav`), or the
     * path a new clip should be written to when none exists.
     */
    fun hit(base: String, preferredExt: String): File {
        val preferred = File(dir, "$base.$preferredExt")
        if (preferred.exists()) return preferred
        val mp3 = File(dir, "$base.mp3")
        if (mp3.exists()) return mp3
        val wav = File(dir, "$base.wav")
        if (wav.exists()) return wav
        return preferred
    }

    fun wordsFile(base: String): File = File(dir, "$base.words.json")

    /** Write via temp + rename so a failed synthesis never leaves a truncated clip behind. */
    fun writeAtomically(target: File, bytes: ByteArray) {
        val tmp = File(dir, "${target.name}.part")
        try {
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(target)) {
                target.delete()
                if (!tmp.renameTo(target)) throw IllegalStateException("Unable to cache audio")
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    /**
     * Delete every file that can't serve [bookKey]'s [window] (see [ClipCacheNames.isStale]). [onRemove]
     * runs before each delete with the file and its parsed name (null for a foreign file).
     */
    fun sweep(
        bookKey: String,
        window: IntRange,
        textAt: (Int) -> String?,
        onRemove: (File, ClipCacheNames.Entry?) -> Unit = { _, _ -> },
    ) {
        dir.listFiles()?.forEach { file ->
            if (!ClipCacheNames.isStale(file.name, bookKey, window, textAt)) return@forEach
            onRemove(file, ClipCacheNames.parse(file.name))
            file.delete()
        }
    }

    /** Delete everything directly in the folder (`File.delete` leaves non-empty subfolders such as `sys-synth`). */
    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
