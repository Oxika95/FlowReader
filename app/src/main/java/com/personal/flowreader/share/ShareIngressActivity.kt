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
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.theme.FlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Receives share. Routes via [ShareRouter]; shows floating overlay or in-app chooser
 * only when Manual Override asks Plugin vs Parse for a handoff URL.
 */
class ShareIngressActivity : ComponentActivity() {
    private var overlay: ShareOverlayController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isEmpty()) {
            finish()
            return
        }
        val payload = SharePayload(text = text)
        setContent {
            FlowTheme(mode = ThemeMode.Oled) {
                var fallback by remember { mutableStateOf<ShareAction.ShowChooser?>(null) }
                var busy by remember { mutableStateOf(true) }
                LaunchedEffect(payload) {
                    val app = application as FlowApp
                    val prefs = withContext(Dispatchers.IO) { app.settings.shareOnce() }
                    val handoffs = withContext(Dispatchers.IO) { app.settings.shareRouterRulesOnce() }
                    val parseRules = withContext(Dispatchers.IO) { app.settings.shareParseRulesOnce() }
                    val action = ShareRouter.decide(payload, prefs, handoffs, parseRules)
                    busy = false
                    when (action) {
                        is ShareAction.ShowChooser -> {
                            val ctrl = ShareOverlayController(applicationContext)
                            overlay = ctrl
                            val shown = ctrl.show(
                                chooser = action,
                                onPick = { picked -> dispatch(picked); finish() },
                                onCancel = { finish() },
                            )
                            if (!shown) fallback = action
                            else moveTaskToBack(true)
                        }
                        else -> {
                            dispatch(action)
                            finish()
                        }
                    }
                }
                val chooser = fallback
                if (chooser != null) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        FallbackChooser(
                            chooser = chooser,
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
    chooser: ShareAction.ShowChooser,
    onPick: (ShareAction) -> Unit,
    onCancel: () -> Unit,
) {
    val url = chooser.payload.url ?: return
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
                url,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
            )
            Button(
                onClick = { onPick(ShareAction.RoyalRoadPlugin(url)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Plugin") }
            OutlinedButton(
                onClick = { onPick(chooser.urlFallback) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when (val fb = chooser.urlFallback) {
                        is ShareAction.Crawl ->
                            if (fb.landing.id == RouterLanding.FILES) "Parse → Files" else "Parse → Queue"
                        is ShareAction.ToQueue -> "Queue URL"
                        is ShareAction.ToFiles -> "Files"
                        else -> "URL default"
                    },
                )
            }
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}
