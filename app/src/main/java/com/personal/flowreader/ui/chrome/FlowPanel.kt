package com.personal.flowreader.ui.chrome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.theme.FlowTokens


internal val ReaderPanelShape = FlowTokens.PanelShape
internal val ReaderPanelFeather = FlowTokens.Icon.M
/** Left inset of reading text (locus rail gutter); bars are centered in this width. */
internal val ReaderContentStartPadding = FlowTokens.ScreenGutter
/** End padding on the reading LazyColumn (right gutter). */
internal val ReaderListEndPadding = FlowTokens.ScreenGutter
/** Extra end padding on the paragraph text itself. */
internal val ReaderTextEndPadding = FlowTokens.Radius.None
/** Right inset of reading text — panels must land on the same edge as the text column. */
internal val ReaderContentEndPadding = ReaderListEndPadding + ReaderTextEndPadding
/** Top/bottom inset for Settings/TOC cards — same scale as the reading-column side gutters. */
internal val ReaderOverlayVerticalPad = ReaderContentStartPadding

/** Soft page-colored halo outside the border so panels separate from reading text. */
private fun DrawScope.drawReaderPanelFeather(
    color: Color,
    corner: Dp,
    feather: Dp,
) {
    val featherPx = feather.toPx()
    if (featherPx <= 0f) return
    val baseCorner = corner.toPx()
    val steps = 14
    for (i in steps downTo 1) {
        val frac = i / steps.toFloat()
        val expand = featherPx * frac
        val alpha = (1f - frac) * (1f - frac) * 0.78f
        if (alpha < 0.01f) continue
        drawRoundRect(
            color = color.copy(alpha = alpha),
            topLeft = Offset(-expand, -expand),
            size = Size(size.width + expand * 2f, size.height + expand * 2f),
            cornerRadius = CornerRadius(baseCorner + expand * 0.4f),
        )
    }
}

/**
 * Reader chrome surface: theme background, outline border, zero elevation,
 * feathered fade outside the box so cards read apart from page text.
 *
 * When [matchReaderWidth] is true, the solid border aligns with the reading
 * column; the feather extends into the side margins beyond that border.
 */
@Composable
internal fun ReaderPanelSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ReaderPanelShape,
    feather: Dp = ReaderPanelFeather,
    /** Align solid card width with the ereader text column. */
    matchReaderWidth: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val cardInset = if (matchReaderWidth) {
        Modifier.padding(
            start = ReaderContentStartPadding,
            end = ReaderContentEndPadding,
            top = feather,
            bottom = feather,
        )
    } else {
        Modifier.padding(feather)
    }
    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .then(cardInset)
                .then(if (matchReaderWidth) Modifier.fillMaxWidth() else Modifier.wrapContentWidth())
                .then(
                    if (feather > FlowTokens.Radius.None) {
                        Modifier.drawBehind {
                            drawReaderPanelFeather(bg, FlowTokens.PanelRadius, feather)
                        }
                    } else {
                        Modifier
                    },
                ),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = bg,
                contentColor = onBg,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = FlowTokens.Radius.None),
            border = BorderStroke(FlowTokens.Stroke.Hairline, borderColor),
        ) {
            content()
        }
    }
}

@Composable
internal fun FloatingPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = FlowTokens.Space.M,
        vertical = FlowTokens.Space.M,
    ),
    /** Soft halo outside the border; None keeps a hard edge aligned with reading text. */
    feather: Dp = ReaderPanelFeather,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReaderPanelSurface(
        modifier = modifier,
        matchReaderWidth = true,
        feather = feather,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}


@Composable
internal fun FlowPanelSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ReaderPanelShape,
    feather: Dp = ReaderPanelFeather,
    matchReaderWidth: Boolean = false,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    content: @Composable () -> Unit,
) {
    ReaderPanelSurface(
        modifier = modifier,
        shape = shape,
        feather = feather,
        matchReaderWidth = matchReaderWidth,
        borderColor = borderColor,
        content = content,
    )
}
