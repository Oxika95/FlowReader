package com.personal.flowreader.ui.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.flowreader.share.CssList
import com.personal.flowreader.share.PathNode
import com.personal.flowreader.share.PickedSelector
import com.personal.flowreader.share.SelectorBuilder
import com.personal.flowreader.share.WebPageIngest
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.tabs.FlowTab
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Parser fields the picker reads and writes (raw editor text). */
data class PickerFields(
    val title: String = "",
    val body: String = "",
    val cover: String = "",
    val prev: String = "",
    val next: String = "",
    val remove: String = "",
) {
    operator fun get(field: PickField): String = when (field) {
        PickField.Title -> title
        PickField.Cover -> cover
        PickField.Body -> body
        PickField.Previous -> prev
        PickField.Next -> next
        PickField.Remove -> remove
    }

    fun with(field: PickField, value: String): PickerFields = when (field) {
        PickField.Title -> copy(title = value)
        PickField.Cover -> copy(cover = value)
        PickField.Body -> copy(body = value)
        PickField.Previous -> copy(prev = value)
        PickField.Next -> copy(next = value)
        PickField.Remove -> copy(remove = value)
    }

    /**
     * Applies a tapped element's [selector]. Remove collects entries; [replacing] is the entry the
     * same pick wrote before Wider/Narrower, so adjusting a pick doesn't pile up entries.
     */
    fun withPick(field: PickField, selector: String, replacing: String? = null): PickerFields {
        if (field != PickField.Remove) return with(field, selector)
        val entries = CssList.split(remove).filterNot { it == replacing }
        return with(field, CssList.join((entries + selector).distinct()))
    }
}

enum class PickField(val label: String, val jsKey: String) {
    Title("Title", "title"),
    Cover("Cover", "cover"),
    Body("Body", "body"),
    Previous("Prev", "prev"),
    Next("Next", "next"),
    Remove("Remove", "remove"),
}

