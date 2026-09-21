package com.personal.flowreader.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.personal.flowreader.ui.theme.FlowTokens

enum class BookProgressVariant {
    /** Theme track on library cards / reader banner. */
    Standard,
    /** Fixed white track on dark cover bands. */
    OnDarkBand,
}

@Composable
fun BookProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    variant: BookProgressVariant = BookProgressVariant.Standard,
    bottomRadius: Boolean = true,
) {
    val track = when (variant) {
        BookProgressVariant.Standard ->
            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
        BookProgressVariant.OnDarkBand ->
            Color.White.copy(alpha = 0.20f)
    }
    val shape = if (bottomRadius) {
        RoundedCornerShape(
            topStart = FlowTokens.Radius.None,
            topEnd = FlowTokens.Radius.None,
            bottomStart = FlowTokens.PanelRadius,
            bottomEnd = FlowTokens.PanelRadius,
        )
    } else {
        RoundedCornerShape(FlowTokens.Radius.None)
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(FlowTokens.ProgressBarHeight)
            .clip(shape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = track,
    )
}
