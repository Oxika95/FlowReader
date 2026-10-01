package com.personal.flowreader.ui.design.surface

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** True when the platform supports `Modifier.blur` (API 31+). */
val CanBlur: Boolean get() = Build.VERSION.SDK_INT >= 31

/**
 * Cover art or a placeholder initial on `primaryContainer`. Every cover in the app renders
 * through this so placeholders and scaling stay consistent.
 */
@Composable
fun FlowCover(
    art: ImageBitmap?,
    title: String,
    modifier: Modifier = Modifier,
    blur: Boolean = false,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderStyle: TextStyle = FlowType.placeholder,
) {
    Box(
        modifier.background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (blur && CanBlur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
            )
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "?",
                style = placeholderStyle,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/** Where a [FlowProgressBar] sits, which decides its track color. */
enum class FlowProgressTrack {
    /** Page background (display cards, title card). */
    OnPage,
    /** Dark cover band (tiles, media card). */
    OnDark,
}

/** Thin reading-progress bar pinned to a card's bottom edge. */
@Composable
fun FlowProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    track: FlowProgressTrack = FlowProgressTrack.OnPage,
    roundBottom: Boolean = true,
) {
    val trackColor = when (track) {
        FlowProgressTrack.OnPage -> MaterialTheme.colorScheme.onBackground.copy(alpha = FlowTokens.Alpha.Track)
        FlowProgressTrack.OnDark -> Color.White.copy(alpha = FlowTokens.Alpha.TrackOnDark)
    }
    val shape = if (roundBottom) {
        RoundedCornerShape(bottomStart = FlowTokens.Radius.L, bottomEnd = FlowTokens.Radius.L)
    } else {
        RoundedCornerShape(FlowTokens.Radius.None)
    }
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(FlowTokens.Comp.ProgressBar)
            .clip(shape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = trackColor,
    )
}
