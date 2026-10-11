package com.personal.flowreader.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import com.personal.flowreader.ui.theme.FlowTokens

/** Silhouette icons for the fixed library tabs, drawn to match tinted plugin `icon.png` images. */
internal object LibraryTabIcons {
    /** Three book spines with cut-out bands. */
    val Files: ImageVector by lazy {
        glyph("Files") {
            listOf(1.2f to 1.4f, 16.4f to 1.4f).forEach { (left, top) ->
                roundRect(left, top, left + 6.4f, 22.6f, 0.9f)
                roundRect(left + 1.2f, 4.9f, left + 5.2f, 5.9f, 0.5f)
                roundRect(left + 1.2f, 15.9f, left + 5.2f, 16.9f, 0.5f)
                roundRect(left + 1.2f, 18.1f, left + 5.2f, 19.1f, 0.5f)
            }
            roundRect(8.8f, 5.8f, 15.2f, 22.6f, 0.9f)
            roundRect(10.0f, 9.5f, 14.0f, 10.5f, 0.5f)
            roundRect(10.0f, 18.6f, 14.0f, 19.6f, 0.5f)
        }
    }

    /** Layer stack: rounded diamond over two chevron bands. */
    val Queue: ImageVector by lazy {
        ImageVector.Builder("Queue", FlowTokens.Comp.TabIcon, FlowTokens.Comp.TabIcon, 24f, 24f)
            .path(
                fill = SolidColor(Color.Black),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                lineTo(21f, 8f)
                lineTo(12f, 13f)
                lineTo(3f, 8f)
                close()
            }
            .path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                listOf(12f, 16f).forEach { y ->
                    moveTo(3f, y)
                    lineTo(12f, y + 5f)
                    lineTo(21f, y)
                }
            }
            .build()
    }

    private fun glyph(name: String, shapes: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, FlowTokens.Comp.TabIcon, FlowTokens.Comp.TabIcon, 24f, 24f)
            .path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd, pathBuilder = shapes)
            .build()

    private fun PathBuilder.roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) {
        moveTo(left + r, top)
        horizontalLineTo(right - r)
        arcTo(r, r, 0f, false, true, right, top + r)
        verticalLineTo(bottom - r)
        arcTo(r, r, 0f, false, true, right - r, bottom)
        horizontalLineTo(left + r)
        arcTo(r, r, 0f, false, true, left, bottom - r)
        verticalLineTo(top + r)
        arcTo(r, r, 0f, false, true, left + r, top)
        close()
    }
}
