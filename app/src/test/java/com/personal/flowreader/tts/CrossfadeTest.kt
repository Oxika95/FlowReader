package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CrossfadeTest {
    @Test
    fun equalPowerMidpointIsConstantPower() {
        val (a, b) = Crossfade.gains(0.5f, Crossfade.Curve.EqualPower)
        val power = a * a + b * b
        assertTrue("power=$power", abs(power - 1f) < 0.01f)
        assertTrue(abs(a - b) < 0.01f)
    }

    @Test
    fun equalPowerEndpoints() {
        val (a0, b0) = Crossfade.gains(0f, Crossfade.Curve.EqualPower)
        assertEquals(1f, a0, 0.001f)
        assertEquals(0f, b0, 0.001f)
        val (a1, b1) = Crossfade.gains(1f, Crossfade.Curve.EqualPower)
        assertEquals(0f, a1, 0.001f)
        assertEquals(1f, b1, 0.001f)
    }

    @Test
    fun clampOverlapNeverExceedsHalfShorter() {
        // 1s current, 0.4s next at 24kHz mono → half shorter = 200ms
        val sampleRate = 24_000
        val current = sampleRate // 1s
        val next = sampleRate / 5 // 0.2s → wait 0.4s = sampleRate*0.4
        val nextSamples = (sampleRate * 0.4).toInt()
        val clamped = Crossfade.clampOverlapMs(
            requestedMs = 1000,
            currentSampleCount = current,
            nextSampleCount = nextSamples,
            sampleRate = sampleRate,
            channels = 1,
        )
        assertEquals(200, clamped)
    }

    @Test
    fun clampZeroWhenRequestedNonPositive() {
        assertEquals(
            0,
            Crossfade.clampOverlapMs(0, 48000, 48000, 48000, 1),
        )
    }

    @Test
    fun mixSampleAtMidpointIsAverageScale() {
        val mixed = Crossfade.mixSample(10000, 10000, 0.5f)
        // equal-power at 0.5 ≈ 0.707 each → ~14140 before clamp... 10000*0.707*2 ≈ 14140
        assertTrue(mixed > 10000)
        assertTrue(mixed < 16000)
    }
}
