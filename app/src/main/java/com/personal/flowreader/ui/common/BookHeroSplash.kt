package com.personal.flowreader.ui.common

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.chrome.ReaderModalScaffold
import com.personal.flowreader.ui.chrome.ReaderPanelFeather
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Shared hero splash shell used by Files and Royal Road story overlays.
 * Pixel-matches the floating-panel cover band + action footer; callers supply band/body content.
 */
@Composable
fun BookHeroSplashShell(
    visible: Boolean,
    onDismiss: () -> Unit,
    art: ImageBitmap?,
    coverBandPadding: PaddingValues,
    coverBand: @Composable ColumnScope.(maxHeight: Dp) -> Unit,
    modifier: Modifier = Modifier,
    canBlur: Boolean = Build.VERSION.SDK_INT >= 31,
    placeholder: @Composable BoxScope.() -> Unit = {},
    overlayExtras: @Composable BoxScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit,
) {
    val cardBg = MaterialTheme.colorScheme.background
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(FlowTokens.Radius.None),
        onDismiss = onDismiss,
        feather = ReaderPanelFeather,
        scrimAlpha = FlowTokens.ScrimHero,
    ) {
        Column(modifier.fillMaxWidth()) {
            BookHeroCover(
                art = art,
                canBlur = canBlur,
                coverBandPadding = coverBandPadding,
                placeholder = placeholder,
                overlayExtras = overlayExtras,
                coverBand = coverBand,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.S),
                verticalArrangement = Arrangement.spacedBy(FlowTokens.SplashActionRowSpacing),
                content = body,
            )
        }
    }
}

/**
 * Cover band: blurred art (or placeholder), bottom gradient, black meta strip, optional overlays.
 */
@Composable
fun BookHeroCover(
    art: ImageBitmap?,
    canBlur: Boolean,
    coverBandPadding: PaddingValues,
    coverBand: @Composable ColumnScope.(maxHeight: Dp) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable BoxScope.() -> Unit = {},
    overlayExtras: @Composable BoxScope.() -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(
                art?.let { it.width.toFloat() / it.height.toFloat() }
                    ?: FlowTokens.CoverAspect,
            ),
    ) {
        val coverMaxHeight = maxHeight
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (canBlur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer),
                content = placeholder,
            )
        }
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(FlowTokens.CoverGradientHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, FlowTokens.CoverBandBlack),
                        ),
                    ),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(FlowTokens.CoverBandBlack)
                    .padding(coverBandPadding),
            ) {
                coverBand(coverMaxHeight)
            }
        }
        overlayExtras()
    }
}

/** Min-touch override + standard splash button row spacing. */
@Composable
fun BookHeroSplashButtons(
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(
        LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FlowTokens.SplashActionRowSpacing),
            content = content,
        )
    }
}

@Composable
fun BookHeroSecondaryButtonRow(
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Space.XS),
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.SplashActionRowSpacing),
        content = content,
    )
}

@Composable
fun RowScope.BookHeroSecondaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .height(FlowTokens.SplashSecondaryButtonHeight),
        contentPadding = PaddingValues(
            horizontal = FlowTokens.Space.XS,
            vertical = FlowTokens.Radius.None,
        ),
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BookHeroPrimaryButton(
    onClick: () -> Unit,
    enabled: Boolean,
    label: String,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FlowTokens.Space.XS)
            .height(FlowTokens.SplashPrimaryButtonHeight),
        contentPadding = PaddingValues(
            horizontal = FlowTokens.Space.L,
            vertical = FlowTokens.Radius.None,
        ),
    ) {
        Text(label)
    }
}
