package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.personal.flowreader.tts.PairedBtDevice
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.ui.theme.FlowTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioSettingsTab(
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float = 1f,
    pitch: Float = 1f,
    prefetchCount: Int = 1,
    clipTargetChars: Int = TtsPrefs.DEFAULT_CLIP_TARGET_CHARS,
    clipFlexChars: Int = TtsPrefs.DEFAULT_CLIP_FLEX_CHARS,
    doubleTapPlay: Boolean = false,
    mobileDataFallback: Boolean = true,
    autoScrollWithTts: Boolean = false,
    minSignal: Float = TtsPrefs.DEFAULT_MIN_SIGNAL,
    underlayBtAddress: String = "",
    underlayBtName: String = "",
    underlayBtConnected: Boolean = false,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onClipTargetChars: (Int) -> Unit = {},
    onClipFlexChars: (Int) -> Unit = {},
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onMobileDataFallback: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
    onMinSignal: (Float, Boolean) -> Unit = { _, _ -> },
    onUnderlayBtDevice: (String, String) -> Unit = { _, _ -> },
    underlayBondedDevices: () -> List<PairedBtDevice> = { emptyList() },
    onSentenceGapMs: (Int) -> Unit = {},
    onHighlightSyncMs: (Int) -> Unit = {},
) {
    var audioTab by remember { mutableIntStateOf(0) }
    val audioTabs = listOf("Voice", "Playback")

    FlowTabBar(tabs = flowTextTabs(audioTabs, audioTab) { audioTab = it }, level = FlowTabLevel.Secondary, inset = FlowTokens.Space.None)

    Spacer(Modifier.height(FlowTokens.Space.M))
    when (audioTab) {
        0 -> VoiceSettingsTab(
            engineKey = engineKey,
            voiceId = voiceId,
            engines = engines,
            voices = voices,
            speed = speed,
            pitch = pitch,
            prefetchCount = prefetchCount,
            clipTargetChars = clipTargetChars,
            clipFlexChars = clipFlexChars,
            onEngine = onEngine,
            onVoice = onVoice,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onPrefetchCount = onPrefetchCount,
            onClipTargetChars = onClipTargetChars,
            onClipFlexChars = onClipFlexChars,
        )
        else -> PlaybackSettingsTab(
            doubleTapPlay = doubleTapPlay,
            mobileDataFallback = mobileDataFallback,
            autoScrollWithTts = autoScrollWithTts,
            minSignal = minSignal,
            underlayBtAddress = underlayBtAddress,
            underlayBtName = underlayBtName,
            underlayBtConnected = underlayBtConnected,
            sentenceGapMs = sentenceGapMs,
            highlightSyncMs = highlightSyncMs,
            onDoubleTapPlay = onDoubleTapPlay,
            onMobileDataFallback = onMobileDataFallback,
            onAutoScrollWithTts = onAutoScrollWithTts,
            onMinSignal = onMinSignal,
            onUnderlayBtDevice = onUnderlayBtDevice,
            underlayBondedDevices = underlayBondedDevices,
            onSentenceGapMs = onSentenceGapMs,
            onHighlightSyncMs = onHighlightSyncMs,
        )
    }
}
