package com.personal.flowreader.tts

import com.personal.flowreader.tts.ClipCacheNames.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipCacheNamesTest {
    private val book = "rr-1.o0"
    private val voice = "edge_en-US-Aria_0_0_fabc12"
    private val texts = mapOf(3 to "Hello there.", 4 to "Second sentence.")
    private val textAt: (Int) -> String? = { texts[it] }

    private fun clip(i: Int, text: String) = ClipCacheNames.base(book, i, voice, text) + ".mp3"
    private fun words(i: Int, text: String) = ClipCacheNames.base(book, i, voice, text) + ".words.json"

    @Test
    fun differentTextAtSameIndexGetsDifferentName() {
        assertNotEquals(clip(3, "Hello there."), clip(3, "Hello there!"))
        assertEquals(clip(3, "Hello there."), clip(3, "Hello there."))
    }

    @Test
    fun bookAndVoiceKeysReplaceCharactersTheNameRegexRejects() {
        assertEquals("rr-123.o4", ClipCacheNames.bookKey("rr:123", 4))
        assertEquals("a-b-c.o0", ClipCacheNames.bookKey("a/b_c", 0))
        assertEquals("edge_en-US-Aria_0_0_fabc", ClipCacheNames.voiceKey("edge_en-US-Aria_0_0_fabc"))
        assertEquals("edge_a_b_c", ClipCacheNames.voiceKey("edge a:b/c"))
        val name = ClipCacheNames.base(ClipCacheNames.bookKey("rr:1/x", 2), 5, ClipCacheNames.voiceKey("v o"), "t") + ".mp3"
        assertEquals(5, ClipCacheNames.parse(name)!!.index)
    }

    @Test
    fun differentVoiceOrFilterGetsDifferentName() {
        val a = ClipCacheNames.base(book, 3, voice, "x")
        val b = ClipCacheNames.base(book, 3, "edge_en-US-Aria_0_0", "x")
        assertNotEquals(a, b)
    }

    @Test
    fun parseReadsBookIndexKindAndTextKey() {
        val clip = ClipCacheNames.parse(clip(3, "Hello there."))!!
        assertEquals(book, clip.bookKey)
        assertEquals(3, clip.index)
        assertEquals(Kind.CLIP, clip.kind)
        assertEquals(ClipCacheNames.textKey("Hello there."), clip.textKey)
        assertEquals(Kind.WORDS, ClipCacheNames.parse(words(3, "Hello there."))!!.kind)
        assertEquals(Kind.PART, ClipCacheNames.parse(clip(3, "Hello there.") + ".part")!!.kind)
        assertEquals(Kind.CLIP, ClipCacheNames.parse(clip(3, "x").replace(".mp3", ".wav"))!!.kind)
    }

    @Test
    fun parseRejectsForeignAndLegacyNames() {
        assertNull(ClipCacheNames.parse("preview.mp3"))
        assertNull(ClipCacheNames.parse("b${book}_s3_${voice}.mp3"))
        assertNull(ClipCacheNames.parse("b${book}_s3_${voice}.words.json"))
    }

    @Test
    fun sidecarOfAttachedBookInWindowIsKept() {
        assertFalse(ClipCacheNames.isStale(clip(3, "Hello there."), book, 2..5, textAt))
        assertFalse(ClipCacheNames.isStale(words(3, "Hello there."), book, 2..5, textAt))
    }

    @Test
    fun inFlightPartFileOfAttachedBookIsKept() {
        assertFalse(ClipCacheNames.isStale(clip(3, "Hello there.") + ".part", book, 2..5, textAt))
        assertFalse(ClipCacheNames.isStale(clip(99, "late") + ".part", book, 2..5, textAt))
    }

    @Test
    fun foreignBookFilesAreStale() {
        val other = ClipCacheNames.base("rr-2.o0", 3, voice, "Hello there.")
        assertTrue(ClipCacheNames.isStale("$other.mp3", book, 2..5, textAt))
        assertTrue(ClipCacheNames.isStale("$other.words.json", book, 2..5, textAt))
        assertTrue(ClipCacheNames.isStale("$other.mp3.part", book, 2..5, textAt))
        assertTrue(ClipCacheNames.isStale("preview.mp3", book, 2..5, textAt))
    }

    @Test
    fun outsideWindowClipAndSidecarAreStale() {
        assertTrue(ClipCacheNames.isStale(clip(3, "Hello there."), book, 4..6, textAt))
        assertTrue(ClipCacheNames.isStale(words(3, "Hello there."), book, 4..6, textAt))
    }

    @Test
    fun clipForOldTextIsStaleWhenSentenceChanged() {
        assertTrue(ClipCacheNames.isStale(clip(3, "Old text."), book, 2..5, textAt))
        assertTrue(ClipCacheNames.isStale(words(3, "Old text."), book, 2..5, textAt))
    }

    @Test
    fun missingSentenceIsStale() {
        assertTrue(ClipCacheNames.isStale(clip(5, "Gone."), book, 2..5, textAt))
    }
}
