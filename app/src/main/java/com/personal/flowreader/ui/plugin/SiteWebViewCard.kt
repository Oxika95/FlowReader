package com.personal.flowreader.ui.plugin

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.flowreader.plugin.runtime.PluginHttp
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowTokens
import okhttp3.Cookie

/**
 * In-app browser for a plugin [sitePages](com.personal.flowreader.plugin.api.PluginManifest.sitePages)
 * URL (clear bot checks, refresh the session). Done imports WebView cookies into the plugin jar;
 * Cancel leaves the jar unchanged.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun SiteWebViewCard(
    visible: Boolean,
    title: String,
    startUrl: String,
    allowedHosts: List<String>,
    seedCookies: List<Cookie>,
    busy: Boolean,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible || startUrl.isBlank()) return
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
                FlowTextAction("Done", onDone, enabled = !busy)
            }
        },
    ) {
        FlowHint(
            "Wait for the page to finish (including any bot check), then tap Done to update the app session.",
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
        )
        AndroidView(
            factory = { ctx ->
                WebLoginCookieStore.seed(allowedHosts, seedCookies)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = PluginHttp.USER_AGENT
                    webViewClient = WebViewClient()
                    loadUrl(startUrl)
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
