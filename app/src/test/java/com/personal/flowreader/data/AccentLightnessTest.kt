package com.personal.flowreader.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccentLightnessTest {
    @Test
    fun defaultIsStock() {
        assertEquals(1f, AccentLightness.DEFAULT, 0.0001f)
        assertEquals(0.40f, AccentLightness.scale(0.40f, AccentLightness.DEFAULT), 0.0001f)
    }

    @Test
    fun scaleRunsFromBlackThroughThemeToWhite() {
        assertEquals(0f, AccentLightness.scale(0.40f, 0f), 0.0001f)
        assertEquals(0.20f, AccentLightness.scale(0.40f, 0.5f), 0.0001f)
        assertEquals(0.70f, AccentLightness.scale(0.40f, 1.5f), 0.0001f)
        assertEquals(1f, AccentLightness.scale(0.40f, 2f), 0.0001f)
        assertEquals(1f, AccentLightness.scale(0.90f, 2f), 0.0001f)
    }

    @Test
    fun coerceClampsFactor() {
        assertEquals(AccentLightness.MIN, AccentLightness.coerce(-1f), 0.0001f)
        assertEquals(AccentLightness.MAX, AccentLightness.coerce(3f), 0.0001f)
        assertEquals(1f, AccentLightness.scale(0.40f, 9f), 0.0001f)
    }

    @Test
    fun missingPrefResolvesToDefault() {
        val stored: Float? = null
        assertEquals(
            AccentLightness.DEFAULT,
            AccentLightness.coerce(stored ?: AccentLightness.DEFAULT),
            0.0001f,
        )
    }
}
