package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusResumeTest {
    @Test
    fun resumesWithinCap() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 1_000)
        assertEquals(FocusGain(elapsedMs = 30_000, resume = true), f.onGain(31_000))
    }

    @Test
    fun resumesAtExactlyCap() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 0)
        assertTrue(f.onGain(FOCUS_RESUME_MAX_MS)!!.resume)
    }

    @Test
    fun noResumePastCap() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 0)
        val gain = f.onGain(FOCUS_RESUME_MAX_MS + 1)!!
        assertFalse(gain.resume)
        assertEquals(FOCUS_RESUME_MAX_MS + 1, gain.elapsedMs)
    }

    @Test
    fun noResumeWhenNotPlayingAtLoss() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = false, nowMs = 0)
        assertFalse(f.armed)
        assertNull(f.onGain(1_000))
    }

    @Test
    fun clearDisarms() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 0)
        f.clear()
        assertNull(f.onGain(1_000))
    }

    @Test
    fun gainDisarms() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 0)
        assertTrue(f.onGain(1_000)!!.resume)
        assertNull(f.onGain(2_000))
    }

    @Test
    fun repeatedLossKeepsFirstTimestamp() {
        val f = FocusResume()
        f.onTransientLoss(wasPlaying = true, nowMs = 0)
        f.onTransientLoss(wasPlaying = false, nowMs = 10_000)
        assertEquals(60_000, f.onGain(60_000)!!.elapsedMs)
    }
}
