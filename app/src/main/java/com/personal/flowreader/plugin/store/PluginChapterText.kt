package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.Block
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.plugin.api.PluginChapter
import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.share.HtmlParagraphs
import org.jsoup.Jsoup

/** Flatten plugin chapter HTML/text into reader [Block]s. */
internal object PluginChapterText {
    fun chapterBlocks(title: String, text: String, prefix: String, chapterIndex: Int): List<Block> {
        val body = if (text.isBlank()) emptyList() else blocksFromPlain(text, prefix, chapterIndex)
        val heading = title.trim()
        if (heading.isEmpty()) return body
        val first = body.firstOrNull()
        if (first != null && first.text.trim().equals(heading, ignoreCase = true)) {
            return listOf(first.copy(kind = BlockKind.Heading)) + body.drop(1)
        }
        return listOf(Block("$prefix-$chapterIndex-title", BlockKind.Heading, heading)) + body
    }

    fun blocksFromPlain(text: String, prefix: String, chapterIndex: Int): List<Block> {
        val paras = text.replace("\r\n", "\n").split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (paras.isEmpty()) {
            return listOf(Block("$prefix-$chapterIndex-0", BlockKind.Paragraph, text.trim()))
        }
        return paras.mapIndexed { i, p -> Block("$prefix-$chapterIndex-$i", BlockKind.Paragraph, p) }
    }

    fun blocksFor(chapter: PluginChapter, prefix: String, chapterIndex: Int): List<Block> {
        if (chapter.html.isNotBlank()) {
            val paras = HtmlParagraphs.of(Jsoup.parseBodyFragment(chapter.html).body())
            if (paras.isNotEmpty()) {
                return paras.mapIndexed { i, p -> Block("$prefix-$chapterIndex-$i", BlockKind.Paragraph, p) }
            }
        }
        return blocksFromPlain(chapter.text, prefix, chapterIndex)
    }

    fun titleAndText(
        chapter: PluginChapter,
        fallbackTitle: String,
        prefix: String,
        chapterIndex: Int,
    ): Pair<String, String> {
        val text = blocksFor(chapter, prefix, chapterIndex).joinToString("\n\n") { it.text }.trim()
        if (text.isBlank()) {
            throw PluginException(PluginErrorCode.Parse, "Chapter page had no readable text")
        }
        return chapter.title.ifBlank { fallbackTitle.ifBlank { "Chapter ${chapterIndex + 1}" } } to text
    }
}
