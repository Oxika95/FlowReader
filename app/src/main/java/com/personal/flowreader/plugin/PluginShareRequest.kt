package com.personal.flowreader.plugin

/** A shared URL routed to [pluginId]'s library tab (opens the add/resolve flow). */
data class PluginShareRequest(val pluginId: String, val url: String)