/**
 * Live page in a WebView: tap an element to build a selector for the chosen field. Each selector
 * is also checked against the downloaded HTML, which is what imports actually parse.
 * Navigate mode passes taps to the page; leaving the Test URL asks first, then makes the new page
 * the Test URL ([onUrlChange]). [desktop] is the rule's Desktop site setting.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PagePickerOverlay(
    url: String,
    initial: PickerFields,
    desktop: Boolean,
    onDesktopChange: (Boolean) -> Unit,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onDone: (PickerFields) -> Unit,
) {
    val context = LocalContext.current
    val pickerJs = remember {
        context.assets.open("picker/picker.js").bufferedReader().use { it.readText() }
    }
    var fields by remember { mutableStateOf(initial) }
    var active by remember { mutableStateOf(PickField.Body) }
    var pageUrl by remember { mutableStateOf(url) }
    var raw by remember { mutableStateOf<Document?>(null) }
    var rawError by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf<List<PathNode>?>(null) }
    var pickedText by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<PickedSelector?>(null) }
    var liveCount by remember { mutableStateOf<Int?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var pageLoads by remember { mutableIntStateOf(0) }
    var navMode by remember { mutableStateOf(false) }
    var pendingNav by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val currentUrlChange by rememberUpdatedState(onUrlChange)

    var applied by remember { mutableStateOf<String?>(null) }

    fun js(script: String, result: ((String) -> Unit)? = null) {
        web?.evaluateJavascript(script) { result?.invoke(it) }
    }

    fun dropPick() {
        path = null
        picked = null
        pickedText = ""
        applied = null
        js("__flowPicker.clear()")
    }

    fun open(target: String) {
        pageUrl = target
        dropPick()
        pageReady = false
        web?.loadUrl(target)
    }

    val onPick by rememberUpdatedState<(String) -> Unit> { json ->
        runCatching { parsePick(json) }.getOrNull()?.let { (nodes, text) ->
            path = nodes
            pickedText = text
        }
    }

    LaunchedEffect(pageUrl, desktop) {
        raw = null
        rawError = null
        try {
            raw = withContext(Dispatchers.IO) { Jsoup.parse(WebPageIngest.fetchHtml(pageUrl, desktop), pageUrl) }
        } catch (t: Throwable) {
            rawError = t.message ?: "Could not download page"
        }
    }

    LaunchedEffect(desktop) {
        val view = web ?: return@LaunchedEffect
        val agent = WebPageIngest.userAgent(desktop)
        if (view.settings.userAgentString == agent) return@LaunchedEffect
        view.settings.userAgentString = agent
        open(pageUrl)
    }

    LaunchedEffect(path, raw) {
        val nodes = path ?: return@LaunchedEffect
        val built = SelectorBuilder.build(nodes, raw, similar = active == PickField.Remove)
        picked = built
        liveCount = null
        built ?: return@LaunchedEffect
        fields = fields.withPick(active, built.selector, replacing = applied)
        applied = built.selector
        js("__flowPicker.count(${JSONObject.quote(built.selector)})") { r -> liveCount = r.toIntOrNull() }
    }

    LaunchedEffect(fields, pageReady, pageLoads) {
        if (!pageReady) return@LaunchedEffect
        PickField.entries.forEach { f ->
            js("__flowPicker.highlight('${f.jsKey}', ${JSONObject.quote(fields[f])})")
        }
    }

    val current = fields[active]
    FlowFullscreenCard(
        visible = true,
        onDismiss = onDismiss,
        title = "Pick on page",
        scrollable = false,
        bodyPadding = PaddingValues(FlowTokens.Space.None),
        bodySpacing = Arrangement.spacedBy(FlowTokens.Space.None),
        headerActions = {
            FlowIconButton(
                Icons.Filled.Explore,
                if (navMode) "Navigate mode on" else "Navigate mode",
                {
                    navMode = !navMode
                    js("__flowPicker.setMode('${if (navMode) "nav" else "pick"}')")
                },
                tint = if (navMode) MaterialTheme.colorScheme.primary else Color.Unspecified,
            )
            FlowIconButton(
                if (desktop) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                if (desktop) "Desktop site, switch to mobile" else "Mobile site, switch to desktop",
                { onDesktopChange(!desktop) },
            )
        },
        tabs = {
            FlowTabBar(
                tabs = PickField.entries.map { f ->
                    FlowTab.text(
                        f.label,
                        selected = active == f,
                        onClick = {
                            if (active != f) dropPick()
                            active = f
                        },
                        key = f,
                    )
                },
                level = FlowTabLevel.Secondary,
                inset = FlowTokens.Space.XS,
            )
        },
        footer = {
            FlowActionRow(
                start = {
                    if (!navMode) {
                        FlowIconButton(
                            Icons.Filled.UnfoldLess,
                            "Narrower",
                            { js("__flowPicker.narrower()") },
                            enabled = path != null,
                        )
                        FlowIconButton(
                            Icons.Filled.UnfoldMore,
                            "Wider",
                            { js("__flowPicker.wider()") },
                            enabled = path != null,
                        )
                    }
                },
            ) {
                if (!navMode && current.isNotBlank()) {
                    FlowTextAction(
                        "Clear",
                        {
                            fields = fields.with(active, "")
                            dropPick()
                        },
                        destructive = true,
                    )
                }
                FlowTextAction("Save", { onDone(fields) })
            }
        },
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = WebPageIngest.userAgent(desktop)
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false
                    addJavascriptInterface(PickerBridge { json -> onPick(json) }, "FlowPicker")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame || !pageReady) return false
                            if (!navMode) return true
                            val target = request.url.toString()
                            if (samePage(target, pageUrl)) return false
                            pendingNav = target
                            return true
                        }

                        // Script-driven URL changes (history.pushState) can't be stopped; follow them.
                        override fun doUpdateVisitedHistory(view: WebView, visited: String?, isReload: Boolean) {
                            if (!navMode || !pageReady || visited == null || samePage(visited, pageUrl)) return
                            pageUrl = visited
                            currentUrlChange(visited)
                        }

                        override fun onPageFinished(view: WebView, loaded: String?) {
                            view.evaluateJavascript(pickerJs, null)
                            if (view.settings.userAgentString == WebPageIngest.DESKTOP_USER_AGENT) {
                                view.evaluateJavascript(DESKTOP_VIEWPORT_JS, null)
                            }
                            if (navMode) view.evaluateJavascript("__flowPicker.setMode('nav')", null)
                            pageReady = true
                            pageLoads++
                        }
                    }
                    loadUrl(url)
                    web = this
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        if (navMode) {
            Text(
                "Navigate: taps go to the page. Leaving this page asks first and changes the Test URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
            )
        } else {
            PickInspector(
                active = active,
                saved = current,
                savedCount = if (current.isBlank()) null else SelectorBuilder.countIn(raw, current),
                picked = picked,
                pickedText = pickedText,
                liveCount = liveCount,
                rawError = rawError,
                rawLoading = raw == null && rawError == null,
            )
        }
    }
    FlowConfirmCard(
        visible = pendingNav != null,
        title = "Leave the Test URL?",
        message = "This opens ${pendingNav.orEmpty()} and makes it the rule's Test URL.",
        confirmLabel = "Open",
        onConfirm = {
            pendingNav?.let { target ->
                pendingNav = null
                currentUrlChange(target)
                open(target)
            }
        },
        onDismiss = { pendingNav = null },
    )
}

/** Responsive sites size to the phone even with a desktop user agent; lay them out at desktop width. */
private const val DESKTOP_VIEWPORT_JS = """(function () {
  var m = document.querySelector('meta[name=viewport]');
  if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
  m.setAttribute('content', 'width=1200');
})();"""

