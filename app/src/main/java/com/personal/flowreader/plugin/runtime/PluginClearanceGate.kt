package com.personal.flowreader.plugin.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Bridges `PluginHttp` Anubis detection to a UI WebView solver. When [handler] is null
 * (no Compose host, e.g. WorkManager), [await] returns false immediately.
 */
object PluginClearanceGate {
    data class Request(
        val pluginId: String,
        val url: String,
        val allowedHosts: List<String>,
    )

    @Volatile
    var handler: (suspend (Request) -> Boolean)? = null

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Boolean>>()

    suspend fun await(pluginId: String, url: String, allowedHosts: List<String>): Boolean {
        val h = handler ?: return false
        val (deferred, leader) = mutex.withLock {
            inFlight[pluginId]?.let { return@withLock it to false }
            CompletableDeferred<Boolean>().also { inFlight[pluginId] = it } to true
        }
        if (leader) {
            try {
                deferred.complete(h(Request(pluginId, url, allowedHosts)))
            } catch (_: Throwable) {
                deferred.complete(false)
            } finally {
                mutex.withLock { inFlight.remove(pluginId) }
            }
        }
        return deferred.await()
    }
}
