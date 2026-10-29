package com.personal.flowreader.tts

import com.personal.flowreader.data.TtsVoiceOption
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsVoicePickTest {
    private val voices = listOf(
        TtsVoiceOption("ar-language", "ar-language", language = "Arabic"),
        TtsVoiceOption("ar-xa-x-arz-local", "ar-xa-x-arz-local", language = "Arabic"),
        TtsVoiceOption("en-AU-language", "en-AU-language", language = "English"),
        TtsVoiceOption("en-GB-language", "en-GB-language", language = "English"),
        TtsVoiceOption("en-US-language", "en-US-language", language = "English"),
        TtsVoiceOption("en-gb-x-gba-local", "en-gb-x-gba-local", language = "English"),
    )

    @Test
    fun coerce_keepsPreferredWhenPresent() {
        assertEquals(
            "ar-language",
            TtsVoicePick.coerce("ar-language", voices, locale = Locale.US),
        )
    }

    @Test
    fun coerce_prefersSystemLocaleOverFirstCatalogEntry() {
        assertEquals(
            "en-US-language",
            TtsVoicePick.coerce("", voices, locale = Locale.US),
        )
    }

    @Test
    fun coerce_usesPluginDefaultWhenInCatalog() {
        assertEquals(
            "en-GB-language",
            TtsVoicePick.coerce(
                "missing",
                voices,
                pluginDefault = "en-GB-language",
                locale = Locale.US,
            ),
        )
    }

    @Test
    fun defaultLanguage_usesSystemWhenListed() {
        val languages = listOf("Arabic", "English", "Spanish").sorted()
        assertEquals("English", TtsVoicePick.defaultLanguage(languages, Locale.US))
    }

    @Test
    fun defaultLanguage_fallsBackToFirstSorted() {
        val languages = listOf("Arabic", "French").sorted()
        assertEquals("Arabic", TtsVoicePick.defaultLanguage(languages, Locale.US))
    }

    @Test
    fun bestForSystem_matchesLanguageTag() {
        assertEquals(
            "en-GB-language",
            TtsVoicePick.bestForSystem(voices, Locale.UK)?.id,
        )
    }
}
