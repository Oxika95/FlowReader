package com.personal.flowreader.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.personal.flowreader.FlowApp
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.design.layer.FlowOverlayHost
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
                FlowOverlayHost {
                    if (chooser != null) {
                        ShareChooserCard(
                            chooser = chooser,
                            note = "Overlay permission is off — using in-app picker.\n" +
                                "Turn on Manual Override in Import settings to grant display-over-other-apps.",
                            onPick = { dispatch(it); finish() },
                            onCancel = { finish() },
                        )
                    } else if (busy) {
                        Box(Modifier.fillMaxSize())
                    }
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
