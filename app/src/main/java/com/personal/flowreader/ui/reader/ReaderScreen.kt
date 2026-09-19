package com.personal.flowreader.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ReaderFont
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.SentenceSplitter
import com.personal.flowreader.data.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Loading pulse — independent of accent. */
private val RailLoading = Color(0xFF6A6A6A)
private val RailLoadingBright = Color(0xFF8A8A8A)
private val EdgeFade = 96.dp
/** Full left margin from screen edge to text; bars are centered in this gutter. */
private val RailGutterWidth = ReaderContentStartPadding
/** Hit strip for Android-style swipe-left back gesture. */
private val RightEdgeBackWidth = 24.dp
private val RailDotRadius = 1.25.dp
private val RailDotStep = 4.dp
/** Loading pill matches cache-dot diameter. */
private val RailBarWidth = RailDotRadius * 2
private val RailCurrentBarWidth = 7.dp
private const val RailForceRegenHoldMs = 3_000L
/** Loading solid → cache window (dots) after Edge audio lands. */
private const val RailReadyRevealMs = 1_500
/** Behind (before) cache dots — neutral gray. Ahead uses soft accent (secondary). */
private val RailBehindDot = Color(0xFF7A7A7A)

/** Now-playing snippet card when the spoken block is scrolled out of view. */
private enum class PlaybackPinEdge { Top, Bottom }

private val PinPadDefault = 24.dp
/** Clears the title banner when chrome is open. */
private val PinPadBelowTitleChrome = 128.dp
/** Clears the media controls when chrome is open. */
private val PinPadAboveMediaChrome = 132.dp

/** One TTS sentence in a block, with char offsets into the displayed paragraph text. */
private data class BlockSentence(
    val index: Int,
    val start: Int,
    val end: Int,
)

