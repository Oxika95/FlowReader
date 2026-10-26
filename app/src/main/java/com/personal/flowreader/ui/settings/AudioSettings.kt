package com.personal.flowreader.ui.settings

import com.personal.flowreader.R

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
import com.personal.flowreader.data.TonalUnderlay
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.data.UnderlayMode
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
    showClipSettings: Boolean = false,
    showNetworkLanes: Boolean = false,
    doubleTapPlay: Boolean = false,
    mobileDataFallback: Boolean = true,
    autoScrollWithTts: Boolean = false,
    underlayMode: UnderlayMode = UnderlayMode.Off,
    minSignal: Float = TonalUnderlay.DEFAULT_LEVEL,
    underlayBtAddress: String = "",
    underlayBtName: String = "",
    underlayBtActive: Boolean = false,
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
    onUnderlayMode: (UnderlayMode) -> Unit = {},
    onMinSignal: (Float, Boolean) -> Unit = { _, _ -> },
    onUnderlayBtDevice: (String, String) -> Unit = { _, _ -> },
    underlayBondedDevices: () -> List<PairedBtDevice> = { emptyList() },
    onSentenceGapMs: (Int) -> Unit = {},
    onHighlightSyncMs: (Int) -> Unit = {},
) {
    var audioTab by remember { mutableIntStateOf(0) }
    val audioTabs = listOf(R.drawable.ic_settings_voice to "Voice", R.drawable.ic_settings_playback to "Playback")

    SettingsSubTabBar(audioTabs, audioTab) { audioTab = it }

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
            showClipSettings = showClipSettings,
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
            showNetworkLanes = showNetworkLanes,
            autoScrollWithTts = autoScrollWithTts,
            underlayMode = underlayMode,
            minSignal = minSignal,
            underlayBtAddress = underlayBtAddress,
            underlayBtName = underlayBtName,
            underlayBtActive = underlayBtActive,
            sentenceGapMs = sentenceGapMs,
            highlightSyncMs = highlightSyncMs,
            onDoubleTapPlay = onDoubleTapPlay,
            onMobileDataFallback = onMobileDataFallback,
            onAutoScrollWithTts = onAutoScrollWithTts,
            onUnderlayMode = onUnderlayMode,
            onMinSignal = onMinSignal,
            onUnderlayBtDevice = onUnderlayBtDevice,
            underlayBondedDevices = underlayBondedDevices,
            onSentenceGapMs = onSentenceGapMs,
            onHighlightSyncMs = onHighlightSyncMs,
        )
    }
}
