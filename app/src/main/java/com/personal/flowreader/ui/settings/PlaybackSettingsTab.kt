package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowSegmentedRow
import com.personal.flowreader.ui.design.controls.FlowToggleRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.personal.flowreader.tts.PairedBtDevice
import com.personal.flowreader.tts.UnderlayBluetoothMonitor
import kotlinx.coroutines.launch
import com.personal.flowreader.data.TonalUnderlay
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.UnderlayMode
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSettingsTab(
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
    val context = LocalContext.current
    var gapDragging by remember { mutableStateOf(false) }
    var localGap by remember { mutableFloatStateOf(sentenceGapMs.toFloat()) }
    val shownGap = if (gapDragging) {
        TtsPrefs.coerceSentenceGapMs(localGap.roundToInt())
    } else {
        sentenceGapMs
    }
    var syncDragging by remember { mutableStateOf(false) }
    var localSync by remember { mutableFloatStateOf(highlightSyncMs.toFloat()) }
    val shownSync = if (syncDragging) {
        TtsPrefs.coerceHighlightSyncMs(localSync.roundToInt())
    } else {
        highlightSyncMs
    }
    var signalDragging by remember { mutableStateOf(false) }
    var localSignal by remember { mutableFloatStateOf(minSignal) }
    val shownSignal = if (signalDragging) localSignal else minSignal
    var gaplessOpen by remember { mutableStateOf(false) }
    var btPickerOpen by remember { mutableStateOf(false) }
    var bondedDevices by remember { mutableStateOf<List<PairedBtDevice>>(emptyList()) }
    var pendingOpenPicker by remember { mutableStateOf(false) }

    fun loadBondedAndOpen() {
        bondedDevices = underlayBondedDevices()
        btPickerOpen = true
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingOpenPicker) {
            pendingOpenPicker = false
            loadBondedAndOpen()
        } else {
            pendingOpenPicker = false
        }
    }

    fun openBtPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !UnderlayBluetoothMonitor.hasConnectPermission(context)
        ) {
            pendingOpenPicker = true
            btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        loadBondedAndOpen()
    }

    FlowToggleRow(
        title = "Auto-scroll with playback",
        subtitle = "Keep the spoken text centered until you scroll away",
        checked = autoScrollWithTts,
        onCheckedChange = onAutoScrollWithTts,
    )
    Spacer(Modifier.height(FlowTokens.Space.S))
    FlowToggleRow(
        title = "Double-tap starts playback",
        subtitle = "Unavailable on body text while selection is on — use play controls",
        checked = doubleTapPlay,
        onCheckedChange = onDoubleTapPlay,
    )
    Spacer(Modifier.height(FlowTokens.Space.S))
    FlowToggleRow(
        title = "Use mobile data when Wi-Fi is weak",
        subtitle = "Edge voices also use mobile data while Wi-Fi is weak or failing",
        checked = mobileDataFallback,
        onCheckedChange = onMobileDataFallback,
    )

    Spacer(Modifier.height(FlowTokens.Space.L))
    FlowSegmentedRow(
        title = "Tonal underlay",
        subtitle = underlaySubtitle(
            mode = underlayMode,
            address = underlayBtAddress,
            name = underlayBtName,
            active = underlayBtActive,
        ),
        options = UnderlayMode.entries,
        selected = underlayMode,
        optionLabel = { it.label },
        onSelect = { mode ->
            onUnderlayMode(mode)
            if (mode == UnderlayMode.Device && underlayBtAddress.isBlank()) openBtPicker()
        },
    )
    AnimatedVisibility(visible = underlayMode != UnderlayMode.Off) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(FlowTokens.Space.S))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Level",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatMinSignalLabel(shownSignal),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Slider(
                value = TonalUnderlay.levelIndex(shownSignal).toFloat(),
                onValueChange = {
                    val level = TonalUnderlay.LEVEL_STEPS[
                        it.roundToInt().coerceIn(TonalUnderlay.LEVEL_STEPS.indices),
                    ]
                    signalDragging = true
                    localSignal = level
                    onMinSignal(level, false)
                },
                onValueChangeFinished = {
                    onMinSignal(TonalUnderlay.coerceLevel(localSignal), true)
                    signalDragging = false
                },
                valueRange = 0f..TonalUnderlay.LEVEL_STEPS.lastIndex.toFloat(),
                steps = (TonalUnderlay.LEVEL_STEPS.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    AnimatedVisibility(visible = underlayMode == UnderlayMode.Device) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(FlowTokens.Space.S))
            ExposedDropdownMenuBox(
                expanded = btPickerOpen,
                onExpandedChange = { expanding ->
                    if (expanding) openBtPicker() else btPickerOpen = false
                },
            ) {
                OutlinedTextField(
                    value = underlayBtDeviceFieldValue(underlayBtAddress, underlayBtName),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Bluetooth device") },
                    supportingText = {
                        Text(
                            underlayBtDeviceSupportingText(
                                address = underlayBtAddress,
                                name = underlayBtName,
                                active = underlayBtActive,
                            ),
                        )
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = btPickerOpen)
                    },
                    colors = OutlinedTextFieldDefaults.colors(),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = btPickerOpen,
                    onDismissRequest = { btPickerOpen = false },
                ) {
                    if (bondedDevices.isEmpty()) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "No paired devices",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = { btPickerOpen = false },
                            enabled = false,
                        )
                    } else {
                        bondedDevices.forEach { device ->
                            DropdownMenuItem(
                                text = { Text(device.name) },
                                onClick = {
                                    onUnderlayBtDevice(device.address, device.name)
                                    btPickerOpen = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(FlowTokens.Space.L))
    SettingsFlyoutHeader(
        title = "Gapless audio",
        expanded = gaplessOpen,
        onToggle = { gaplessOpen = !gaplessOpen },
    )
    AnimatedVisibility(visible = gaplessOpen) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sentence offset",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatSentenceGapLabel(shownGap),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                when {
                    shownGap < 0 -> "Negative = equal-power crossfade between sentences"
                    shownGap > 0 -> "Positive = pause between sentences"
                    else -> "Zero = butt-join (gapless)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CenterOriginSlider(
                value = if (gapDragging) localGap else sentenceGapMs.toFloat(),
                onValueChange = {
                    gapDragging = true
                    localGap = it
                },
                onValueChangeFinished = {
                    onSentenceGapMs(TtsPrefs.coerceSentenceGapMs(localGap.roundToInt()))
                    gapDragging = false
                },
                valueRange = TtsPrefs.MIN_SENTENCE_GAP_MS.toFloat()..TtsPrefs.MAX_SENTENCE_GAP_MS.toFloat(),
                steps = (TtsPrefs.MAX_SENTENCE_GAP_MS - TtsPrefs.MIN_SENTENCE_GAP_MS) /
                    TtsPrefs.SENTENCE_GAP_STEP_MS - 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(FlowTokens.Space.L))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Word highlight sync",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatHighlightSyncLabel(shownSync),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                "Shift Edge word highlight earlier (−) or later (+)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CenterOriginSlider(
                value = if (syncDragging) localSync else highlightSyncMs.toFloat(),
                onValueChange = {
                    syncDragging = true
                    localSync = it
                },
                onValueChangeFinished = {
                    onHighlightSyncMs(TtsPrefs.coerceHighlightSyncMs(localSync.roundToInt()))
                    syncDragging = false
                },
                valueRange = TtsPrefs.MIN_HIGHLIGHT_SYNC_MS.toFloat()..
                    TtsPrefs.MAX_HIGHLIGHT_SYNC_MS.toFloat(),
                steps = (TtsPrefs.MAX_HIGHLIGHT_SYNC_MS - TtsPrefs.MIN_HIGHLIGHT_SYNC_MS) /
                    TtsPrefs.HIGHLIGHT_SYNC_STEP_MS - 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun underlaySubtitle(
    mode: UnderlayMode,
    address: String,
    name: String,
    active: Boolean,
): String {
    if (mode != UnderlayMode.Device) {
        return "Prevents audio drop out on some hardware at low tones."
    }
    if (address.isBlank()) return "Pick a Bluetooth device below"
    val label = name.ifBlank { address }
    return if (active) "Playing to $label" else "Standby until playback goes to $label"
}

private fun underlayBtDeviceFieldValue(address: String, name: String): String =
    when {
        address.isBlank() -> "None"
        name.isNotBlank() -> name
        else -> address
    }

private fun underlayBtDeviceSupportingText(
    address: String,
    name: String,
    active: Boolean,
): String = when {
    address.isBlank() -> "Underlay stays silent until a device is picked"
    active -> "Playing to this device — underlay on"
    else -> "Plays when audio goes to ${name.ifBlank { address }}"
}
