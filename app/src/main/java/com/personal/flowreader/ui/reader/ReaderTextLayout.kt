package com.personal.flowreader.ui.reader

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/** Rounded TTS highlight pills; clamps to visible lines (e.g. pin card maxLines). */
internal fun DrawScope.drawTtsHighlightRange(
    layout: TextLayoutResult,
    range: IntRange,
    color: Color,
    pad: Float,
    radius: Float,
) {
    val len = layout.layoutInput.text.length
    if (len <= 0 || layout.lineCount <= 0) return
    val start = range.first.coerceIn(0, len)
    val endExclusive = (range.last + 1).coerceIn(start, len)
    if (start >= endExclusive) return
    val lastLine = layout.lineCount - 1
    val startLine = layout.getLineForOffset(start).coerceIn(0, lastLine)
    val endLine = layout.getLineForOffset((endExclusive - 1).coerceAtLeast(start))
        .coerceIn(startLine, lastLine)
    for (line in startLine..endLine) {
        val lineStart = maxOf(layout.getLineStart(line), start)
        val lineEndExclusive = minOf(
            layout.getLineEnd(line, visibleEnd = true),
            endExclusive,
        )
        if (lineStart >= lineEndExclusive) continue
        val lastChar = (lineEndExclusive - 1).coerceIn(0, len - 1)
        val firstChar = lineStart.coerceIn(0, len - 1)

        // Geometry APIs report pre-justify (start-aligned) x. Shift by the same
        // per-space expansion Android applies at draw time for TextAlign.Justify.
        val startBox = layout.getBoundingBox(firstChar)
        val endBox = layout.getBoundingBox(lastChar)
        var left = minOf(startBox.left, endBox.left) + justifyXShift(layout, firstChar)
        var right = maxOf(startBox.right, endBox.right) + justifyXShift(layout, lastChar)
        if (right <= left) {
            left = layout.getHorizontalPosition(firstChar, usePrimaryDirection = true) +
                justifyXShift(layout, firstChar)
            right = layout.getHorizontalPosition(lineEndExclusive, usePrimaryDirection = true) +
                justifyXShift(layout, (lineEndExclusive - 1).coerceAtLeast(lineStart))
            if (right < left) {
                val tmp = left
                left = right
                right = tmp
            }
        }

        // Full-line spans snap to the justified line edges (edge-to-edge).
        val fullStart = layout.getLineStart(line)
        val fullEnd = layout.getLineEnd(line, visibleEnd = true)
        if (lineStart <= fullStart && lineEndExclusive >= fullEnd) {
            left = layout.getLineLeft(line)
            right = layout.getLineRight(line)
        }

        val l = left - pad
        val r = right + pad
        val rect = Rect(
            left = l.coerceAtLeast(0f),
            top = layout.getLineTop(line),
            right = r.coerceAtMost(size.width),
            bottom = layout.getLineBottom(line),
        )
        if (rect.width <= 0f || rect.height <= 0f) continue
        drawRoundRect(
            color = color,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(radius, radius),
        )
    }
}

/**
 * Android/Compose apply [TextAlign.Justify] as extra width on U+0020 at draw time;
 * [TextLayoutResult.getBoundingBox] / [TextLayoutResult.getPathForRange] still return
 * the pre-justify caret. Mirror TextLine.justify so highlight x matches glyphs.
 */
private fun justifyXShift(layout: TextLayoutResult, offset: Int): Float {
    if (layout.layoutInput.style.textAlign != TextAlign.Justify) return 0f
    val text = layout.layoutInput.text
    val len = text.length
    if (len <= 0) return 0f
    val line = layout.getLineForOffset(offset.coerceIn(0, len - 1))
    val lineEnd = layout.getLineEnd(line)
    // Same rule as Layout.isJustificationRequired: not the last line / newline line.
    if (lineEnd >= len) return 0f
    if (lineEnd > 0 && text[lineEnd - 1] == '\n') return 0f

    val lineStart = layout.getLineStart(line)
    var end = lineEnd
    while (end > lineStart && isAndroidLineEndSpace(text[end - 1])) end--
    if (end <= lineStart) return 0f

    var spaces = 0
    for (i in lineStart until end) {
        if (text[i] == ' ') spaces++
    }
    if (spaces == 0) return 0f

    val justifyWidth = layout.getLineRight(line) - layout.getLineLeft(line)
    val naturalWidth = layout.getHorizontalPosition(end, usePrimaryDirection = true) -
        layout.getHorizontalPosition(lineStart, usePrimaryDirection = true)
    val added = (justifyWidth - kotlin.math.abs(naturalWidth)) / spaces
    if (added <= 0f) return 0f

    val limit = offset.coerceIn(lineStart, end)
    var before = 0
    for (i in lineStart until limit) {
        if (text[i] == ' ') before++
    }
    return added * before
}

/** Keep in sync with android.text.TextLine.isLineEndSpace. */
private fun isAndroidLineEndSpace(ch: Char): Boolean =
    ch == ' ' || ch == '\t' || ch == 0x1680.toChar() ||
        (ch in 0x2000.toChar()..0x200A.toChar() && ch != 0x2007.toChar()) ||
        ch == 0x205F.toChar() || ch == 0x3000.toChar()

internal fun sentenceAtPosition(
    sentences: List<BlockSentence>,
    layout: TextLayoutResult,
    pos: Offset,
): BlockSentence? {
    if (sentences.isEmpty()) return null
    val offset = layout.getOffsetForPosition(pos).coerceIn(0, layout.layoutInput.text.length)
    sentences.firstOrNull { offset in it.start until it.end.coerceAtLeast(it.start + 1) }
        ?.let { return it }
    // Prefer the nearest sentence by start offset when landing on whitespace gaps.
    return sentences.minByOrNull { abs(it.start - offset) }
}
