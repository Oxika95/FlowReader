package com.personal.flowreader.ui.plugin

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.flowreader.plugin.api.PluginWebLogin
import com.personal.flowreader.plugin.runtime.PluginHttp
import com.personal.flowreader.plugin.runtime.WebLoginCookies
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.delay
import okhttp3.Cookie

/**
 * The site's own sign-in page in a WebView (no JavaScript bridge). Once [PluginWebLogin.doneCookie]
 * appears on an allowed host, every cookie of the allowed hosts is handed to [onSignedIn].
 * The WebView uses the plugin HTTP user agent: Cloudflare clearance cookies are bound to it.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebLoginOverlay(
    title: String,
    web: PluginWebLogin,
    allowedHosts: List<String>,
    busy: Boolean,
    error: String?,
    onSignedIn: (List<Cookie>) -> Unit,
    onDismiss: () -> Unit,
) {
    var done by remember { mutableStateOf(false) }
    val signedIn by rememberUpdatedState(onSignedIn)

    fun check() {
        if (done) return
        val cookies = WebLoginCookieStore.read(allowedHosts)
        if (cookies.isEmpty() || cookies.none { it.name == web.doneCookie && it.value.isNotBlank() }) return
        done = true
        signedIn(cookies)
    }

    LaunchedEffect(Unit) {
        while (!done) {
            delay(POLL_MS)
            check()
        }
    }

    FlowFullscreenCard(
        visible = true,
        onDismiss = onDismiss,
        dismissible = !busy,
        title = title,
        scrollable = false,
        bodyPadding = PaddingValues(FlowTokens.Space.None),
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.None),
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onDismiss, enabled = !busy)
            }
        },
    ) {
        FlowHint(
            error ?: "Sign in on the site. Flow Reader keeps the site's session cookies, never your password.",
            error = error != null,
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
        )
        AndroidView(
            factory = { ctx ->
                WebLoginCookieStore.clear(allowedHosts)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = PluginHttp.USER_AGENT
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) = check()

                        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) = check()
                    }
                    loadUrl(web.url)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

/** WebView cookies of a plugin's allowed hosts. */
internal object WebLoginCookieStore {
    fun read(allowedHosts: List<String>): List<Cookie> {
        val manager = CookieManager.getInstance()
        val now = System.currentTimeMillis()
        return WebLoginCookies.origins(allowedHosts)
            .flatMap { origin -> WebLoginCookies.parse(manager.getCookie(origin), hostOf(origin), now) }
            .distinctBy { it.name }
    }

    /** Expire every cookie the WebView holds for [allowedHosts] (fresh sign-in, sign-out). */
    fun clear(allowedHosts: List<String>) {
        val manager = CookieManager.getInstance()
        for (origin in WebLoginCookies.origins(allowedHosts)) {
            val host = hostOf(origin)
            val names = manager.getCookie(origin).orEmpty().split(';')
                .map { it.substringBefore('=').trim() }
                .filter { it.isNotEmpty() }
            for (name in names) {
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/")
                manager.setCookie(origin, "$name=; Max-Age=0; Path=/; Domain=.${host.removePrefix("www.")}")
            }
        }
        manager.flush()
    }

    private fun hostOf(origin: String): String = origin.removePrefix("https://").trimEnd('/')
}

private const val POLL_MS = 1500L
