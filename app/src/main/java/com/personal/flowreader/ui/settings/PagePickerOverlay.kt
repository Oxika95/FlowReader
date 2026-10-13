package com.personal.flowreader.ui.settings

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DesktopWindows
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import com.personal.flowreader.R
import com.personal.flowreader.share.PathNode
import com.personal.flowreader.share.PickedSelector
import com.personal.flowreader.share.SelectorBuilder
import com.personal.flowreader.share.WebArticle
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
    stripHidden: Boolean = true,
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
    var options by remember { mutableStateOf<List<PickedSelector>>(emptyList()) }
    var optionIndex by remember { mutableIntStateOf(0) }
    val picked = options.getOrNull(optionIndex)
    var pageReady by remember { mutableStateOf(false) }
    var pageLoads by remember { mutableIntStateOf(0) }
    var navMode by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var pendingNav by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    val currentUrlChange by rememberUpdatedState(onUrlChange)

    var applied by remember { mutableStateOf<String?>(null) }

    fun js(script: String, result: ((String) -> Unit)? = null) {
        web?.evaluateJavascript(script) { result?.invoke(it) }
    }

    fun setNavMode(on: Boolean) {
        navMode = on
        js("__flowPicker.setMode('${if (on) "nav" else "pick"}')")
    }

    fun applyOption(index: Int) {
        val option = options.getOrNull(index) ?: return
        optionIndex = index
        fields = fields.withPick(active, option.selector, replacing = applied)
        applied = option.selector
    }

    fun dropPick() {
        path = null
        options = emptyList()
        optionIndex = 0
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
        options = SelectorBuilder.options(nodes, raw, similar = active == PickField.Remove)
        applyOption(0)
    }

    LaunchedEffect(fields, pageReady, pageLoads) {
        if (!pageReady) return@LaunchedEffect
        PickField.entries.forEach { f ->
            js("__flowPicker.highlight('${f.jsKey}', ${JSONObject.quote(fields[f])})")
        }
    }

    val preview by produceState<Result<WebArticle>?>(null, previewing, fields, raw, stripHidden) {
        val doc = raw
        value = if (!previewing || doc == null) {
            null
        } else {
            withContext(Dispatchers.Default) {
                runCatching {
                    WebPageIngest.extractArticle(doc.outerHtml(), pageUrl, fields.selectors(), stripHidden)
                }
            }
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
            if (!previewing) {
                FlowIconButton(
                    ImageVector.vectorResource(if (navMode) R.drawable.ic_picker_navigate else R.drawable.ic_picker_pick),
                    if (navMode) "Navigate mode, switch to picking" else "Picking, switch to navigate mode",
                    { setNavMode(!navMode) },
                )
            }
            if (!previewing) {
                FlowIconButton(
                    if (desktop) Icons.Filled.DesktopWindows else Icons.Filled.PhoneAndroid,
                    if (desktop) "Desktop site, switch to mobile" else "Mobile site, switch to desktop",
                    { onDesktopChange(!desktop) },
                )
            }
            FlowIconButton(
                ImageVector.vectorResource(if (previewing) R.drawable.ic_picker_preview else R.drawable.ic_picker_page),
                if (previewing) "Parse result, switch to the page" else "Page, switch to the parse result",
                {
                    if (!previewing) setNavMode(false)
                    previewing = !previewing
                },
            )
        },
        tabs = if (navMode) {
            {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(FlowTokens.Comp.TabBar)
                        .padding(horizontal = FlowTokens.Space.XS),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlowIconButton(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        { web?.goBack() },
                        enabled = canGoBack,
                    )
                    Text(
                        pageUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FlowIconButton(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        "Forward",
                        { web?.goForward() },
                        enabled = canGoForward,
                    )
                }
            }
        } else {
            {
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
            }
        },
        footer = {
            FlowActionRow(
                modifier = Modifier.heightIn(min = FlowTokens.Comp.ButtonPrimary),
                start = {
                    if (!navMode && !previewing) {
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
                        FlowIconButton(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            "Previous selector",
                            { applyOption((optionIndex - 1).mod(options.size)) },
                            enabled = options.size > 1,
                        )
                        FlowIconButton(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            "Next selector",
                            { applyOption((optionIndex + 1).mod(options.size)) },
                            enabled = options.size > 1,
                        )
                    }
                },
            ) {
                if (!navMode && !previewing && current.isNotBlank()) {
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
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
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
                                canGoBack = view.canGoBack()
                                canGoForward = view.canGoForward()
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
                modifier = Modifier.fillMaxSize(),
            )
            if (previewing) ParsePreview(preview, rawError, Modifier.matchParentSize())
        }
        if (!previewing && navMode) {
            Text(
                "Navigate: taps go to the page. Leaving this page asks first and changes the Test URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FlowTokens.Pad.CardBody, vertical = FlowTokens.Space.XS),
            )
        } else if (!previewing) {
            PickInspector(
                active = active,
                saved = current,
                picked = picked,
                pickedText = pickedText,
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