private data class FilterEditorSession(
    val scope: FilterScope,
    val rule: FilterRule,
    val isNew: Boolean,
)

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    queId: String? = null,
    themeMode: ThemeMode,
    accentHue: Float,
    fontScale: Float,
    fontFamily: ReaderFont,
    lineSpacing: Float,
    orientation: ReaderOrientation,
    onTheme: (ThemeMode) -> Unit,
    onAccentHue: (Float) -> Unit,
    onFontScale: (Float) -> Unit,
    onFontFamily: (ReaderFont) -> Unit,
    onLineSpacing: (Float) -> Unit,
    onOrientation: (ReaderOrientation) -> Unit,
    onBack: () -> Unit,
    onAdvanceQue: (bookId: String, queId: String) -> Unit = { _, _ -> },
) {
    val ui by vm.ui.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val doc = ui.doc
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var programmatic by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(ReaderOverlay.Hidden) }
    var filterEditor by remember { mutableStateOf<FilterEditorSession?>(null) }
    /** Skip the next TTS follow-scroll once when we center the list ourselves (double-tap / pin). */
    var suppressFollowScroll by remember { mutableStateOf(false) }
    var restoredScroll by remember(vm.bookId) { mutableStateOf(false) }
    val view = LocalView.current
    var selectionActive by remember { mutableStateOf(false) }
    var selectionEpoch by remember { mutableIntStateOf(0) }
    val textToolbar = remember(view) {
        ReaderTextToolbar(view) { selectionActive = it }
    }
    fun clearTextSelection() {
        textToolbar.hide()
        selectionActive = false
        selectionEpoch++
    }
    val items = doc?.items.orEmpty()
    val allSentences = remember(doc) { doc?.let { SentenceSplitter.split(it) }.orEmpty() }
    /** (chapter, block) → flat item index; the reader looks this up on every frame. */
    val blockIndexOf = remember(doc) {
        buildMap(items.size) {
            items.forEachIndexed { i, item -> put(item.chapterIndex to item.blockIndex, i) }
        }
    }
    // Only computed while paused, and only when the locus actually moves.
    val locusSentenceIndex = remember(allSentences) {
        derivedStateOf {
            if (allSentences.isEmpty()) 0 else SentenceSplitter.indexAt(allSentences, ui.locus)
        }
    }
    val currentSentenceIndex = if (tts.playing && tts.sentence != null) {
        tts.sentenceIndex
    } else {
        locusSentenceIndex.value
    }
    val restoreSystemBars = rememberImmersiveSystemBars()
    val leave = rememberUpdatedState {
        vm.persistNow()
        restoreSystemBars()
        onBack()
    }

    val activeQueId = queId ?: vm.queId
    LaunchedEffect(activeQueId, vm.bookId) {
        if (activeQueId.isNullOrBlank()) return@LaunchedEffect
        vm.tts.bookFinished.collect {
            val next = vm.finishQueAndNext()
            if (next != null) {
                vm.suppressPauseOnClear = true
                onAdvanceQue(next.first, next.second)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) vm.persistNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            vm.persistNow()
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locusIndex by remember(doc) {
        derivedStateOf {
            val s = tts.sentence
            // While TTS is actively following, highlight the spoken sentence.
            // Otherwise prefer the saved/scrolled UI locus so silent reading tracks the viewport.
            if (s != null && tts.playing && tts.following && tts.autoScrollWithTts) {
                blockIndexOf[s.chapterIndex to s.blockIndex] ?: 0
            } else {
                ui.locus.flatIndex(doc ?: return@derivedStateOf 0)
            }
        }
    }

    val chapterIndex = ui.locus.chapterIndex
    val chapterName = doc?.chapters?.getOrNull(chapterIndex)?.title
        ?.ifBlank { null }
        ?: "Chapter ${chapterIndex + 1}"
    val chapters = doc?.chapters?.mapIndexed { i, ch ->
        ch.title.ifBlank { "Chapter ${i + 1}" }
    }.orEmpty()
    val progress = if (items.size <= 1) 0f else locusIndex.toFloat() / items.lastIndex

    /**
     * Where to park the now-playing chip, or null if the spoken block is still on-screen
     * (or nothing is playing).
     */
    val pinEdge by remember(doc) {
        derivedStateOf {
            if (!tts.playing) return@derivedStateOf null
            val s = tts.sentence ?: return@derivedStateOf null
            val idx = blockIndexOf[s.chapterIndex to s.blockIndex] ?: return@derivedStateOf null

            // Subscribe to scroll position (layoutInfo alone can miss some updates).
            listState.firstVisibleItemIndex
            listState.firstVisibleItemScrollOffset

            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null

            val viewStart = info.viewportStartOffset
            val viewEnd = info.viewportEndOffset
            val placed = visible.firstOrNull { it.index == idx }
            if (placed != null) {
                val top = placed.offset
                val bottom = placed.offset + placed.size
                val overlap = minOf(bottom, viewEnd) - maxOf(top, viewStart)
                // Any real intersection counts as on-screen.
                if (overlap > 1) return@derivedStateOf null
                return@derivedStateOf if (bottom <= viewStart) {
                    PlaybackPinEdge.Top
                } else {
                    PlaybackPinEdge.Bottom
                }
            }

            val first = visible.first().index
            val last = visible.last().index
            when {
                idx < first -> PlaybackPinEdge.Top
                idx > last -> PlaybackPinEdge.Bottom
                idx <= (first + last) / 2 -> PlaybackPinEdge.Top
                else -> PlaybackPinEdge.Bottom
            }
        }
    }

    val playbackBlockIndex by remember(doc) {
        derivedStateOf {
            val s = tts.sentence
            if (!tts.playing || s == null) -1 else blockIndexOf[s.chapterIndex to s.blockIndex] ?: -1
        }
    }

    suspend fun centerItem(index: Int) {
        if (items.isEmpty()) return
        val target = index.coerceIn(0, items.lastIndex)
        programmatic = true
        try {
            listState.animateScrollItemToCenter(target)
            // Wait until LazyList reports idle so a cancelled/settling scroll
            // cannot be mistaken for a user fling after we clear [programmatic].
            snapshotFlow { listState.isScrollInProgress }
                .first { !it }
        } finally {
            programmatic = false
        }
    }

    // Jump to the saved locus once the book finishes loading.
    LaunchedEffect(doc, ui.loading) {
        if (doc == null || ui.loading || restoredScroll || items.isEmpty()) return@LaunchedEffect
        val target = ui.locus.flatIndex(doc).coerceIn(0, items.lastIndex)
        programmatic = true
        try {
            listState.scrollItemToCenter(target)
            snapshotFlow { listState.isScrollInProgress }
                .first { !it }
        } finally {
            programmatic = false
            restoredScroll = true
        }
    }

    // Re-center on each spoken sentence while follow mode is on.
    LaunchedEffect(tts.following, tts.playing, tts.sentenceIndex, tts.autoScrollWithTts, restoredScroll) {
        if (!tts.autoScrollWithTts || !tts.following || !tts.playing ||
            items.isEmpty() || !restoredScroll
        ) {
            return@LaunchedEffect
        }
        val s = tts.sentence ?: return@LaunchedEffect
        val target = blockIndexOf[s.chapterIndex to s.blockIndex] ?: return@LaunchedEffect
        if (suppressFollowScroll) {
            suppressFollowScroll = false
            return@LaunchedEffect
        }
        centerItem(target)
    }

    val ttsForScroll = rememberUpdatedState(tts)
    val programmaticForScroll = rememberUpdatedState(programmatic)

    // Only real user gestures break follow. Do not watch isScrollInProgress —
    // follow animations and their cancellation settling look identical and were
    // permanently clearing [following] mid-playback.
    val stopFollowOnUserScroll = remember(vm) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput &&
                    available != Offset.Zero &&
                    !programmaticForScroll.value &&
                    ttsForScroll.value.playing &&
                    ttsForScroll.value.following
                ) {
                    vm.tts.userScrolledAway()
                }
                return Offset.Zero
            }
        }
    }

    // Playback locus is updated by TTS while playing, or by explicit double-tap / TOC — not by scroll.
    LaunchedEffect(tts.sentence, tts.playing) {
        if (!tts.playing) return@LaunchedEffect
        tts.sentence?.let { s ->
            vm.onLocus(Locus(s.chapterIndex, s.blockIndex, s.start))
        }
    }

    fun toggleChrome() {
        overlay = when (overlay) {
            ReaderOverlay.Hidden -> ReaderOverlay.Chrome
            ReaderOverlay.Chrome -> ReaderOverlay.Hidden
            ReaderOverlay.Settings, ReaderOverlay.Toc -> ReaderOverlay.Hidden
        }
    }

    fun navigateBack() {
        when {
            selectionActive -> clearTextSelection()
            filterEditor != null -> filterEditor = null
            overlay == ReaderOverlay.Settings ||
                overlay == ReaderOverlay.Toc ||
                overlay == ReaderOverlay.Chrome -> overlay = ReaderOverlay.Hidden
            else -> leave.value()
        }
    }

    fun resumeFollow() {
        suppressFollowScroll = true
        vm.tts.followAgain()
        val target = playbackBlockIndex
        if (target >= 0) {
            scope.launch { centerItem(target) }
        }
    }

    /** All reader gesture outcomes go through here so handlers share one policy. */
    fun dispatch(action: ReaderTouchAction) {
        when (action) {
            ReaderTouchAction.ClearSelection -> clearTextSelection()
            ReaderTouchAction.ToggleChrome -> toggleChrome()
            ReaderTouchAction.DismissOverlay -> overlay = ReaderOverlay.Hidden
            ReaderTouchAction.NavigateBack -> navigateBack()
            ReaderTouchAction.ResumeFollow -> resumeFollow()
            ReaderTouchAction.DoubleTapPlay -> Unit // handled at the Text call site
        }
    }

    fun onReaderGesture(
        target: ReaderTouchTarget,
        kind: ReaderGestureKind,
        onDoubleTapPlay: (() -> Unit)? = null,
    ) {
        when (val action = resolveTouch(target, kind, overlay, selectionActive)) {
            null -> Unit
            ReaderTouchAction.DoubleTapPlay -> onDoubleTapPlay?.invoke()
            else -> dispatch(action)
        }
    }

    BackHandler { navigateBack() }

    val colors = MaterialTheme.colorScheme
    val typeface = when (fontFamily) {
        ReaderFont.Sans -> FontFamily.SansSerif
        ReaderFont.Serif -> FontFamily.Serif
        ReaderFont.Mono -> FontFamily.Monospace
    }
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = typeface,
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale * lineSpacing,
    )
    val headingStyle = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = typeface,
        fontSize = MaterialTheme.typography.headlineSmall.fontSize * fontScale,
        lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * fontScale * lineSpacing,
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
            doc != null -> {
                val fadePx = with(LocalDensity.current) { EdgeFade.toPx() }
                val rightEdgePx = with(LocalDensity.current) { RightEdgeBackWidth.toPx() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(overlay, selectionActive) {
                            detectRightEdgeBackSwipe(rightEdgePx) {
                                onReaderGesture(
                                    ReaderTouchTarget.RightEdgeBack,
                                    ReaderGestureKind.SwipeBack,
                                )
                            }
                        },
                ) {
                CompositionLocalProvider(LocalTextToolbar provides textToolbar) {
                    key(selectionEpoch) {
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(selectionActive) {
                                    if (!selectionActive) return@pointerInput
                                    detectSelectionCancelGestures(
                                        onCancel = {
                                            dispatch(ReaderTouchAction.ClearSelection)
                                        },
                                    )
                                },
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .nestedScroll(stopFollowOnUserScroll)
                                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                    .drawWithContent {
                                        drawContent()
                                        val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Transparent,
                                                stop to Color.Black,
                                                1f - stop to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                                contentPadding = PaddingValues(
                                    start = 0.dp,
                                    end = ReaderListEndPadding,
                                    top = 28.dp,
                                    bottom = 28.dp,
                                ),
                            ) {
                                itemsIndexed(items, key = { _, it -> it.block.id }) { index, item ->
                                    val active = index == locusIndex
                                    val sentence = tts.sentence
                                    val highlight = active && sentence != null &&
                                        sentence.chapterIndex == item.chapterIndex &&
                                        sentence.blockIndex == item.blockIndex &&
                                        tts.playing
                                    val blockSentences = remember(allSentences, item.chapterIndex, item.blockIndex) {
                                        allSentences.mapIndexedNotNull { si, s ->
                                            if (s.chapterIndex == item.chapterIndex && s.blockIndex == item.blockIndex) {
                                                BlockSentence(si, s.start, s.end)
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    var textLayout by remember(item.block.id) { mutableStateOf<TextLayoutResult?>(null) }
                                    val replacedRanges = ui.replacedRangesByBlockId[item.block.id].orEmpty()
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
                                        if (highlight) {
                                            val start = sentence!!.start.coerceIn(0, raw.length)
                                            val end = sentence.end.coerceIn(start, raw.length)
                                            if (start < end) {
                                                addStyle(
                                                    SpanStyle(
                                                        background = Color(0x66FFC107),
                                                        fontWeight = FontWeight.Medium,
                                                    ),
                                                    start,
                                                    end,
                                                )
                                            }
                                        }
                                    }
                                    val style = when (item.block.kind) {
                                        BlockKind.Heading -> headingStyle
                                        BlockKind.Quote, BlockKind.Paragraph -> bodyStyle
                                    }
                                    // Gaps: single-tap chrome / clear. Double-tap play stays on Text.
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pointerInput(overlay, selectionActive) {
                                                detectTapGestures(
                                                    onTap = {
                                                        onReaderGesture(
                                                            ReaderTouchTarget.BodyGap,
                                                            ReaderGestureKind.SingleTap,
                                                        )
                                                    },
                                                )
                                            },
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(IntrinsicSize.Min)
                                                .padding(vertical = 10.dp),
                                        ) {
                                            LocusRail(
                                                modifier = Modifier
                                                    .width(RailGutterWidth)
                                                    .fillMaxHeight(),
                                                sentences = blockSentences,
                                                textLayout = textLayout,
                                                currentSentenceIndex = currentSentenceIndex,
                                                ready = tts.readySentenceIndices,
                                                generating = tts.generatingSentenceIndices,
                                                softAccent = colors.secondary,
                                                onForceRegenerate = { vm.tts.forceRegenerateSentence(it) },
                                            )
                                            Text(
                                                text,
                                                style = style,
                                                color = colors.onBackground,
                                                onTextLayout = { textLayout = it },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .padding(end = ReaderTextEndPadding)
                                                    .pointerInput(
                                                        blockSentences,
                                                        overlay,
                                                        tts.doubleTapPlay,
                                                        selectionActive,
                                                        index,
                                                    ) {
                                                        detectTapGestures(
                                                            onTap = {
                                                                onReaderGesture(
                                                                    ReaderTouchTarget.BodyText,
                                                                    ReaderGestureKind.SingleTap,
                                                                )
                                                            },
                                                            onDoubleTap = { pos ->
                                                                onReaderGesture(
                                                                    ReaderTouchTarget.BodyText,
                                                                    ReaderGestureKind.DoubleTap,
                                                                ) {
                                                                    val layout = textLayout
                                                                    val sentence = if (layout != null) {
                                                                        sentenceAtPosition(
                                                                            blockSentences,
                                                                            layout,
                                                                            pos,
                                                                        )
                                                                    } else {
                                                                        blockSentences.firstOrNull()
                                                                    }
                                                                    val charOffset = sentence?.start ?: 0
                                                                    suppressFollowScroll = true
                                                                    vm.jumpTo(
                                                                        Locus(
                                                                            item.chapterIndex,
                                                                            item.blockIndex,
                                                                            charOffset,
                                                                        ),
                                                                    )
                                                                    if (tts.doubleTapPlay) {
                                                                        vm.tts.play()
                                                                    }
                                                                    scope.launch { centerItem(index) }
                                                                    if (overlay == ReaderOverlay.Hidden) {
                                                                        toggleChrome()
                                                                    }
                                                                }
                                                            },
                                                        )
                                                    },
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                if (overlay == ReaderOverlay.Hidden || overlay == ReaderOverlay.Chrome) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .pointerInput(overlay, selectionActive) {
                                detectTapGestures(
                                    onTap = {
                                        onReaderGesture(
                                            ReaderTouchTarget.EdgeBand,
                                            ReaderGestureKind.SingleTap,
                                        )
                                    },
                                )
                            },
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .pointerInput(overlay, selectionActive) {
                                detectTapGestures(
                                    onTap = {
                                        onReaderGesture(
                                            ReaderTouchTarget.EdgeBand,
                                            ReaderGestureKind.SingleTap,
                                        )
                                    },
                                )
                            },
                    )
                }

                TitleBannerCard(
                    visible = overlay == ReaderOverlay.Chrome,
                    title = ui.title,
                    chapter = chapterName,
                    progress = progress,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    onBack = { leave.value() },
                    onToc = { overlay = ReaderOverlay.Toc },
                )

                MediaControlCard(
                    visible = overlay == ReaderOverlay.Chrome,
                    playing = tts.playing,
                    error = tts.error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    onPlay = { vm.tts.play() },
                    onPause = { vm.tts.pause() },
                    onPrev = { vm.tts.skipPrev() },
                    onNext = { vm.tts.skipNext() },
                    onSettings = { overlay = ReaderOverlay.Settings },
                )
                }
            }
        }

        SettingsOverlay(
            visible = overlay == ReaderOverlay.Settings && filterEditor == null,
            themeMode = themeMode,
            accentHue = accentHue,
            fontScale = fontScale,
            fontFamily = fontFamily,
            lineSpacing = lineSpacing,
            orientation = orientation,
            engineKey = tts.engineKey,
            voiceId = tts.voiceId,
            engines = tts.engines,
            voices = tts.voices,
            speed = tts.speed,
            pitch = tts.pitch,
            prefetchCount = tts.prefetchCount,
            doubleTapPlay = tts.doubleTapPlay,
            autoScrollWithTts = tts.autoScrollWithTts,
            filtersGlobal = ui.filtersGlobal,
            filtersGroups = ui.filtersGroups,
            filtersLocal = ui.filtersLocal,
            onTheme = onTheme,
            onAccentHue = onAccentHue,
            onFontScale = onFontScale,
            onFontFamily = onFontFamily,
            onLineSpacing = onLineSpacing,
            onOrientation = onOrientation,
            onEngine = { vm.tts.setEngine(it) },
            onVoice = { vm.tts.setVoice(it) },
            onSpeed = { vm.tts.setSpeed(it) },
            onPitch = { vm.tts.setPitch(it) },
            onPrefetchCount = { vm.tts.setPrefetchCount(it) },
            onDoubleTapPlay = { vm.tts.setDoubleTapPlay(it) },
            onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
            onAddFilter = { scope ->
                filterEditor = FilterEditorSession(
                    scope = scope,
                    rule = FilterRule(),
                    isNew = true,
                )
            },
            onEditFilter = { scope, rule ->
                filterEditor = FilterEditorSession(
                    scope = scope,
                    rule = rule,
                    isNew = false,
                )
            },
            onSetFilterEnabled = { scope, id, enabled ->
                vm.setFilterEnabled(scope, id, enabled)
            },
            onDismiss = { overlay = ReaderOverlay.Hidden },
        )

        val editor = filterEditor
        if (editor != null) {
            FilterRuleEditorOverlay(
                visible = true,
                scope = editor.scope,
                initial = editor.rule,
                sampleSeed = vm.currentBlockSample(),
                isNew = editor.isNew,
                previewApply = { sample, draft, mode ->
                    vm.previewApply(sample, draft, editor.scope, mode)
                },
                onSave = { draft ->
                    if (editor.isNew) vm.addFilter(editor.scope, draft)
                    else vm.updateFilter(editor.scope, draft)
                    filterEditor = null
                },
                onDelete = if (editor.isNew) {
                    null
                } else {
                    {
                        vm.deleteFilter(editor.scope, editor.rule.id)
                        filterEditor = null
                    }
                },
                onSpeak = { vm.tts.speakPreview(it) },
                onDismiss = { filterEditor = null },
            )
        }

        TocOverlay(
            visible = overlay == ReaderOverlay.Toc,
            chapters = chapters,
            chapterIndex = chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)),
            onChapter = { ci ->
                overlay = ReaderOverlay.Hidden
                suppressFollowScroll = true
                vm.jumpToChapter(ci)
                val target = doc?.let { d ->
                    var i = 0
                    for (c in 0 until ci) i += d.chapters[c].blocks.size
                    i
                } ?: 0
                scope.launch { centerItem(target) }
            },
            onDismiss = { overlay = ReaderOverlay.Hidden },
        )

        when (val edge = pinEdge) {
            null -> Unit
            PlaybackPinEdge.Top, PlaybackPinEdge.Bottom -> {
                // pinEdge is non-null only when the spoken block is outside the viewport.
                // User scroll clears follow so auto-center does not pull it back on-screen.
                val showPin = tts.playing &&
                    tts.snippet.isNotBlank() &&
                    overlay != ReaderOverlay.Settings &&
                    overlay != ReaderOverlay.Toc
                if (showPin) {
                    val align = when (edge) {
                        PlaybackPinEdge.Top -> Alignment.TopCenter
                        PlaybackPinEdge.Bottom -> Alignment.BottomCenter
                    }
                    val chromeOpen = overlay == ReaderOverlay.Chrome
                    val edgePad = when {
                        chromeOpen && edge == PlaybackPinEdge.Top -> PinPadBelowTitleChrome
                        chromeOpen && edge == PlaybackPinEdge.Bottom -> PinPadAboveMediaChrome
                        else -> PinPadDefault
                    }
                    ReaderPanelSurface(
                        modifier = Modifier
                            .zIndex(8f)
                            .align(align)
                            .padding(vertical = edgePad)
                            .fillMaxWidth()
                            .pointerInput(playbackBlockIndex, overlay, selectionActive) {
                                detectTapGestures(
                                    onTap = {
                                        onReaderGesture(
                                            ReaderTouchTarget.Pin,
                                            ReaderGestureKind.SingleTap,
                                        )
                                    },
                                    onDoubleTap = {
                                        onReaderGesture(
                                            ReaderTouchTarget.Pin,
                                            ReaderGestureKind.DoubleTap,
                                        )
                                    },
                                )
                            },
                        matchReaderWidth = true,
                    ) {
                        Text(
                            tts.snippet,
                            style = bodyStyle,
                            color = colors.onBackground,
                            maxLines = 3,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

private suspend fun LazyListState.animateScrollItemToCenter(index: Int) {
    bringItemToCenter(index, animated = true)
}

private suspend fun LazyListState.scrollItemToCenter(index: Int) {
    bringItemToCenter(index, animated = false)
}

/**
 * Always targets the vertical middle of the viewport.
 * Never leaves the user on LazyList's default "item at top" alignment.
 */
private suspend fun LazyListState.bringItemToCenter(index: Int, animated: Boolean) {
    val alreadyVisible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!alreadyVisible) {
        // Place on-screen first, then correct to center (animated when requested).
        scrollToItem(index)
        val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
        val delta = centerDelta(item)
        if (kotlin.math.abs(delta) <= 1f) return
        if (animated) {
            animateScrollBy(delta)
        } else {
            scroll { scrollBy(delta) }
        }
        return
    }

    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = centerDelta(item)
    if (kotlin.math.abs(delta) <= 2f) return
    if (animated) {
        animateScrollBy(delta)
    } else {
        scroll { scrollBy(delta) }
    }
}

private fun LazyListState.centerDelta(item: LazyListItemInfo): Float {
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    val itemCenter = item.offset + item.size / 2
    return (itemCenter - viewport / 2).toFloat()
}

private fun sentenceAtPosition(
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

/**
 * Hold without letting LazyColumn steal the gesture after touch-slop.
 * Consumes small moves inside the rail hit target for [holdMs], then fires [onHold].
 */
private suspend fun PointerInputScope.detectRailHold(
    holdMs: Long,
    onHold: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val holdSlop = viewConfiguration.touchSlop * 2f
        // Completes early on release or drag; null means the finger outlasted [holdMs].
        val heldToTimeout = withTimeoutOrNull(holdMs) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: return@withTimeoutOrNull
                val travel = (change.position - down.position).getDistance()
                if (travel > holdSlop) return@withTimeoutOrNull
                // Consume so the parent LazyColumn does not start a scroll.
                change.consume()
                if (!change.pressed) return@withTimeoutOrNull
            }
        } == null
        if (heldToTimeout) {
            onHold()
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                event.changes.forEach { it.consume() }
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) break
            }
        }
    }
}

@Composable
private fun LocusRail(
    modifier: Modifier,
    sentences: List<BlockSentence>,
    textLayout: TextLayoutResult?,
    currentSentenceIndex: Int,
    ready: Set<Int>,
    generating: Set<Int>,
    softAccent: Color,
    onForceRegenerate: (Int) -> Unit,
) {
    if (sentences.isEmpty()) {
        Spacer(modifier)
        return
    }

    val farthestGenerating = generating.maxOrNull()
    val transition = rememberInfiniteTransition(label = "rail-pulse")
    // Composing the animation only while something is generating keeps the per-frame
    // clock off entirely during normal reading.
    val pulse = if (generating.isEmpty()) {
        0f
    } else {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "rail-pulse-t",
        ).value
    }
    val density = LocalDensity.current
    val onForceRegenerateState = rememberUpdatedState(onForceRegenerate)

    fun segmentGenerating(index: Int): Boolean = index in generating
    fun segmentReady(index: Int): Boolean =
        index in ready && index !in generating && index != currentSentenceIndex
    fun canForceRegenerate(index: Int): Boolean =
        index in ready && index !in generating

    /** One shared absolute-Y grid; windows only reveal it (lapping won't densify). */
    fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCacheDotWindows(
        windows: List<Pair<Float, Float>>,
        color: Color,
    ) {
        if (windows.isEmpty()) return
        val step = RailDotStep.toPx()
        val r = RailDotRadius.toPx()
        val cx = size.width * 0.5f
        val phase = step * 0.5f
        for ((top, bottom) in windows) {
            if (bottom <= top) continue
            clipRect(left = 0f, top = top, right = size.width, bottom = bottom) {
                var y = phase + ceil((top - phase) / step).toInt() * step
                while (y < bottom) {
                    drawCircle(color = color, radius = r, center = Offset(cx, y))
                    y += step
                }
            }
        }
    }

    Box(
        modifier.drawBehind {
            val layout = textLayout
            val behind = ArrayList<Pair<Float, Float>>()
            val ahead = ArrayList<Pair<Float, Float>>()
            if (layout != null && layout.layoutInput.text.isNotEmpty()) {
                val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
                for (s in sentences) {
                    if (!segmentReady(s.index)) continue
                    val start = s.start.coerceIn(0, lastChar)
                    val endInclusive = (s.end - 1).coerceIn(start, lastChar)
                    val top = layout.getLineTop(layout.getLineForOffset(start))
                    val bottom = layout.getLineBottom(layout.getLineForOffset(endInclusive))
                    if (s.index > currentSentenceIndex) ahead += top to bottom
                    else behind += top to bottom
                }
            } else {
                val h = size.height / sentences.size.coerceAtLeast(1)
                for ((i, s) in sentences.withIndex()) {
                    if (!segmentReady(s.index)) continue
                    val top = i * h
                    val bottom = (i + 1) * h
                    if (s.index > currentSentenceIndex) ahead += top to bottom
                    else behind += top to bottom
                }
            }
            // Before = gray; after = soft accent (scheme secondary).
            drawCacheDotWindows(behind, RailBehindDot)
            drawCacheDotWindows(ahead, softAccent)
        },
    ) {
        val layout = textLayout
        // Farthest from current drawn first; current gets zIndex 0 (top) when bars lap.
        val drawOrder = remember(sentences, currentSentenceIndex) {
            sentences.sortedByDescending { abs(it.index - currentSentenceIndex) }
        }
        if (layout == null || layout.layoutInput.text.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                for (s in sentences) {
                    key(s.index) {
                        RailSegmentMark(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .zIndex(-abs(s.index - currentSentenceIndex).toFloat()),
                            isCurrent = s.index == currentSentenceIndex,
                            isGenerating = segmentGenerating(s.index),
                            isReady = segmentReady(s.index),
                            emphasizePulse = s.index == farthestGenerating ||
                                (segmentGenerating(s.index) && s.index == currentSentenceIndex),
                            pulse = pulse,
                            canForceRegenerate = canForceRegenerate(s.index),
                            onForceRegenerate = { onForceRegenerateState.value(s.index) },
                        )
                    }
                }
            }
            return@Box
        }

        val lastChar = (layout.layoutInput.text.length - 1).coerceAtLeast(0)
        for (s in drawOrder) {
            val start = s.start.coerceIn(0, lastChar)
            val endInclusive = (s.end - 1).coerceIn(start, lastChar)
            // Same geometry the amber SpanStyle highlight uses: line boxes for the char range.
            val top = layout.getLineTop(layout.getLineForOffset(start))
            val bottom = layout.getLineBottom(layout.getLineForOffset(endInclusive))
            val heightPx = (bottom - top).coerceAtLeast(1f)
            val dist = abs(s.index - currentSentenceIndex)
            key(s.index) {
                RailSegmentMark(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .zIndex(-dist.toFloat())
                        .offset { IntOffset(0, top.roundToInt()) }
                        .width(RailGutterWidth)
                        .height(with(density) { heightPx.toDp() }),
                    isCurrent = s.index == currentSentenceIndex,
                    isGenerating = segmentGenerating(s.index),
                    isReady = segmentReady(s.index),
                    emphasizePulse = s.index == farthestGenerating ||
                        (segmentGenerating(s.index) && s.index == currentSentenceIndex),
                    pulse = pulse,
                    canForceRegenerate = canForceRegenerate(s.index),
                    onForceRegenerate = { onForceRegenerateState.value(s.index) },
                )
            }
        }
    }
}

