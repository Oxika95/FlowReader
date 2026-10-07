package com.personal.flowreader.plugin.store

import com.personal.flowreader.data.BlockKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginChapterBlocksTest {
    @Test
    fun titleBecomesLeadingHeading() {
        val blocks = PluginBookStore.chapterBlocks("Chapter 3: Rain", "First.\n\nSecond.", "rr", 2)
        assertEquals(listOf("Chapter 3: Rain", "First.", "Second."), blocks.map { it.text })
        assertEquals(BlockKind.Heading, blocks[0].kind)
        assertEquals(BlockKind.Paragraph, blocks[1].kind)
    }

    @Test
    fun bodyOpeningWithTitleIsNotDuplicated() {
        val blocks = PluginBookStore.chapterBlocks("Chapter 3", "chapter 3\n\nBody.", "rr", 2)
        assertEquals(listOf("chapter 3", "Body."), blocks.map { it.text })
        assertEquals(BlockKind.Heading, blocks[0].kind)
    }

    @Test
    fun blankTitleAddsNothing() {
        val blocks = PluginBookStore.chapterBlocks(" ", "Body.", "rr", 0)
        assertEquals(listOf("Body."), blocks.map { it.text })
    }
}
