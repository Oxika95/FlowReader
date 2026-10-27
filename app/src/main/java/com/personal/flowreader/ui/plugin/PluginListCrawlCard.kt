package com.personal.flowreader.ui.plugin

import android.annotation.SuppressLint
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
import com.personal.flowreader.plugin.api.PluginManifest
import com.personal.flowreader.plugin.api.PluginPage
import com.personal.flowreader.plugin.api.PluginSitePage
import com.personal.flowreader.plugin.api.PluginWork
import com.personal.flowreader.plugin.runtime.PluginHttp
import com.personal.flowreader.plugin.store.SyncMode
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import kotlin.coroutines.resume

/** One list URL to crawl (page 1+ while the plugin reports hasMore). */
data class ListCrawlTarget(
    val listId: String,
    val title: String,
    val baseUrl: String,
)

/** Account Sync/Replace/Site Done WebView crawl request. */
data class PluginListCrawlUi(
    val targets: List<ListCrawlTarget>,
    /** Overwrite = Replace / Site Done; Merge = Replace→Merge; null = Sync now (three-way). */
    val mode: SyncMode?,
    val seedCookies: List<Cookie>,
)

/**
 * Ready when Anubis is gone and either fiction rows or an intentional empty-list message exist.
 * Returning true on Anubis-gone alone captured a logged-in shell with no list markup.
 */
private const val LIST_READY_JS =
    "(function(){" +
        "if(!document.body||document.readyState==='loading')return false;" +
        "if(document.getElementById('anubis_challenge'))return false;" +
        "if(document.querySelector('div.fiction-list-item'))return true;" +
        "var t=document.body.innerText||'';" +
        "return /not following any|don.?t have any|no fictions|haven.?t (favorited|bookmarked|added)|" +
        "nothing (here|to show)|your list is empty/i.test(t);" +
        "})()"

/** Base64 UTF-8 outerHTML — avoids evaluateJavascript JSON corruption on large pages. */
private const val OUTER_HTML_B64_JS =
    "(function(){" +
        "try{" +
        "var h=document.documentElement?document.documentElement.outerHTML:'';" +
        "return btoa(unescape(encodeURIComponent(h)));" +
        "}catch(e){return '';}" +
        "})()"

private const val POLL_MS = 250L
private const val PAGE_TIMEOUT_MS = 60_000L
private const val MAX_PAGES = 100

/** `https://host/path` or `https://host/path?page=N`. */
internal fun listCrawlPageUrl(baseUrl: String, page: Int): String {
    val base = baseUrl.trim().substringBefore('#').substringBefore('?').trimEnd('/')
    return if (page <= 1) base else "$base?page=$page"
}

internal fun PluginManifest.toListCrawlTargets(listIds: Collection<String>? = null): List<ListCrawlTarget> {
    val filter = listIds?.toSet()
    return listCrawlPages().mapNotNull { page ->
        val id = page.listId?.trim().orEmpty()
        if (id.isEmpty()) return@mapNotNull null
        if (filter != null && id !in filter) return@mapNotNull null
        ListCrawlTarget(listId = id, title = page.title, baseUrl = page.url)
    }
}

