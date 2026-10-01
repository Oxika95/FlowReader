package com.personal.flowreader.share

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowPrimaryButton
import com.personal.flowreader.ui.design.controls.FlowSecondaryButton
import com.personal.flowreader.ui.theme.FlowType

/**
 * Plugin-vs-parse chooser for a shared URL (Manual Override). Compact fullscreen card; needs a
 * `FlowOverlayHost` ancestor (the overlay window and the ingress fallback each provide one).
 */
@Composable
fun ShareChooserCard(
    chooser: ShareAction.ShowChooser,
    onPick: (ShareAction) -> Unit,
    onCancel: () -> Unit,
    note: String? = null,
) {
    val url = chooser.payload.url ?: return
    val (pluginLabel, pluginAction) = ShareDispatch.pluginChoice(chooser, url)
    FlowFullscreenCard(
        visible = true,
        onDismiss = onCancel,
        title = "Send to Flow Reader",
        variant = FlowCardVariant.Compact,
        footer = { FlowActionRow { FlowTextAction("Cancel", onCancel) } },
    ) {
        note?.let { FlowHint(it) }
        Text(
            url,
            style = FlowType.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        FlowPrimaryButton(pluginLabel, onClick = { onPick(pluginAction) })
        Row(Modifier.fillMaxWidth()) {
            FlowSecondaryButton(fallbackLabel(chooser.urlFallback), onClick = { onPick(chooser.urlFallback) })
        }
    }
}

internal fun fallbackLabel(fallback: ShareAction): String = when (fallback) {
    is ShareAction.Crawl -> if (fallback.landing.id == RouterLanding.FILES) "Parse → Files" else "Parse → Queue"
    is ShareAction.ToQueue -> "Queue URL"
    is ShareAction.ToFiles -> "Files"
    else -> "URL default"
}