private fun samePage(a: String, b: String): Boolean =
    a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')

/** Selector under consideration (new pick, else the saved value) with its match counts. */
@Composable
private fun PickInspector(
    active: PickField,
    saved: String,
    savedCount: Int?,
    picked: PickedSelector?,
    pickedText: String,
    liveCount: Int?,
    rawError: String?,
    rawLoading: Boolean,
) {
    val selector = picked?.selector ?: saved.ifBlank { null }
    val counts = when {
        picked != null -> "Page ${liveCount ?: "…"} · Import ${if (rawLoading) "…" else picked.rawMatches}"
        savedCount != null -> "Import ${if (rawLoading) "…" else savedCount}"
        else -> ""
    }
    val note: Pair<String, Boolean>? = when {
        rawError != null -> "Downloaded copy failed ($rawError); import counts unavailable." to true
        picked != null && !rawLoading && (!picked.inRaw || picked.rawMatches == 0) ->
            "Not in fetched HTML, won't import. Try Wider." to true
        picked != null && pickedText.isNotBlank() -> pickedText to false
        else -> null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                selector ?: "${active.label} not set. Tap an element on the page.",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = if (selector != null) FontFamily.Monospace else null,
                color = if (selector != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (counts.isNotEmpty()) {
                Text(
                    counts,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.padding(start = FlowTokens.Space.S),
                )
            }
        }
        note?.let { (text, error) ->
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class PickerBridge(private val handler: (String) -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onPick(json: String) {
        main.post { handler(json) }
    }
}

private fun parsePick(json: String): Pair<List<PathNode>, String> {
    val o = JSONObject(json)
    val arr = o.getJSONArray("path")
    val nodes = (0 until arr.length()).map { i ->
        val n = arr.getJSONObject(i)
        val cls = n.optJSONArray("classes")
        PathNode(
            tag = n.getString("tag"),
            id = n.optString("id"),
            classes = if (cls == null) emptyList() else (0 until cls.length()).map(cls::getString),
            nthOfType = n.optInt("nth", 1),
        )
    }
    return nodes to o.optString("text")
}