/**
 * Loads each [PluginListCrawlUi.targets] URL in a WebView, waits for Anubis to clear, captures
 * HTML, and asks the plugin to parse it (paging while hasMore).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PluginListCrawlCard(
    crawl: PluginListCrawlUi?,
    pluginName: String,
    allowedHosts: List<String>,
    onParse: suspend (listId: String, html: String, pageUrl: String) -> PluginPage,
    onFinished: (Map<String, List<PluginWork>>) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    if (crawl == null || crawl.targets.isEmpty()) return
    var webView by remember { mutableStateOf<WebView?>(null) }
    var status by remember { mutableStateOf("Opening…") }
    var cancelled by remember { mutableStateOf(false) }
    val loadSignalRef = remember { AtomicReference(CompletableDeferred<Unit>()) }
    val parse by rememberUpdatedState(onParse)
    val finished by rememberUpdatedState(onFinished)
    val error by rememberUpdatedState(onError)
    val cancel by rememberUpdatedState(onCancel)

    fun requestCancel() {
        if (cancelled) return
        cancelled = true
        cancel()
    }

    LaunchedEffect(crawl) {
        cancelled = false
        val collected = LinkedHashMap<String, List<PluginWork>>()
        try {
            val deadlineBoot = System.currentTimeMillis() + 5_000L
            while (webView == null && !cancelled && System.currentTimeMillis() < deadlineBoot) {
                delay(50)
            }
            val view = webView ?: run {
                error("Could not open the site browser")
                return@LaunchedEffect
            }
            for (target in crawl.targets) {
                if (cancelled) return@LaunchedEffect
                val items = ArrayList<PluginWork>()
                var page = 1
                while (page <= MAX_PAGES) {
                    if (cancelled) return@LaunchedEffect
                    val url = listCrawlPageUrl(target.baseUrl, page)
                    status = "Importing ${target.title} (page $page)…"
                    val signal = CompletableDeferred<Unit>()
                    loadSignalRef.set(signal)
                    withContext(Dispatchers.Main.immediate) { view.loadUrl(url) }
                    val loaded = awaitListReady(view, signal) { cancelled }
                    if (!loaded) {
                        if (!cancelled) error("Timed out waiting for ${target.title} to finish loading")
                        return@LaunchedEffect
                    }
                    if (cancelled) return@LaunchedEffect
                    val finalUrl = withContext(Dispatchers.Main.immediate) { view.url ?: url }
                    val html = captureHtmlBase64(view)
                    if (html.isBlank()) {
                        error("Empty page for ${target.title}")
                        return@LaunchedEffect
                    }
                    val result = try {
                        parse(target.listId, html, finalUrl)
                    } catch (t: Throwable) {
                        error(t.message ?: "Could not read ${target.title}")
                        return@LaunchedEffect
                    }
                    items += result.items
                    if (!result.hasMore || result.items.isEmpty()) break
                    page++
                }
                collected[target.listId] = items
            }
            if (!cancelled) finished(collected)
        } catch (t: Throwable) {
            if (!cancelled) error(t.message ?: "Could not import lists from $pluginName")
        }
    }

    FlowFullscreenCard(
        visible = true,
        onDismiss = { requestCancel() },
        dismissible = true,
        title = "Importing from $pluginName",
        scrollable = false,
        bodyPadding = PaddingValues(FlowTokens.Space.None),
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.None),
        footer = {
            FlowActionRow {
                FlowTextAction("Cancel", onClick = { requestCancel() })
            }
        },
    ) {
        FlowHint(
            status,
            modifier = Modifier.padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
        )
        AndroidView(
            factory = { ctx ->
                WebLoginCookieStore.seed(allowedHosts, crawl.seedCookies)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = PluginHttp.USER_AGENT
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            loadSignalRef.get().complete(Unit)
                        }
                    }
                    webView = this
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

private suspend fun awaitListReady(
    view: WebView,
    loaded: CompletableDeferred<Unit>,
    isCancelled: () -> Boolean,
): Boolean {
    val deadline = System.currentTimeMillis() + PAGE_TIMEOUT_MS
    loaded.await()
    while (System.currentTimeMillis() < deadline) {
        if (isCancelled()) return false
        if (listReady(view)) return true
        delay(POLL_MS)
    }
    return listReady(view)
}

private suspend fun listReady(view: WebView): Boolean =
    suspendCancellableCoroutine { cont ->
        view.evaluateJavascript(LIST_READY_JS) { raw ->
            if (cont.isActive) cont.resume(raw == "true")
        }
    }

private suspend fun captureHtmlBase64(view: WebView): String =
    suspendCancellableCoroutine { cont ->
        view.evaluateJavascript(OUTER_HTML_B64_JS) { raw ->
            if (!cont.isActive) return@evaluateJavascript
            cont.resume(decodeJsBase64Html(raw))
        }
    }

/** Unwrap WebView `evaluateJavascript` string results (`"…"` with escapes). */
internal fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return runCatching {
        org.json.JSONTokener(raw).nextValue() as? String ?: raw.trim().removeSurrounding("\"")
    }.getOrDefault(raw.trim().removeSurrounding("\""))
}

/** Decode base64 outerHTML from [OUTER_HTML_B64_JS]. */
internal fun decodeJsBase64Html(raw: String?): String {
    val b64 = decodeJsString(raw).trim()
    if (b64.isEmpty()) return ""
    return runCatching {
        String(Base64.getDecoder().decode(b64), Charsets.UTF_8)
    }.getOrDefault("")
}

internal fun PluginSitePage.toCrawlTarget(): ListCrawlTarget? {
    val id = listId?.trim().orEmpty()
    if (id.isEmpty()) return null
    return ListCrawlTarget(listId = id, title = title, baseUrl = url)
}
