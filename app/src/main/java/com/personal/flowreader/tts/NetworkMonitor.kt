package com.personal.flowreader.tts

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Dns
import okhttp3.OkHttpClient

/**
 * Tracks the default, Wi-Fi and cellular networks, reads signal strength on demand, and hands out
 * OkHttp clients whose sockets and DNS are bound to one network. Holding a cellular request keeps
 * mobile data up while Wi-Fi is the default network.
 */
class NetworkMonitor(context: Context, private val base: OkHttpClient) {
    private val app = context.applicationContext
    private val cm = app.getSystemService(ConnectivityManager::class.java)
    private val wifiManager = app.getSystemService(WifiManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)

    @Volatile private var defaultNet: Network? = null
    @Volatile private var defaultCaps: NetworkCapabilities? = null
    @Volatile private var wifiNet: Network? = null
    @Volatile private var cellNet: Network? = null
    private val clients = ConcurrentHashMap<Network, OkHttpClient>()

    private val _changes = MutableStateFlow(0)
    /** Bumped on every network callback. */
    val changes: StateFlow<Int> = _changes

    private var started = false
    private var cellRequest: ConnectivityManager.NetworkCallback? = null

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            defaultNet = network
            bump()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            defaultNet = network
            defaultCaps = caps
            bump()
        }

        override fun onLost(network: Network) {
            if (defaultNet == network) {
                defaultNet = null
                defaultCaps = null
            }
            bump()
        }
    }

    private val wifiCallback = trackingCallback({ wifiNet }, { wifiNet = it })
    private val cellCallback = trackingCallback({ cellNet }, { cellNet = it })

    @Synchronized
    fun start() {
        if (started || cm == null) return
        started = true
        runCatching { cm.registerDefaultNetworkCallback(defaultCallback) }
        runCatching { cm.registerNetworkCallback(internetRequest(NetworkCapabilities.TRANSPORT_WIFI), wifiCallback) }
        runCatching {
            cm.registerNetworkCallback(internetRequest(NetworkCapabilities.TRANSPORT_CELLULAR), cellCallback)
        }
    }

    @Synchronized
    fun stop() {
        if (!started || cm == null) return
        started = false
        setCellularWanted(false)
        runCatching { cm.unregisterNetworkCallback(defaultCallback) }
        runCatching { cm.unregisterNetworkCallback(wifiCallback) }
        runCatching { cm.unregisterNetworkCallback(cellCallback) }
        defaultNet = null
        defaultCaps = null
        wifiNet = null
        cellNet = null
        clients.clear()
    }

    /** Request (or release) cellular so it can carry traffic while Wi-Fi is the default. */
    @Synchronized
    fun setCellularWanted(want: Boolean) {
        if (cm == null) return
        val held = cellRequest
        if (want && held == null && started) {
            val cb = object : ConnectivityManager.NetworkCallback() {}
            val ok = runCatching {
                cm.requestNetwork(internetRequest(NetworkCapabilities.TRANSPORT_CELLULAR), cb)
            }.isSuccess
            if (ok) {
                cellRequest = cb
                SynthDebugLog.append("net cellular request on")
            } else {
                SynthDebugLog.append("net cellular request refused")
            }
        } else if (!want && held != null) {
            cellRequest = null
            runCatching { cm.unregisterNetworkCallback(held) }
            SynthDebugLog.append("net cellular request off")
        }
    }

    val cellularHeld: Boolean get() = cellRequest != null

    fun snapshot(): NetSnapshot {
        val net = defaultNet
        val caps = net?.let { runCatching { cm?.getNetworkCapabilities(it) }.getOrNull() } ?: defaultCaps
        val transport = when {
            net == null || caps == null -> NetTransport.NONE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetTransport.CELL
            else -> NetTransport.OTHER
        }
        return NetSnapshot(
            transport = transport,
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
            wifiUp = wifiNet != null,
            cellUp = cellNet != null,
            wifiRssi = wifiRssi(),
            cellLevel = cellLevel(),
        )
    }

    /** Client bound to [lane]'s network; the shared default client when that network is down. */
    fun clientFor(lane: NetLane): OkHttpClient {
        val net = when (lane) {
            NetLane.DEFAULT -> null
            NetLane.WIFI -> wifiNet
            NetLane.CELL -> cellNet
        } ?: return base
        return clients.getOrPut(net) {
            base.newBuilder()
                .socketFactory(net.socketFactory)
                .dns(
                    object : Dns {
                        override fun lookup(hostname: String): List<InetAddress> =
                            net.getAllByName(hostname).toList()
                    },
                )
                .build()
        }
    }

    private fun wifiRssi(): Int? {
        val net = wifiNet ?: return null
        @Suppress("DEPRECATION")
        val polled = runCatching { wifiManager?.connectionInfo?.rssi }.getOrNull()
        if (polled != null && polled in MIN_RSSI..-1) return polled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val s = runCatching { cm?.getNetworkCapabilities(net)?.signalStrength }.getOrNull()
            if (s != null && s != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return s
        }
        return null
    }

    private fun cellLevel(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching { telephony?.signalStrength?.level }.getOrNull()
    }

    private fun trackingCallback(
        current: () -> Network?,
        set: (Network?) -> Unit,
    ) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            set(network)
            bump()
        }

        override fun onLost(network: Network) {
            if (current() == network) set(null)
            clients.remove(network)
            bump()
        }
    }

    private fun bump() = _changes.update { it + 1 }

    private fun internetRequest(transport: Int): NetworkRequest = NetworkRequest.Builder()
        .addTransportType(transport)
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    private companion object {
        /** WifiInfo reports -127 when RSSI is unknown. */
        const val MIN_RSSI = -126
    }
}
