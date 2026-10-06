package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
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
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.ui.theme.FlowTokens
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSettingsTab(
    doubleTapPlay: Boolean = false,
    autoScrollWithTts: Boolean = false,
    minSignal: Float = TtsPrefs.DEFAULT_MIN_SIGNAL,
    underlayBtAddress: String = "",
    underlayBtName: String = "",
    underlayBtConnected: Boolean = false,
    sentenceGapMs: Int = TtsPrefs.DEFAULT_SENTENCE_GAP_MS,
    highlightSyncMs: Int = TtsPrefs.DEFAULT_HIGHLIGHT_SYNC_MS,
    onDoubleTapPlay: (Boolean) -> Unit = {},
    onAutoScrollWithTts: (Boolean) -> Unit = {},
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
    val underlayOn = TtsPrefs.isUnderlayEnabled(shownSignal)
    var lastUnderlayLevel by remember {
        mutableFloatStateOf(
            if (TtsPrefs.isUnderlayEnabled(minSignal)) minSignal else DEFAULT_UNDERLAY_ON_LEVEL,
        )
    }
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

    Spacer(Modifier.height(FlowTokens.Space.L))
    FlowToggleRow(
        title = "Tonal underlay",
        subtitle = underlaySubtitle(
            underlayOn = underlayOn,
            address = underlayBtAddress,
            name = underlayBtName,
            connected = underlayBtConnected,
        ),
        checked = underlayOn,
        onCheckedChange = { enabled ->
            if (enabled) {
                val level = TtsPrefs.coerceMinSignal(lastUnderlayLevel).let {
                    if (TtsPrefs.isUnderlayEnabled(it)) it else DEFAULT_UNDERLAY_ON_LEVEL
                }
                localSignal = level
                onMinSignal(level, true)
            } else {
                if (TtsPrefs.isUnderlayEnabled(shownSignal)) {
                    lastUnderlayLevel = shownSignal
                }
                localSignal = TtsPrefs.DEFAULT_MIN_SIGNAL
                onMinSignal(TtsPrefs.DEFAULT_MIN_SIGNAL, true)
            }
            signalDragging = false
        },
    )
    AnimatedVisibility(visible = underlayOn) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                value = underlayLevelSliderIndex(shownSignal).toFloat(),
                onValueChange = {
                    val level = UNDERLAY_LEVEL_STEPS[
                        it.roundToInt().coerceIn(UNDERLAY_LEVEL_STEPS.indices),
                    ]
                    signalDragging = true
                    localSignal = level
                    lastUnderlayLevel = level
                    onMinSignal(level, false)
                },
                onValueChangeFinished = {
                    val level = TtsPrefs.coerceMinSignal(localSignal)
                    lastUnderlayLevel = level
                    onMinSignal(level, true)
                    signalDragging = false
                },
                valueRange = 0f..UNDERLAY_LEVEL_STEPS.lastIndex.toFloat(),
                steps = (UNDERLAY_LEVEL_STEPS.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )

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
                                connected = underlayBtConnected,
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
                    DropdownMenuItem(
                        text = { Text("None (always on)") },
                        onClick = {
                            onUnderlayBtDevice("", "")
                            btPickerOpen = false
                        },
                    )
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
    underlayOn: Boolean,
    address: String,
    name: String,
    connected: Boolean,
): String {
    if (!underlayOn) {
        return "Prevents audio drop out on some hardware at low tones."
    }
    if (address.isBlank()) {
        return "Prevents audio drop out on some hardware at low tones."
    }
    val label = name.ifBlank { address }
    return if (connected) {
        "Active on $label"
    } else {
        "Standby — waiting for $label"
    }
}

private fun underlayBtDeviceFieldValue(address: String, name: String): String =
    when {
        address.isBlank() -> "None (always on)"
        name.isNotBlank() -> name
        else -> address
    }

private fun underlayBtDeviceSupportingText(
    address: String,
    name: String,
    connected: Boolean,
): String = when {
    address.isBlank() -> "Underlay plays whenever playback is on"
    connected -> "Connected — underlay active"
    else -> "Standby until ${name.ifBlank { address }} connects"
}

/** Enabled underlay amplitudes only (`0` / Off is handled by the toggle). */
private val UNDERLAY_LEVEL_STEPS: FloatArray =
    TtsPrefs.TONAL_UNDERLAY_STEPS.filter { it < 0f }.toFloatArray()

private const val DEFAULT_UNDERLAY_ON_LEVEL = -60f

private fun underlayLevelSliderIndex(level: Float): Int {
    var best = 0
    var bestDist = Float.MAX_VALUE
    for (i in UNDERLAY_LEVEL_STEPS.indices) {
        val dist = kotlin.math.abs(UNDERLAY_LEVEL_STEPS[i] - level)
        if (dist < bestDist) {
            best = i
            bestDist = dist
        }
    }
    return best
}
