package com.personal.flowreader.tts

import com.personal.flowreader.data.TtsPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeVoiceCatalogTest {
    private val sample = """
        [
          {"ShortName":"en-US-AndrewMultilingualNeural","Locale":"en-US","LocaleName":"English (United States)","Gender":"Male"},
          {"ShortName":"en-AU-NatashaNeural","Locale":"en-AU","LocaleName":"English (Australia)","Gender":"Female"},
          {"ShortName":"en-GB-RyanNeural","Locale":"en-GB","LocaleName":"English (United Kingdom)","Gender":"Male"},
          {"ShortName":"en-US-AriaNeural","Locale":"en-US","LocaleName":"English (United States)","Gender":"Female"},
          {"ShortName":"fr-FR-DeniseNeural","Locale":"fr-FR","FriendlyName":"Microsoft Denise Online (Natural) - French (France)","Gender":"Female"},
          {"ShortName":"zh-CN-liaoning-XiaobeiNeural","Locale":"zh-CN-liaoning","LocaleName":"Chinese (Northeastern Mandarin, Simplified)","Gender":"Female"},
          {"ShortName":"","Locale":"xx-XX"},
          {"ShortName":"en-US-AriaNeural","Locale":"en-US","LocaleName":"English (United States)","Gender":"Female"}
        ]
    """.trimIndent()

    @Test
    fun parseSplitsLanguageAndRegionAndSkipsBadRows() {
        val voices = EdgeVoiceCatalog.parse(sample)
        assertEquals(6, voices.size)
        val andrew = voices.first()
        assertEquals("English", andrew.language)
        assertEquals("United States", andrew.region)
        assertEquals("Andrew Multilingual", andrew.name)
        assertEquals("Andrew Multilingual · United States · Male", andrew.label)
        val denise = voices.first { it.id == "fr-FR-DeniseNeural" }
        assertEquals("French", denise.language)
        assertEquals("France", denise.region)
        assertEquals("Xiaobei", voices.first { it.locale == "zh-CN-liaoning" }.name)
    }

    @Test
    fun parseRejectsGarbage() {
        assertTrue(EdgeVoiceCatalog.parse("not json").isEmpty())
    }

    @Test
    fun optionsSortLanguagesThenPinnedRegions() {
        val options = EdgeVoiceCatalog.options(EdgeVoiceCatalog.parse(sample))
        assertEquals(
            listOf(
                "zh-CN-liaoning-XiaobeiNeural",
                "en-US-AndrewMultilingualNeural",
                "en-US-AriaNeural",
                "en-GB-RyanNeural",
                "en-AU-NatashaNeural",
                "fr-FR-DeniseNeural",
            ),
            options.map { it.id },
        )
        assertEquals("English", options[1].language)
    }

    @Test
    fun langOfUsesLanguageRegion() {
        val voices = EdgeVoiceCatalog.parse(sample)
        assertEquals("zh-CN", EdgeVoiceCatalog.langOf("zh-CN-liaoning-XiaobeiNeural", voices))
        assertEquals("fr-FR", EdgeVoiceCatalog.langOf("fr-FR-DeniseNeural", voices))
        assertEquals("de-DE", EdgeVoiceCatalog.langOf("de-DE-KatjaNeural", emptyList()))
    }

    @Test
    fun bundledSnapshotHasEveryLanguageAndTheDefaultVoice() {
        val voices = EdgeVoiceCatalog.bundled()
        assertTrue(voices.size > 300)
        assertTrue(voices.any { it.id == TtsPrefs.DEFAULT_EDGE_VOICE })
        assertTrue(voices.map { it.language }.distinct().size > 50)
    }
}
