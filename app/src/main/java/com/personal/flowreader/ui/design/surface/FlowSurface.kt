package com.personal.flowreader.ui.design.surface

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.theme.FlowTokens

/** Visual style of a [FlowSurface]. */
enum class FlowSurfaceStyle {
    /** Floating and fullscreen cards: feathered halo separates the card from page text. */
    Panel,
    /** Display cards in lists and grids: hard edge, no halo. */
    Flat,
}

/**
 * Base surface for every Flow card: page background, hairline border, [FlowTokens.Shape.Card],
 * zero elevation. [FlowSurfaceStyle.Panel] adds a page-colored feather drawn *outside* the
 * bounds — it never takes layout space or touch area, so spacing is measured border to border.
 *
 * Click handlers are applied inside the clip, so ripples follow the rounded shape.
 * Do not wrap this in M3 `Card`/`Surface`; build card families on top of it instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlowSurface(
    modifier: Modifier = Modifier,
    style: FlowSurfaceStyle = FlowSurfaceStyle.Panel,
    shape: RoundedCornerShape = FlowTokens.Shape.Card,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    containerColor: Color = MaterialTheme.colorScheme.background,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val feather = if (style == FlowSurfaceStyle.Panel) FlowTokens.Feather.Panel else FlowTokens.Feather.None
    val interactive = onClick != null || onLongClick != null
    Box(
        modifier = modifier
            .then(
                if (feather > FlowTokens.Feather.None) {
                    Modifier.drawBehind { drawFeather(containerColor, FlowTokens.Radius.L, feather) }
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(containerColor)
            .border(FlowTokens.Stroke.Hairline, borderColor, shape)
            .then(
                if (interactive) {
                    Modifier.combinedClickable(
                        onClick = onClick ?: {},
                        onLongClick = onLongClick,
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

/** Page-colored halo that fades out over [feather] beyond the card edge. */
private fun DrawScope.drawFeather(color: Color, corner: Dp, feather: Dp) {
    val featherPx = feather.toPx()
    if (featherPx <= 0f) return
    val baseCorner = corner.toPx()
    val steps = FEATHER_STEPS
    for (i in steps downTo 1) {
        val frac = i / steps.toFloat()
        val expand = featherPx * frac
        val alpha = (1f - frac) * (1f - frac) * FEATHER_PEAK_ALPHA
        if (alpha < 0.01f) continue
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-expand, -expand),
            size = Size(size.width + expand * 2f, size.height + expand * 2f),
            cornerRadius = CornerRadius(baseCorner + expand * FEATHER_CORNER_GROWTH),
        )
    }
}

private const val FEATHER_STEPS = 14
private const val FEATHER_PEAK_ALPHA = 0.78f
private const val FEATHER_CORNER_GROWTH = 0.4f
