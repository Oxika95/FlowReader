package com.personal.flowreader.tts

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceGenderTest {
    @Test
    fun trailingDashFAndM() {
        assertEquals("female", VoiceGender.infer("expr-voice-2-f"))
        assertEquals("male", VoiceGender.infer("expr-voice-2-m"))
        assertEquals("female", VoiceGender.infer("expr-voice-2-f", "expr-voice-2-f (expr)"))
        assertEquals("male", VoiceGender.infer("pkg.expr-voice-3-m", "expr-voice-3-m (expr)"))
        assertEquals("female", VoiceGender.infer("voice_f", "voice_f"))
        assertEquals("male", VoiceGender.infer("voice_m"))
    }

    @Test
    fun fDigitMDigitTokens() {
        assertEquals("female", VoiceGender.infer("korokoro_f3"))
        assertEquals("male", VoiceGender.infer("korokoro_m5"))
        assertEquals("female", VoiceGender.infer("f1"))
        assertEquals("male", VoiceGender.infer("pack-m2-en"))
    }

    @Test
    fun wholeWords() {
        assertEquals("female", VoiceGender.infer("com.foo.Female"))
        assertEquals("male", VoiceGender.infer("en-US-MaleNeural", "Male (en-US)"))
        assertEquals("female", VoiceGender.infer("voice-woman-1"))
        assertEquals("male", VoiceGender.infer("voice-man-1"))
    }

    @Test
    fun negatives() {
        assertEquals("", VoiceGender.infer("default"))
        assertEquals("", VoiceGender.infer("platform"))
        assertEquals("", VoiceGender.infer("manager"))
        assertEquals("", VoiceGender.infer("en-US-AriaNeural"))
        assertEquals("", VoiceGender.infer("en-US-AndrewNeural"))
        assertEquals("", VoiceGender.infer("human-voice"))
    }

    @Test
    fun preferExplicitOverInfer() {
        assertEquals("male", VoiceGender.resolve("Male", "expr-voice-2-f"))
        assertEquals("female", VoiceGender.resolve("female", "expr-voice-2-m"))
        assertEquals("female", VoiceGender.resolve("", "expr-voice-2-f"))
        assertEquals("male", VoiceGender.resolve(null, "expr-voice-2-m"))
    }
}
