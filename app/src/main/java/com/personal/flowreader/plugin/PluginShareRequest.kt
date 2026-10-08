package com.personal.flowreader.plugin

/**
 * Work for [pluginId]'s library tab: a shared [url] (opens the add/resolve flow) or a stored
 * story [bookId] (opens its media card, e.g. from a new-chapter notification).
 */
data class PluginShareRequest(val pluginId: String, val url: String = "", val bookId: String? = null)
