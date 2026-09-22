package com.personal.flowreader.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.theme.FlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receives share aliases. Routes via [ShareRouter]; shows floating overlay or in-app chooser.
 */
class ShareIngressActivity : ComponentActivity() {
    private var overlay: ShareOverlayController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val fromQue = intent?.component?.className?.endsWith("ShareQueAlias") == true
        if (text.isEmpty()) {
            finish()
            return
        }
        val payload = SharePayload(text = text, fromQueAlias = fromQue)
        setContent {
            FlowTheme(mode = ThemeMode.Oled) {
                var showFallback by remember { mutableStateOf(false) }
                var busy by remember { mutableStateOf(true) }
                LaunchedEffect(payload) {
                    val app = application as FlowApp
                    val prefs = withContext(Dispatchers.IO) { app.settings.shareOnce() }
                    val rules = withContext(Dispatchers.IO) { app.settings.shareDomainRulesOnce() }
                    val action = ShareRouter.decide(payload, prefs, rules)
                    busy = false
                    when (action) {
                        is ShareAction.ShowChooser -> {
                            val ctrl = ShareOverlayController(applicationContext)
                            overlay = ctrl
                            val shown = ctrl.show(
                                payload = payload,
                                onPick = { picked -> dispatch(picked); finish() },
                                onCancel = { finish() },
                            )
                            if (!shown) showFallback = true
                            else {
                                // Keep activity alive under overlay briefly, then move back.
                                moveTaskToBack(true)
                            }
                        }
                        else -> {
                            dispatch(action)
                            finish()
                        }
                    }
                }
                if (showFallback) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FallbackChooser(
                            payload = payload,
                            onPick = { dispatch(it); finish() },
                            onCancel = { finish() },
                        )
                    }
                } else if (busy) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun dispatch(action: ShareAction) {
        startActivity(ShareDispatch.intentFor(this, action))
    }

    override fun onDestroy() {
        overlay?.dismiss()
        overlay = null
        super.onDestroy()
    }
}

@Composable
private fun FallbackChooser(
    payload: SharePayload,
    onPick: (ShareAction) -> Unit,
    onCancel: () -> Unit,
) {
    val url = payload.url
    val rr = ShareRouter.isRoyalRoadHost(UrlDetector.hostOf(url.orEmpty()))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Send to Flow Reader", style = MaterialTheme.typography.titleMedium)
            Text(
                "Overlay permission is off — using in-app picker.\n" +
                    "Turn on Manual Override in Import settings to grant display-over-other-apps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                url ?: payload.text.take(120),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
            if (rr && url != null) {
                Button(
                    onClick = { onPick(ShareAction.RoyalRoadPlugin(url)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Plugin") }
                OutlinedButton(
                    onClick = { onPick(ShareAction.RoyalRoadSimple(url, toQueue = true)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Queue") }
            } else {
                Button(
                    onClick = { onPick(ShareAction.ToFiles(payload.text)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Files") }
                OutlinedButton(
                    onClick = { onPick(ShareAction.ToQueue(payload.text)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Queue") }
                if (url != null) {
                    OutlinedButton(
                        onClick = {
                            onPick(
                                ShareAction.Crawl(
                                    url,
                                    ShareDomainRule(hostPattern = UrlDetector.hostOf(url).orEmpty()),
                                    toQueue = false,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Parse page") }
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
