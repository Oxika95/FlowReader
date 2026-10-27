package com.personal.flowreader.ui.plugin

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.runtime.AnubisCookies
import com.personal.flowreader.plugin.runtime.PluginClearanceGate
import com.personal.flowreader.plugin.runtime.PluginHttp
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val POLL_MS = 250L
private const val TIMEOUT_MS = 60_000L

/** True only after document is ready and the challenge node is absent (not during blank load). */
private const val CHALLENGE_GONE_JS =
    "(function(){" +
        "if(!document.body||document.readyState==='loading')return false;" +
        "return !document.getElementById('anubis_challenge');" +
        "})()"

/**
 * Registers [PluginClearanceGate.handler] while composed: shows a WebView that runs the site's
 * Anubis challenge and imports clearance cookies into the plugin jar.
 */
@Composable
fun PluginClearanceHost() {
    val app = LocalContext.current.applicationContext as FlowApp
    var active by remember { mutableStateOf<Pair<PluginClearanceGate.Request, CompletableDeferred<Boolean>>?>(null) }
    val activeState = rememberUpdatedState(active)

    DisposableEffect(app) {
        PluginClearanceGate.handler = { req ->
            val deferred = CompletableDeferred<Boolean>()
            withContext(Dispatchers.Main.immediate) {
                active = req to deferred
            }
            try {
                deferred.await()
            } finally {
                withContext(Dispatchers.Main.immediate) {
                    if (activeState.value?.second === deferred) active = null
                }
            }
        }
        onDispose {
            PluginClearanceGate.handler = null
            activeState.value?.second?.complete(false)
            active = null
        }
    }

    val current = active ?: return
    val (req, deferred) = current
    ClearanceOverlay(
        url = req.url,
        allowedHosts = req.allowedHosts,
        onSuccess = {
            runCatching {
                app.pluginManager.cookieJar(req.pluginId).import(WebLoginCookieStore.read(req.allowedHosts))
            }
            deferred.complete(true)
        },
        onCancel = { deferred.complete(false) },
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ClearanceOverlay(
    url: String,
    allowedHosts: List<String>,
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
) {
    var done by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val success by rememberUpdatedState(onSuccess)
    val cancel by rememberUpdatedState(onCancel)
    // Cleared on open so a prior attempt cannot false-succeed; baseline stays empty after clear.
    val baseline = remember {
        WebLoginCookieStore.clearWhere(allowedHosts, AnubisCookies::isAnubisAuthCookie)
        emptyMap<String, String>()
    }

    suspend fun challengeGone(view: WebView?): Boolean {
        if (view == null) return false
        return suspendCancellableCoroutine { cont ->
            view.evaluateJavascript(CHALLENGE_GONE_JS) { raw ->
                if (cont.isActive) cont.resume(raw == "true")
            }
        }
    }

    suspend fun tryComplete() {
        if (done) return
        val current = AnubisCookies.authCookieMap(
            WebLoginCookieStore.read(allowedHosts).map { it.name to it.value },
        )
        if (!AnubisCookies.hasNewAuthCookie(baseline, current)) return
        if (!challengeGone(webView)) return
        done = true
        success()
    }

    LaunchedEffect(Unit) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!done) {
            if (System.currentTimeMillis() >= deadline) {
                done = true
                cancel()
                return@LaunchedEffect
            }
            delay(POLL_MS)
            tryComplete()
        }
    }

    FlowFullscreenCard(
        visible = true,
        onDismiss = {
            if (!done) {
                done = true
                cancel()
            }
        },
        dismissible = true,
        title = "Checking site access…",
        scrollable = false,
        bodyPadding = PaddingValues(FlowTokens.Space.None),
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.None),
        footer = {
            FlowActionRow {
                FlowTextAction(
                    "Cancel",
                    onClick = {
                        if (!done) {
                            done = true
                            cancel()
                        }
                    },
                )
            }
        },
    ) {
        FlowHint(
            "This site is checking that you are not a bot. It usually finishes in a few seconds.",
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
        )
        AndroidView(
            factory = { ctx ->
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = PluginHttp.USER_AGENT
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    webView = this
                    loadUrl(url)
                }
            },
            onRelease = {
                if (webView === it) webView = null
                it.destroy()
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
