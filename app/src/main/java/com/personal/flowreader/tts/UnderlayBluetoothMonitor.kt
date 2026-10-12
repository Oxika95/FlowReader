package com.personal.flowreader.tts

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedBtDevice(
    val address: String,
    val name: String,
)

/**
 * Tracks whether the app's media audio is currently routed to a chosen paired Bluetooth device,
 * so tonal underlay Auto plays only while playback actually goes there (being connected is not
 * enough: devices auto-connect by proximity, and multipoint output can switch to another device).
 *
 * Route source: API 33+ `getAudioDevicesForAttributes` for media; older APIs the routed device
 * of the playing track ([routedDevice]). Connection broadcasts, device add/remove and track
 * routing changes only trigger a re-check.
 */
class UnderlayBluetoothMonitor(
    context: Context,
    /** Routed output of the app's playing track (API < 33), or null when nothing plays. */
    private val routedDevice: () -> AudioDeviceInfo? = { null },
) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager =
        app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _targetActive = MutableStateFlow(false)
    /** True while media audio is routed to the target device. */
    val targetActive: StateFlow<Boolean> = _targetActive.asStateFlow()

    @Volatile
    private var targetAddress: String = ""

    @Volatile
    private var started = false

    private val mediaAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    /** Attach to the app's AudioTracks so output switches re-check at once. */
    val routingListener = AudioRouting.OnRoutingChangedListener { refresh() }

    fun setTargetAddress(address: String) {
        targetAddress = UnderlayRoute.normalizeAddress(address)
        refresh()
    }

    fun start() {
        if (started) return
        started = true
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(
            app,
            connectionReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { app.unregisterReceiver(connectionReceiver) }
        _targetActive.value = false
    }

    fun refresh() {
        _targetActive.value = UnderlayRoute.routedToTarget(currentOutputs(), targetAddress)
    }

    private fun currentOutputs(): List<RouteOutput> {
        val devices = if (Build.VERSION.SDK_INT >= 33) {
            runCatching { audioManager.getAudioDevicesForAttributes(mediaAttributes) }.getOrNull().orEmpty()
        } else {
            listOfNotNull(runCatching { routedDevice() }.getOrNull())
        }
        return devices.map { RouteOutput(isBluetoothAudioType(it.type), it.address.orEmpty()) }
    }

    /** Bonded (paired) devices for the settings picker. Empty if permission denied. */
    fun bondedDevices(): List<PairedBtDevice> {
        if (!hasConnectPermission(app)) return emptyList()
        val adapter = bluetoothAdapter() ?: return emptyList()
        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        return bonded.mapNotNull { device ->
            val address = device.address?.trim().orEmpty()
            if (address.isEmpty()) return@mapNotNull null
            val name = runCatching { device.name }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: address
            PairedBtDevice(address = address, name = name)
        }.sortedBy { it.name.lowercase() }
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        bluetoothManager?.adapter ?: @Suppress("DEPRECATION") BluetoothAdapter.getDefaultAdapter()

    companion object {
        fun hasConnectPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
        }

        private fun isBluetoothAudioType(type: Int): Boolean = when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> true
            else -> {
                if (Build.VERSION.SDK_INT >= 31) {
                    type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                        type == AudioDeviceInfo.TYPE_BLE_SPEAKER
                } else {
                    false
                }
            }
        }
    }
}
