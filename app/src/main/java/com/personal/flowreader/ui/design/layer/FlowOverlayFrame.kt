package com.personal.flowreader.ui.design.layer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Positions one fullscreen card: clear of system bars, display cutout and the keyboard,
 * inset by the screen gutter, centered. Cards under the top card are scaled down slightly.
 */
@Composable
internal fun FlowOverlayFrame(
    isTop: Boolean,
    content: @Composable () -> Unit,
) {
    val scale by animateFloatAsState(if (isTop) 1f else FlowTokens.Scrim.ParentScale, label = "parentScale")
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(FlowTokens.Pad.Screen),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
