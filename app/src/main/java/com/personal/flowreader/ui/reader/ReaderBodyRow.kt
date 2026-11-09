package com.personal.flowreader.ui.reader

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.BookPlace
import com.personal.flowreader.data.ReaderItem
import com.personal.flowreader.data.Sentence
import com.personal.flowreader.data.SentenceTable
import com.personal.flowreader.ui.theme.FlowTokens

/** One reader paragraph/heading row (rail + text) for the Compose body list. */
@Composable
internal fun ReaderBodyRow(
    item: ReaderItem,
    items: List<ReaderItem>,
    locusIndex: Int,
    ttsPlaying: Boolean,
    ttsSentence: Sentence?,
    ttsWordHighlight: IntRange?,
    ttsReady: Set<Int>,
    ttsGenerating: Set<Int>,
    ttsDoubleTapPlay: Boolean,
    ttsSentenceIndex: Int,
    allSentences: SentenceTable,
    replacedRangesByBlockId: Map<String, List<IntRange>>,
    colors: androidx.compose.material3.ColorScheme,
    bodyStyle: TextStyle,
    headingStyle: TextStyle,
    justifyText: Boolean,
    currentSentenceIndex: Int,
    scrollLocked: Boolean,
    overlay: ReaderOverlay,
    selectionActive: Boolean,
    /** Live viewport coords for list-local sentence boxes; null skips reporting. */
    viewportCoords: () -> androidx.compose.ui.layout.LayoutCoordinates?,
    onSentenceSpan: (SentenceSpan) -> Unit,
    onClearSpansForBlock: (String) -> Unit,
    onGapTap: () -> Unit,
    onTextTap: () -> Unit,
    onDoubleTapTitle: () -> Unit,
    onDoubleTapBody: (place: BookPlace, homeIndex: Int, alreadySpeaking: Boolean) -> Unit,
    onForceRegenerate: (Int) -> Unit,
    isAlreadySpeaking: (Int) -> Boolean,
) {
    val index = ReaderHome.placeToAdapterIndex(
        items,
        BookPlace(item.chapterIndex, item.blockIndex, 0),
    )
    val active = index == locusIndex
    val highlight = active && ttsSentence != null &&
        ttsSentence.chapterIndex == item.chapterIndex &&
        ttsSentence.blockIndex == item.blockIndex &&
        ttsPlaying
    val sentenceRange: IntRange? =
        if (highlight) ttsSentence!!.start until ttsSentence.end else null
    val wordRange: IntRange? = when {
        !highlight -> null
        else -> {
            val spoken = ttsSentence!!
            val word = ttsWordHighlight
            if (word != null && word.first >= spoken.start && word.last < spoken.end) word else null
        }
    }
    val blockSentences = remember(allSentences, item.chapterIndex, item.blockIndex) {
        allSentences.inBlock(item.chapterIndex, item.blockIndex).mapNotNull { si ->
            allSentences.getOrNull(si)?.let { s -> BlockSentence(si, s.start, s.end) }
        }
    }
    var textLayout by remember(item.block.id, justifyText) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    var textCoords by remember(item.block.id) {
        mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null)
    }
    fun publishSentenceSpans(
        layout: TextLayoutResult,
        coords: androidx.compose.ui.layout.LayoutCoordinates,
    ) {
        // Read live: the Box's LayoutCoordinates object is replaced on remasure; a captured
        // instance can be detached while the controller already holds a new one.
        val viewport = viewportCoords() ?: return
        if (!viewport.isAttached || !coords.isAttached) return
        val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
        for (hit in blockSentences) {
            val start = hit.start.coerceIn(0, lastChar)
            val endInclusive = (hit.end - 1).coerceIn(start, lastChar)
            val lineTop = layout.getLineTop(layout.getLineForOffset(start))
            val lineBottom = layout.getLineBottom(layout.getLineForOffset(endInclusive))
            val top = viewport.localPositionOf(coords, Offset(0f, lineTop)).y
            val bottom = viewport.localPositionOf(coords, Offset(0f, lineBottom)).y
            onSentenceSpan(SentenceSpan(item.block.id, hit.index, top, bottom))
        }
    }
    DisposableEffect(item.block.id) {
        onDispose { onClearSpansForBlock(item.block.id) }
    }
    val replacedRanges = replacedRangesByBlockId[item.block.id].orEmpty()
    val sentenceHighlightColor = colors.secondary.copy(alpha = 0.40f)
    val wordHighlightColor = colors.primary.copy(alpha = 0.40f)
    val text = buildAnnotatedString {
        val raw = item.block.text
        append(raw)
        for (range in replacedRanges) {
            val start = range.first.coerceIn(0, raw.length)
            val end = (range.last + 1).coerceIn(start, raw.length)
            if (start < end) {
                addStyle(SpanStyle(color = colors.secondary), start, end)
            }
        }
    }
    val style = when (item.block.kind) {
        BlockKind.Heading -> headingStyle
        BlockKind.Quote, BlockKind.Paragraph -> bodyStyle
    }
    ReaderSelectionContainer(enabled = !scrollLocked) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(overlay, selectionActive) {
                    detectTapGestures(onTap = { onGapTap() })
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = FlowTokens.Space.M),
            ) {
                LocusRail(
                    modifier = Modifier
                        .width(RailGutterWidth)
                        .fillMaxHeight(),
                    sentences = blockSentences,
                    textLayout = textLayout,
                    currentSentenceIndex = currentSentenceIndex,
                    ready = ttsReady,
                    generating = ttsGenerating,
                    softAccent = colors.secondary,
                    onForceRegenerate = onForceRegenerate,
                )
                Text(
                    text,
                    style = style,
                    color = colors.onBackground,
                    onTextLayout = { layout ->
                        textLayout = layout
                        textCoords?.let { publishSentenceSpans(layout, it) }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            textCoords = coords
                            val layout = textLayout ?: return@onGloballyPositioned
                            publishSentenceSpans(layout, coords)
                        }
                        .drawBehind {
                            val layout = textLayout ?: return@drawBehind
                            val radius = 6.dp.toPx()
                            val pad = HighlightSidePad.toPx()
                            sentenceRange?.let {
                                drawTtsHighlightRange(layout, it, sentenceHighlightColor, pad, radius)
                            }
                            wordRange?.let {
                                drawTtsHighlightRange(layout, it, wordHighlightColor, pad, radius)
                            }
                        }
                        .pointerInput(
                            blockSentences,
                            overlay,
                            ttsDoubleTapPlay,
                            selectionActive,
                            index,
                        ) {
                            detectTapGestures(
                                onTap = { onTextTap() },
                                onDoubleTap = { pos ->
                                    if (item.isChapterTitle) {
                                        onDoubleTapTitle()
                                        return@detectTapGestures
                                    }
                                    val layout = textLayout
                                    val hit = if (layout != null) {
                                        sentenceAtPosition(blockSentences, layout, pos)
                                    } else {
                                        blockSentences.firstOrNull()
                                    }
                                    val charOffset = hit?.start ?: 0
                                    val place = BookPlace(item.chapterIndex, item.blockIndex, charOffset)
                                    val homeIndex = hit?.index
                                        ?: allSentences.indexAt(place)
                                    val already = hit != null && isAlreadySpeaking(hit.index)
                                    onDoubleTapBody(place, homeIndex, already)
                                },
                            )
                        },
                )
            }
            Spacer(Modifier.height(FlowTokens.Space.XS))
        }
    }
}
