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
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val prev: String = "",
    val next: String = "",
    val remove: String = "",
) {
    operator fun get(field: PickField): String = when (field) {
        PickField.Title -> title
        PickField.Body -> body
        PickField.Previous -> prev
        PickField.Next -> next
        PickField.Remove -> remove
    }

    fun with(field: PickField, value: String): PickerFields = when (field) {
        PickField.Title -> copy(title = value)
        PickField.Body -> copy(body = value)
        PickField.Previous -> copy(prev = value)
        PickField.Next -> copy(next = value)
        PickField.Remove -> copy(remove = value)
    }
}

enum class PickField(val label: String, val jsKey: String) {
    Title("Title", "title"),
    Body("Body", "body"),
    Previous("Prev", "prev"),
    Next("Next", "next"),
    Remove("Remove", "remove"),
}

/**
 * Live page in a WebView: tap an element to build a selector for the chosen field. Each selector
 * is also checked against the downloaded HTML, which is what imports actually parse.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun PagePickerOverlay(
    url: String,
    initial: PickerFields,
    onDismiss: () -> Unit,
    onDone: (PickerFields) -> Unit,
) {
    val context = LocalContext.current
    val pickerJs = remember {
        context.assets.open("picker/picker.js").bufferedReader().use { it.readText() }
    }
    var fields by remember { mutableStateOf(initial) }
    var active by remember { mutableStateOf(PickField.Body) }
    var raw by remember { mutableStateOf<Document?>(null) }
    var rawError by remember { mutableStateOf<String?>(null) }
    var path by remember { mutableStateOf<List<PathNode>?>(null) }
    var pickedText by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<PickedSelector?>(null) }
    var liveCount by remember { mutableStateOf<Int?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }

    val onPick by rememberUpdatedState<(String) -> Unit> { json ->
        runCatching { parsePick(json) }.getOrNull()?.let { (nodes, text) ->
            path = nodes
            pickedText = text
        }
    }

    fun js(script: String, result: ((String) -> Unit)? = null) {
        web?.evaluateJavascript(script) { result?.invoke(it) }
    }

    LaunchedEffect(url) {
        try {
            raw = withContext(Dispatchers.IO) { Jsoup.parse(WebPageIngest.fetchHtml(url), url) }
        } catch (t: Throwable) {
            rawError = t.message ?: "Could not download page"
        }
    }

    LaunchedEffect(path, raw, active) {
        val nodes = path ?: return@LaunchedEffect
        val built = SelectorBuilder.build(nodes, raw, similar = active == PickField.Remove)
        picked = built
        liveCount = null
        built?.let { js("__flowPicker.count(${JSONObject.quote(it.selector)})") { r -> liveCount = r.toIntOrNull() } }
    }

    LaunchedEffect(fields, pageReady) {
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
        tabs = {
            FlowTabBar(
                tabs = PickField.entries.map { f ->
                    FlowTab.text(
                        f.label,
                        selected = active == f,
                        onClick = { active = f },
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
                    if (current.isNotBlank()) {
                        FlowTextAction("Clear", { fields = fields.with(active, "") }, destructive = true)
                    }
                },
            ) {
                FlowTextAction(
                    if (active == PickField.Remove) "Add" else "Set ${active.label}",
                    {
                        picked?.let { p ->
                            fields = if (active == PickField.Remove) {
                                val entries = CssList.split(fields.remove)
                                fields.with(active, CssList.join((entries + p.selector).distinct()))
                            } else {
                                fields.with(active, p.selector)
                            }
                        }
                    },
                    enabled = picked != null,
                )
                FlowTextAction("Done", { onDone(fields) })
            }
        },
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = WebPageIngest.USER_AGENT
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    addJavascriptInterface(PickerBridge { json -> onPick(json) }, "FlowPicker")
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            pageReady

                        override fun onPageFinished(view: WebView, loaded: String?) {
                            view.evaluateJavascript(pickerJs, null)
                            pageReady = true
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
