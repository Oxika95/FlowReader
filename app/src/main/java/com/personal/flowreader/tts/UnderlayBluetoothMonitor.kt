package com.personal.flowreader.tts

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Parcelable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairedBtDevice(
    val address: String,
    val name: String,
)

/**
 * Tracks whether a chosen paired Bluetooth **audio** device is connected so
 * tonal underlay can stay armed (prefs Level) while silent in standby.
 *
 * Connection signal priority (inspired by [android-a2dpnostandby](https://github.com/tylerwhall/android-a2dpnostandby)):
 * 1. `BluetoothA2dp` / `BluetoothHeadset` profile state for the target MAC
 * 2. `AudioDeviceCallback` address match (A2DP/SCO routing)
 * 3. ACL connect/disconnect as a weak fallback
 */
class UnderlayBluetoothMonitor(context: Context) {
    private val app = context.applicationContext
    private val audioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothManager =
        app.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val _targetConnected = MutableStateFlow(false)
    val targetConnected: StateFlow<Boolean> = _targetConnected.asStateFlow()

    /** Addresses with A2DP or Headset profile STATE_CONNECTED. */
    private val audioProfileConnected = LinkedHashSet<String>()
    private val aclConnected = LinkedHashSet<String>()
    private val lock = Any()

    @Volatile
    private var targetAddressNorm: String = ""

    @Volatile
    private var started = false

    @Volatile
    private var a2dpProxy: BluetoothA2dp? = null

    @Volatile
    private var headsetProxy: BluetoothHeadset? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            refresh()
        }
    }

    private val connectionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            when (action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
                -> {
                    val device = intent.bluetoothDeviceExtra() ?: return
                    val addr = normalizeAddress(device.address.orEmpty())
                    if (addr.isEmpty()) return
                    val state = intent.getIntExtra(
                        BluetoothProfile.EXTRA_STATE,
                        BluetoothProfile.STATE_DISCONNECTED,
                    )
                    synchronized(lock) {
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            audioProfileConnected.add(addr)
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED ||
                            state == BluetoothProfile.STATE_DISCONNECTING
                        ) {
                            audioProfileConnected.remove(addr)
                        }
                    }
                    refresh()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                -> {
                    val device = intent.bluetoothDeviceExtra() ?: return
                    val addr = normalizeAddress(device.address.orEmpty())
                    if (addr.isEmpty()) return
                    synchronized(lock) {
                        when (action) {
                            BluetoothDevice.ACTION_ACL_CONNECTED -> aclConnected.add(addr)
                            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                                aclConnected.remove(addr)
                                // ACL drop implies audio profiles are gone too.
                                audioProfileConnected.remove(addr)
                            }
                        }
                    }
                    refresh()
                }
            }
        }
    }

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (proxy == null || !hasConnectPermission(app)) return
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = proxy as? BluetoothA2dp
                BluetoothProfile.HEADSET -> headsetProxy = proxy as? BluetoothHeadset
            }
            ingestConnectedDevices(proxy)
            refresh()
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> a2dpProxy = null
                BluetoothProfile.HEADSET -> headsetProxy = null
            }
            refresh()
        }
    }

    fun setTargetAddress(address: String) {
        targetAddressNorm = normalizeAddress(address)
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
        bindAudioProfiles()
        refresh()
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) }
        runCatching { app.unregisterReceiver(connectionReceiver) }
        unbindAudioProfiles()
        synchronized(lock) {
            audioProfileConnected.clear()
            aclConnected.clear()
        }
        _targetConnected.value = false
    }

    fun refresh() {
        val target = targetAddressNorm
        if (target.isEmpty()) {
            _targetConnected.value = false
            return
        }
        // Re-sync from live proxies before deciding (sticky connection).
        snapshotProxies()
        _targetConnected.value = isAddressConnected(target)
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

    private fun bindAudioProfiles() {
        if (!hasConnectPermission(app)) return
        val adapter = bluetoothAdapter() ?: return
        runCatching { adapter.getProfileProxy(app, profileListener, BluetoothProfile.A2DP) }
        runCatching { adapter.getProfileProxy(app, profileListener, BluetoothProfile.HEADSET) }
    }

    private fun unbindAudioProfiles() {
        val adapter = bluetoothAdapter() ?: return
        a2dpProxy?.let { runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, it) } }
        headsetProxy?.let { runCatching { adapter.closeProfileProxy(BluetoothProfile.HEADSET, it) } }
        a2dpProxy = null
        headsetProxy = null
    }

    private fun snapshotProxies() {
        a2dpProxy?.let { ingestConnectedDevices(it) }
        headsetProxy?.let { ingestConnectedDevices(it) }
    }

    private fun ingestConnectedDevices(proxy: BluetoothProfile) {
        if (!hasConnectPermission(app)) return
        val connected = runCatching { proxy.connectedDevices }.getOrNull().orEmpty()
        synchronized(lock) {
            for (device in connected) {
                val addr = normalizeAddress(device.address.orEmpty())
                if (addr.isNotEmpty()) audioProfileConnected.add(addr)
            }
        }
    }

    private fun isAddressConnected(targetNorm: String): Boolean {
        synchronized(lock) {
            if (targetNorm in audioProfileConnected) return true
        }
        if (isConnectedViaAudioDevices(targetNorm)) return true
        // ACL alone can mean a watch/HID — only use when no audio routing exists yet
        // but profile broadcasts haven't landed (cold start race).
        synchronized(lock) {
            if (targetNorm in aclConnected && isAnyBluetoothAudioOutputPresent()) {
                return true
            }
        }
        return false
    }

    private fun isConnectedViaAudioDevices(targetNorm: String): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (device in devices) {
            if (!isBluetoothAudioType(device.type)) continue
            val addr = device.address?.let { normalizeAddress(it) }.orEmpty()
            if (addr.isNotEmpty() && addr == targetNorm) return true
        }
        return false
    }

    private fun isAnyBluetoothAudioOutputPresent(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { isBluetoothAudioType(it.type) }
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

        fun normalizeAddress(address: String): String =
            address.trim().uppercase()

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

        @Suppress("DEPRECATION")
        private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? {
            return if (Build.VERSION.SDK_INT >= 33) {
                getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                getParcelableExtra<Parcelable>(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
            }
        }
    }
}
