package com.personal.flowreader.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Owner of `OkHttpClient` construction for plain HTTP. Every client comes from one shared root, so
 * they share a connection pool and dispatcher, and every client has a [callTimeout] (connect and read
 * timeouts alone don't stop a server that keeps sending slowly).
 *
 * New HTTP code calls [create] instead of `OkHttpClient.Builder()`; `rg 'OkHttpClient\.Builder'` should
 * match only this file. Clients that bind to a network or open sockets by hand (`PluginWs`) are not
 * built here.
 */
object HttpClients {
    private val root = OkHttpClient()

    // 20/30/60 s are the values PluginHttp, RepoManager, WebPageIngest and PluginBookStore used before
    // the factory; BookCover passes its own shorter ones.
    const val CONNECT_SECONDS = 20L
    const val READ_SECONDS = 30L
    const val CALL_SECONDS = 60L

    fun create(
        connectSeconds: Long = CONNECT_SECONDS,
        readSeconds: Long = READ_SECONDS,
        callSeconds: Long = CALL_SECONDS,
        configure: OkHttpClient.Builder.() -> Unit = {},
    ): OkHttpClient = root.newBuilder()
        .connectTimeout(connectSeconds, TimeUnit.SECONDS)
        .readTimeout(readSeconds, TimeUnit.SECONDS)
        .callTimeout(callSeconds, TimeUnit.SECONDS)
        .apply(configure)
        .build()
}
