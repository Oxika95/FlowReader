package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowLabel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.tts.EdgeVoiceCatalog
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoiceSettingsTab(
    engineKey: String,
    voiceId: String,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    speed: Float = 1f,
    pitch: Float = 1f,
    prefetchCount: Int = 1,
    clipTargetChars: Int = TtsPrefs.DEFAULT_CLIP_TARGET_CHARS,
    clipFlexChars: Int = TtsPrefs.DEFAULT_CLIP_FLEX_CHARS,
    onEngine: (String) -> Unit,
    onVoice: (String) -> Unit,
    onSpeed: (Float) -> Unit = {},
    onPitch: (Float) -> Unit = {},
    onPrefetchCount: (Int) -> Unit = {},
    onClipTargetChars: (Int) -> Unit = {},
    onClipFlexChars: (Int) -> Unit = {},
) {
    var engineOpen by remember { mutableStateOf(false) }
    var voiceOpen by remember { mutableStateOf(false) }
    var speedDragging by remember { mutableStateOf(false) }
    var pitchDragging by remember { mutableStateOf(false) }
    var prefetchDragging by remember { mutableStateOf(false) }
    var targetDragging by remember { mutableStateOf(false) }
    var flexDragging by remember { mutableStateOf(false) }
    var localSpeed by remember { mutableFloatStateOf(speed) }
    var localPitch by remember { mutableFloatStateOf(pitch) }
    var localPrefetch by remember { mutableFloatStateOf(prefetchCount.toFloat()) }
    var localTarget by remember { mutableFloatStateOf(clipTargetChars.toFloat()) }
    var localFlex by remember { mutableFloatStateOf(clipFlexChars.toFloat()) }
    val shownSpeed = if (speedDragging) localSpeed else speed
    val shownPitch = if (pitchDragging) localPitch else pitch
    val shownPrefetch = if (prefetchDragging) localPrefetch.roundToInt() else prefetchCount
    val shownTarget = if (targetDragging) {
        TtsPrefs.coerceClipTargetChars(localTarget.roundToInt())
    } else {
        clipTargetChars
    }
    val shownFlex = if (flexDragging) {
        TtsPrefs.coerceClipFlexChars(localFlex.roundToInt())
    } else {
        clipFlexChars
    }

    val engineLabel = engines.firstOrNull { it.key == engineKey }?.label ?: engineKey
    val voiceLabel = voices.firstOrNull { it.id == voiceId }?.label
        ?: voices.firstOrNull()?.label
        ?: "Default"
    val languages = remember(voices) { voices.map { it.language }.filter { it.isNotBlank() }.distinct() }
    var languageOpen by remember { mutableStateOf(false) }
    var language by remember(voiceId, languages) {
        mutableStateOf(
            voices.firstOrNull { it.id == voiceId }?.language?.takeIf { it.isNotBlank() }
                ?: EdgeVoiceCatalog.DEFAULT_LANGUAGE.takeIf { it in languages }
                ?: languages.firstOrNull().orEmpty(),
        )
    }
    val shownVoices = if (languages.isEmpty()) voices else voices.filter { it.language == language }

    FlowLabel("TTS Engine")
    ExposedDropdownMenuBox(
        expanded = engineOpen,
        onExpandedChange = { engineOpen = it },
    ) {
        OutlinedTextField(
            value = engineLabel,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(engineOpen) },
            shape = FlowTokens.Shape.Field,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = engineOpen,
            onDismissRequest = { engineOpen = false },
        ) {
            engines.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onEngine(option.key)
                        engineOpen = false
                    },
                )
            }
        }
    }

    if (languages.isNotEmpty()) {
        Spacer(Modifier.height(FlowTokens.Space.M))
        FlowLabel("Language")
        ExposedDropdownMenuBox(
            expanded = languageOpen,
            onExpandedChange = { languageOpen = it },
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(languageOpen) },
                shape = FlowTokens.Shape.Field,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(
                expanded = languageOpen,
                onDismissRequest = { languageOpen = false },
            ) {
                languages.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            language = option
                            languageOpen = false
                        },
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    FlowLabel("Voice")
    ExposedDropdownMenuBox(
        expanded = voiceOpen,
        onExpandedChange = { voiceOpen = it },
    ) {
        OutlinedTextField(
            value = if (shownVoices.any { it.id == voiceId } || shownVoices.isEmpty()) voiceLabel else "Choose a voice",
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceOpen) },
            shape = FlowTokens.Shape.Field,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = voiceOpen,
            onDismissRequest = { voiceOpen = false },
        ) {
            shownVoices.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onVoice(option.id)
                        voiceOpen = false
                    },
                )
            }
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    FlowLabel("Speed")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shownSpeed,
            onValueChange = {
                speedDragging = true
                localSpeed = (it * 20f).roundToInt() / 20f
            },
            onValueChangeFinished = {
                onSpeed(localSpeed)
                speedDragging = false
            },
            valueRange = 0.5f..2.5f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${"%.2f".format(shownSpeed).trimEnd('0').trimEnd('.')}×",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    FlowLabel("Pitch")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = shownPitch,
            onValueChange = {
                pitchDragging = true
                localPitch = (it * 20f).roundToInt() / 20f
            },
            onValueChangeFinished = {
                onPitch(localPitch)
                pitchDragging = false
            },
            valueRange = 0.5f..2f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${"%.2f".format(shownPitch).trimEnd('0').trimEnd('.')}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
            textAlign = TextAlign.End,
        )
    }

    Spacer(Modifier.height(FlowTokens.Space.S))
    var advancedOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { advancedOpen = !advancedOpen }
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Advanced",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (advancedOpen) {
                Icons.Filled.ExpandLess
            } else {
                Icons.Filled.ExpandMore
            },
            contentDescription = if (advancedOpen) "Hide advanced" else "Show advanced",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    AnimatedVisibility(visible = advancedOpen) {
        Column(modifier = Modifier.fillMaxWidth()) {
            FlowLabel("Pre-cache clips")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = if (prefetchDragging) localPrefetch else prefetchCount.toFloat(),
                    onValueChange = {
                        prefetchDragging = true
                        localPrefetch = it
                    },
                    onValueChangeFinished = {
                        onPrefetchCount(localPrefetch.roundToInt())
                        prefetchDragging = false
                    },
                    valueRange = TtsPrefs.MIN_PREFETCH.toFloat()..TtsPrefs.MAX_PREFETCH.toFloat(),
                    steps = TtsPrefs.MAX_PREFETCH - TtsPrefs.MIN_PREFETCH - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$shownPrefetch",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Clip size")
            Text(
                "Target characters per spoken clip.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = shownTarget.toFloat(),
                    onValueChange = {
                        targetDragging = true
                        localTarget = it
                    },
                    onValueChangeFinished = {
                        onClipTargetChars(TtsPrefs.coerceClipTargetChars(localTarget.roundToInt()))
                        targetDragging = false
                    },
                    valueRange = TtsPrefs.MIN_CLIP_TARGET_CHARS.toFloat()..
                        TtsPrefs.MAX_CLIP_TARGET_CHARS.toFloat(),
                    steps = (TtsPrefs.MAX_CLIP_TARGET_CHARS - TtsPrefs.MIN_CLIP_TARGET_CHARS) /
                        TtsPrefs.CLIP_TARGET_STEP_CHARS - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$shownTarget",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
                    textAlign = TextAlign.End,
                )
            }

            Spacer(Modifier.height(FlowTokens.Space.S))
            FlowLabel("Size leeway")
            Text(
                "Characters allowed above or below the target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = shownFlex.toFloat(),
                    onValueChange = {
                        flexDragging = true
                        localFlex = it
                    },
                    onValueChangeFinished = {
                        onClipFlexChars(TtsPrefs.coerceClipFlexChars(localFlex.roundToInt()))
                        flexDragging = false
                    },
                    valueRange = TtsPrefs.MIN_CLIP_FLEX_CHARS.toFloat()..
                        TtsPrefs.MAX_CLIP_FLEX_CHARS.toFloat(),
                    steps = (TtsPrefs.MAX_CLIP_FLEX_CHARS - TtsPrefs.MIN_CLIP_FLEX_CHARS) /
                        TtsPrefs.CLIP_FLEX_STEP_CHARS - 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "±$shownFlex",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.widthIn(min = FlowTokens.Comp.SliderValueWidth),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}