@Composable
private fun RailSegmentMark(
    modifier: Modifier,
    isCurrent: Boolean,
    isGenerating: Boolean,
    isReady: Boolean,
    emphasizePulse: Boolean,
    pulse: Float,
    canForceRegenerate: Boolean,
    onForceRegenerate: () -> Unit,
) {
    val railCurrent = MaterialTheme.colorScheme.primary
    val edge = MaterialTheme.colorScheme.background
    val onForceRegenerateState = rememberUpdatedState(onForceRegenerate)
    // 1 = solid loading cover; 0 = fully revealed cache window underneath.
    val reveal = remember { Animatable(0f) }

    LaunchedEffect(isGenerating, isReady, isCurrent) {
        when {
            isGenerating -> reveal.snapTo(1f)
            isCurrent -> reveal.snapTo(0f)
            isReady -> {
                if (reveal.value > 0f) {
                    reveal.animateTo(
                        0f,
                        tween(RailReadyRevealMs, easing = FastOutSlowInEasing),
                    )
                }
            }
            else -> reveal.snapTo(0f)
        }
    }

    val pulseAmount = if (emphasizePulse) pulse else pulse * 0.45f
    val loadingColor = lerp(RailLoading, RailLoadingBright, pulseAmount)
    val showCurrentBar = isCurrent && !isGenerating
    val showLoadingCover = !isCurrent && reveal.value > 0.001f
    val barWidth = if (showCurrentBar) RailCurrentBarWidth else RailBarWidth
    val holdModifier = if (canForceRegenerate) {
        Modifier.pointerInput(canForceRegenerate) {
            detectRailHold(RailForceRegenHoldMs) {
                onForceRegenerateState.value()
            }
        }
    } else {
        Modifier
    }

    val barColor = when {
        showCurrentBar -> railCurrent
        isGenerating -> loadingColor
        showLoadingCover -> RailLoading.copy(alpha = reveal.value)
        else -> null
    }

    Box(modifier.then(holdModifier), contentAlignment = Alignment.Center) {
        val color = barColor ?: return@Box
        Box(
            Modifier
                .width(barWidth)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(barWidth / 2))
                .drawBehind {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to edge,
                            0.20f to color,
                            0.80f to color,
                            1f to edge,
                        ),
                    )
                },
        )
    }
}
/** Hides the system bars for as long as it stays composed; returns a restore callback for leave. */
@Composable
private fun rememberImmersiveSystemBars(): () -> Unit {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.systemBarsBehavior
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previous
        }
    }
    return remember(view) {
        {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
