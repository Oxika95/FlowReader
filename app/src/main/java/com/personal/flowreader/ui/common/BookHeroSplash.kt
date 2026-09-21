package com.personal.flowreader.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Shared hero cover band used by Files and Royal Road story splashes.
 */
@Composable
fun BookHeroCover(
    art: ImageBitmap?,
    title: String,
    subtitle: String?,
    canBlur: Boolean,
    modifier: Modifier = Modifier,
    placeholderLetter: Boolean = true,
    belowCover: @Composable ColumnScope.() -> Unit = {},
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(FlowTokens.CoverAspect),
        ) {
            if (art != null) {
                Image(
                    bitmap = art,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (canBlur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    if (placeholderLetter) {
                        Text(
                            title.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FlowTokens.CoverGradientHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, FlowTokens.CoverBandBlack),
                        ),
                    ),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(FlowTokens.CoverBandBlack),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FlowTokens.CoverMutedWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                belowCover()
            }
            overlayExtras()
        }
    }
}
