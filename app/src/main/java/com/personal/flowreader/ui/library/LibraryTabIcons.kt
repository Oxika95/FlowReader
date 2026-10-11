package com.personal.flowreader.ui.library

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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

    /** Stacked video card with a cut-out play triangle. */
    val Queue: ImageVector by lazy {
        glyph("Queue") {
            roundRect(5.8f, 2.6f, 18.2f, 3.8f, 0.6f)
            roundRect(4.2f, 5.2f, 19.8f, 6.4f, 0.6f)
            roundRect(2.4f, 8.0f, 21.6f, 22.4f, 2.0f)
            moveTo(10.4f, 11.6f)
            lineTo(15.8f, 15.2f)
            lineTo(10.4f, 18.8f)
            close()
        }
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
