package com.personal.flowreader.tts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TtsClipCacheTest {
    @get:Rule val tmp = TemporaryFolder()

    private val book = ClipCacheNames.bookKey("rr:1", 0)
    private val voice = ClipCacheNames.voiceKey("edge_en-US-Aria_0_0")
    private val texts = mapOf(3 to "Hello there.", 4 to "Second sentence.")
    private val textAt: (Int) -> String? = { texts[it] }

    private fun cache() = TtsClipCache(File(tmp.root, "tts"))
    private fun base(i: Int, text: String, bookKey: String = book) = ClipCacheNames.base(bookKey, i, voice, text)
    private fun TtsClipCache.touch(name: String) = File(dir, name).apply { writeText("x") }

    @Test
    fun hitPrefersEngineExtensionThenMp3ThenWav() {
        val c = cache()
        val b = base(3, "Hello there.")
        assertEquals("$b.wav", c.hit(b, "wav").name)
        c.touch("$b.wav")
        assertEquals("$b.wav", c.hit(b, "mp3").name)
        c.touch("$b.mp3")
        assertEquals("$b.mp3", c.hit(b, "mp3").name)
        assertEquals("$b.wav", c.hit(b, "wav").name)
    }

    @Test
    fun hitIsKeyedByText() {
        val c = cache()
        c.touch(base(3, "Hello there.") + ".mp3")
        assertTrue(c.hit(base(3, "Hello there."), "mp3").exists())
        assertFalse(c.hit(base(3, "Hello there!"), "mp3").exists())
    }

    @Test
    fun writeAtomicallyLeavesNoPartFile() {
        val c = cache()
        val target = File(c.dir, base(3, "Hello there.") + ".mp3")
        c.writeAtomically(target, byteArrayOf(1, 2, 3))
        assertEquals(3L, target.length())
        assertEquals(listOf(target.name), c.dir.list()!!.toList())
        c.writeAtomically(target, byteArrayOf(9))
        assertEquals(1L, target.length())
    }

    @Test
    fun sweepRemovesStaleFilesAndReportsThem() {
        val c = cache()
        val keep = c.touch(base(3, "Hello there.") + ".mp3")
        val keepWords = c.touch(c.wordsFile(base(3, "Hello there.")).name)
        val part = c.touch(base(4, "Second sentence.") + ".mp3.part")
        val oldText = c.touch(base(4, "Old text.") + ".mp3")
        val outside = c.touch(base(9, "Far.") + ".mp3")
        val foreign = c.touch(base(3, "Hello there.", "rr-2.o0") + ".mp3")
        val foreignName = c.touch("filter_preview.mp3")

        val removed = mutableListOf<Pair<String, ClipCacheNames.Entry?>>()
        c.sweep(book, 2..5, textAt) { f, e -> removed += f.name to e }

        assertTrue(keep.exists() && keepWords.exists() && part.exists())
        assertFalse(oldText.exists() || outside.exists() || foreign.exists() || foreignName.exists())
        assertEquals(
            setOf(oldText.name, outside.name, foreign.name, foreignName.name),
            removed.map { it.first }.toSet(),
        )
        assertEquals(null, removed.first { it.first == foreignName.name }.second)
        assertEquals(9, removed.first { it.first == outside.name }.second!!.index)
    }

    @Test
    fun clearRemovesEveryFile() {
        val c = cache()
        c.touch(base(3, "Hello there.") + ".mp3")
        c.touch(base(3, "Hello there.") + ".words.json")
        c.clear()
        assertEquals(0, c.dir.list()!!.size)
    }
}
