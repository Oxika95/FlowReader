package com.personal.flowreader.tts

/** One output device media audio is routed to right now. */
data class RouteOutput(val isBluetooth: Boolean, val address: String)

object UnderlayRoute {
    fun normalizeAddress(address: String): String = address.trim().uppercase()

    /** True when [targetMac] is among the Bluetooth outputs media audio currently plays to. */
    fun routedToTarget(outputs: List<RouteOutput>, targetMac: String): Boolean {
        val target = normalizeAddress(targetMac)
        if (target.isEmpty()) return false
        return outputs.any { it.isBluetooth && normalizeAddress(it.address) == target }
    }
}
