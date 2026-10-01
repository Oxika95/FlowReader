package com.personal.flowreader.ui.design.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.personal.flowreader.ui.design.surface.FlowSurface
import com.personal.flowreader.ui.design.surface.FlowSurfaceStyle
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/**
 * Floating card for a [com.personal.flowreader.ui.design.layer.FlowDock] item: full dock width,
 * feathered panel surface. Title card, media card, now-playing card are all this.
 * Never position a floating card yourself — put it in a dock item.
 */
@Composable
fun FlowFloatingCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(FlowTokens.Pad.FloatingCard),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    FlowSurface(
        modifier = modifier.fillMaxWidth(),
        style = FlowSurfaceStyle.Panel,
        borderColor = borderColor,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** Content-width floating chip, centered in its dock item (e.g. "Jump to saved position"). */
@Composable
fun FlowFloatingChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        FlowSurface(style = FlowSurfaceStyle.Panel, onClick = onClick) {
            Text(
                label,
                style = FlowType.action,
                modifier = Modifier.padding(horizontal = FlowTokens.Space.M, vertical = FlowTokens.Space.S),
            )
        }
    }
}
