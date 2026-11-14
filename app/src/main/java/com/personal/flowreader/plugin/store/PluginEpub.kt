package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Chapter
import com.personal.flowreader.data.EpubChapterSource
import java.io.File

/** EPUB file under a plugin data dir for sessions that set [PluginReadSession.epubFile]. */
internal object PluginEpub {
    fun file(root: File, session: PluginReadSession): File? {
        val relative = session.epubFile.trim().trimStart('/').replace('\\', '/')
        if (relative.isEmpty() || relative.contains("..")) return null
        return File(root, relative).takeIf { it.isFile }
    }

    fun loadChapter(root: File, session: PluginReadSession, index: Int): Chapter? {
        val epub = file(root, session) ?: return null
        return runCatching { EpubChapterSource.open(epub).load(index) }.getOrNull()
    }

    fun weights(root: File, session: PluginReadSession): LongArray? {
        if (!session.hasEpub) return null
        val epub = file(root, session) ?: return null
        return runCatching {
            val source = EpubChapterSource.open(epub)
            LongArray(source.chapterCount) { i -> source.weight(i).coerceAtLeast(1L) }
        }.getOrNull()
    }
}
