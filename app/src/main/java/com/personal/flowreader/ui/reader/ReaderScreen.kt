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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.chrome.ReaderContentStartPadding
import com.personal.flowreader.ui.chrome.ReaderPanelFeather
import com.personal.flowreader.ui.chrome.ReaderPanelSurface
import com.personal.flowreader.ui.settings.FilterRuleEditorOverlay
import com.personal.flowreader.ui.settings.SettingsOverlay
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.chrome.ReaderListEndPadding
import com.personal.flowreader.ui.settings.FilterEditorSession
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Loading pulse — independent of accent. */
private val RailLoading = Color(0xFF6A6A6A)
private val RailLoadingBright = Color(0xFF8A8A8A)
/** Scroll fade mask height — gesture/visual constant, not on spacing ramp. */
private val EdgeFade = 96.dp
/** Full left margin from screen edge to text; bars are centered in this gutter. */
private val RailGutterWidth = ReaderContentStartPadding
/** Hit strip for Android-style swipe-left back gesture. */
private val RightEdgeBackWidth = 24.dp
private val RailDotRadius = 1.25.dp
private val RailDotStep = FlowTokens.Space.XS
/** Loading pill matches cache-dot diameter. */
private val RailBarWidth = RailDotRadius * 2
private val RailCurrentBarWidth = 7.dp
private const val RailForceRegenHoldMs = 3_000L
/** Loading solid → cache window (dots) after Edge audio lands. */
private const val RailReadyRevealMs = 1_500
/** Behind (before) cache dots — neutral gray. Ahead uses soft accent (secondary). */
private val RailBehindDot = FlowTokens.NeutralCacheGray
/** LazyColumn top/bottom content pad (16 + 12). */
private val ReaderListVerticalPad = FlowTokens.Space.L + FlowTokens.Space.M
/** Horizontal inset for TTS highlight pills (matches typical line-box pad). */
private val HighlightSidePad = 4.dp
/** Edge-band tap targets — gesture constants, not on spacing ramp. */
private val EdgeBandTopHeight = 56.dp
private val EdgeBandBottomHeight = 72.dp

/** Now-playing snippet card when the spoken block is scrolled out of view. */
private enum class PlaybackPinEdge { Top, Bottom }

/** Gap between stacked chrome / pin cards — matches reader side gutters. */
private val PinGap = ReaderContentStartPadding
private val ChromeScreenPad = ReaderContentStartPadding
/**
 * Each [ReaderPanelSurface] reserves [ReaderPanelFeather] above and below the solid card.
 * When stacking two feathered cards, collapse both feathers in *layout* (not [Modifier.offset])
 * so solid borders sit [PinGap] apart without leaving empty space under a bottom-aligned stack.
 */
private val ChromeStackFeatherCollapse = ReaderPanelFeather * 2

private fun Modifier.chromeStackCollapse(enabled: Boolean): Modifier {
    if (!enabled) return this
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val pull = ChromeStackFeatherCollapse.roundToPx()
        val height = (placeable.height - pull).coerceAtLeast(0)
        layout(placeable.width, height) {
            placeable.placeRelative(0, -pull)
        }
    }
}

/** One TTS sentence in a block, with char offsets into the displayed paragraph text. */
private data class BlockSentence(
    val index: Int,
    val start: Int,
    val end: Int,
)

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    queId: String? = null,
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    onBack: () -> Unit,
    onAdvanceQue: (bookId: String, queId: String) -> Unit = { _, _ -> },
) {
    val themeMode = appearance.themeMode
    val accentHue = appearance.accentHue
    val uiScale = appearance.uiScale
    val fontScale = appearance.fontScale
    val fontFamily = appearance.fontFamily
    val lineSpacing = appearance.lineSpacing
    val justifyText = appearance.justifyText
    val orientation = appearance.orientation
    val showChapterHeadingsInBody = appearance.showChapterHeadingsInBody
    val keepScreenAwake = appearance.keepScreenAwake
    val onTheme = appearanceCallbacks.onTheme
    val onAccentHue = appearanceCallbacks.onAccentHue
    val onUiScale = appearanceCallbacks.onUiScale
    val onFontScale = appearanceCallbacks.onFontScale
    val onFontFamily = appearanceCallbacks.onFontFamily
    val onLineSpacing = appearanceCallbacks.onLineSpacing
    val onJustifyText = appearanceCallbacks.onJustifyText
    val onOrientation = appearanceCallbacks.onOrientation
    val onShowChapterHeadingsInBody = appearanceCallbacks.onShowChapterHeadingsInBody
    val onKeepScreenAwake = appearanceCallbacks.onKeepScreenAwake
    val ui by vm.ui.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val doc = ui.doc
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var programmatic by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(ReaderOverlay.Hidden) }
    var filterEditor by remember { mutableStateOf<FilterEditorSession?>(null) }
    /** Follow TTS scroll, hide chrome, and ignore taps until unlocked. */
    var scrollLocked by remember { mutableStateOf(false) }
    /** Skip the next TTS follow-scroll once when we center the list ourselves (double-tap / pin). */
    var suppressFollowScroll by remember { mutableStateOf(false) }
    var restoredScroll by remember(vm.bookId) { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(keepScreenAwake, view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }
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
    val items = remember(doc, showChapterHeadingsInBody) {
        doc?.readingItems(includeChapterTitles = showChapterHeadingsInBody).orEmpty()
    }
    val allSentences = remember(doc) { doc?.let { SentenceSplitter.split(it) }.orEmpty() }
    /** (chapter, block) → flat item index; the reader looks this up on every frame. */
    val blockIndexOf = remember(items) {
        buildMap(items.size) {
            items.forEachIndexed { i, item ->
                if (!item.isChapterTitle) put(item.chapterIndex to item.blockIndex, i)
            }
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
            if (s != null && tts.playing && (
                    scrollLocked || (tts.following && tts.autoScrollWithTts)
                )
            ) {
                blockIndexOf[s.chapterIndex to s.blockIndex] ?: 0
            } else {
                blockIndexOf[ui.locus.chapterIndex to ui.locus.blockIndex] ?: 0
            }
        }
    }

    val chapterIndex = ui.tocIndex
    val chapterName = ui.tocTitles.getOrNull(chapterIndex)
        ?.ifBlank { null }
        ?: doc?.chapters?.getOrNull(ui.locus.chapterIndex)?.title?.ifBlank { null }
        ?: "Chapter ${chapterIndex + 1}"
    val chapters = ui.tocTitles.ifEmpty {
        doc?.chapters?.mapIndexed { i, ch ->
            ch.title.ifBlank { "Chapter ${i + 1}" }
        }.orEmpty()
    }
    val progress = if (items.size <= 1) 0f else locusIndex.toFloat() / items.lastIndex

    /**
     * Where to park the edge chip (now-playing snippet or jump-back), or null if the
     * target block is still on-screen.
     */
    val pinEdge by remember(doc, items, blockIndexOf) {
        derivedStateOf {
            val idx = if (tts.playing) {
                val s = tts.sentence ?: return@derivedStateOf null
                blockIndexOf[s.chapterIndex to s.blockIndex] ?: return@derivedStateOf null
            } else {
                blockIndexOf[ui.locus.chapterIndex to ui.locus.blockIndex]
                    ?: return@derivedStateOf null
            }

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

    fun jumpToSavedPosition() {
        if (items.isEmpty()) return
        scope.launch { centerItem(locusIndex.coerceIn(0, items.lastIndex)) }
    }

    /**
     * Resume TTS at the saved playhead. If that block is off-screen, keep the viewport
     * where it is (now-playing pin will show) instead of auto-scrolling to it.
     */
    fun playResumingSavedPosition() {
        val playheadOffScreen = pinEdge != null
        if (playheadOffScreen) suppressFollowScroll = true
        vm.tts.play(follow = !playheadOffScreen)
    }

    // Jump to the saved locus once the book finishes loading.
    LaunchedEffect(doc, ui.loading) {
        if (doc == null || ui.loading || restoredScroll || items.isEmpty()) return@LaunchedEffect
        val target = (blockIndexOf[ui.locus.chapterIndex to ui.locus.blockIndex] ?: 0)
            .coerceIn(0, items.lastIndex)
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

    // Re-center on each spoken sentence while follow mode is on (or scroll-locked).
    LaunchedEffect(
        tts.following,
        tts.playing,
        tts.sentenceIndex,
        tts.autoScrollWithTts,
        scrollLocked,
        restoredScroll,
    ) {
        val followScroll = scrollLocked || (tts.autoScrollWithTts && tts.following)
        if (!followScroll || !tts.playing || items.isEmpty() || !restoredScroll) {
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
    val scrollLockedForScroll = rememberUpdatedState(scrollLocked)

    // Only real user gestures break follow. Do not watch isScrollInProgress —
    // follow animations and their cancellation settling look identical and were
    // permanently clearing [following] mid-playback.
    val stopFollowOnUserScroll = remember(vm) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (scrollLockedForScroll.value) return Offset.Zero
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
        if (scrollLocked) return
        overlay = when (overlay) {
            ReaderOverlay.Hidden -> ReaderOverlay.Chrome
            ReaderOverlay.Chrome -> ReaderOverlay.Hidden
            ReaderOverlay.Settings, ReaderOverlay.Toc -> ReaderOverlay.Hidden
        }
    }

    fun navigateBack() {
        when {
            scrollLocked -> scrollLocked = false
            selectionActive -> clearTextSelection()
            filterEditor != null -> filterEditor = null
            overlay == ReaderOverlay.Settings ||
                overlay == ReaderOverlay.Toc ||
                overlay == ReaderOverlay.Chrome -> overlay = ReaderOverlay.Hidden
            else -> leave.value()
        }
    }

    fun enableScrollLock() {
        scrollLocked = true
        overlay = ReaderOverlay.Hidden
        filterEditor = null
        val target = playbackBlockIndex
        if (target >= 0) {
            scope.launch { centerItem(target) }
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
        if (scrollLocked) return
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
    // Material bodyLarge defaults to 0.5.sp letterSpacing. With TextAlign.Justify,
    // Compose still reserves that trailing per-glyph spacing on each line, so the
    // glyphs stop ~n*ls short of the right edge (looks like extra right padding).
    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = typeface,
        fontSize = MaterialTheme.typography.bodyLarge.fontSize * fontScale,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * fontScale * lineSpacing,
        textAlign = if (justifyText) TextAlign.Justify else TextAlign.Start,
        letterSpacing = if (justifyText) 0.sp else MaterialTheme.typography.bodyLarge.letterSpacing,
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
                                userScrollEnabled = !scrollLocked,
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
                                    start = FlowTokens.Radius.None,
                                    end = ReaderListEndPadding,
                                    top = ReaderListVerticalPad,
                                    bottom = ReaderListVerticalPad,
                                ),
                            ) {
                                itemsIndexed(items, key = { _, it -> it.block.id }) { index, item ->
                                    val active = index == locusIndex
                                    val sentence = tts.sentence
                                    val highlight = active && sentence != null &&
                                        sentence.chapterIndex == item.chapterIndex &&
                                        sentence.blockIndex == item.blockIndex &&
                                        tts.playing
                                    val sentenceRange: IntRange? =
                                        if (highlight) sentence!!.start until sentence.end else null
                                    val wordRange: IntRange? = when {
                                        !highlight -> null
                                        else -> {
                                            val spoken = sentence!!
                                            val word = tts.wordHighlight
                                            if (word != null &&
                                                word.first >= spoken.start &&
                                                word.last < spoken.end
                                            ) {
                                                word
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    val blockSentences = remember(allSentences, item.chapterIndex, item.blockIndex) {
                                        allSentences.mapIndexedNotNull { si, s ->
                                            if (s.chapterIndex == item.chapterIndex && s.blockIndex == item.blockIndex) {
                                                BlockSentence(si, s.start, s.end)
                                            } else {
                                                null
                                            }
                                        }
                                    }
                                    var textLayout by remember(item.block.id, justifyText) {
                                        mutableStateOf<TextLayoutResult?>(null)
                                    }
                                    val replacedRanges = ui.replacedRangesByBlockId[item.block.id].orEmpty()
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
                                    // Gaps: single-tap chrome / clear. Double-tap play stays on Text.
                                    // Rail occupies the left gutter; list end pad is the right gutter —
                                    // same ContentStart/ContentEnd the chrome cards use.
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
                                                .padding(vertical = FlowTokens.Space.M),
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
                                                    .fillMaxWidth()
                                                    .drawBehind {
                                                        val layout = textLayout ?: return@drawBehind
                                                        val radius = 6.dp.toPx()
                                                        val pad = HighlightSidePad.toPx()
                                                        sentenceRange?.let {
                                                            drawTtsHighlightRange(
                                                                layout,
                                                                it,
                                                                sentenceHighlightColor,
                                                                pad,
                                                                radius,
                                                            )
                                                        }
                                                        wordRange?.let {
                                                            drawTtsHighlightRange(
                                                                layout,
                                                                it,
                                                                wordHighlightColor,
                                                                pad,
                                                                radius,
                                                            )
                                                        }
                                                    }
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
                                                                    if (item.isChapterTitle) {
                                                                        suppressFollowScroll = true
                                                                        vm.jumpTo(
                                                                            Locus(item.chapterIndex, 0, 0),
                                                                        )
                                                                        if (tts.doubleTapPlay) {
                                                                            vm.tts.play()
                                                                        }
                                                                        scope.launch { centerItem(index) }
                                                                        if (overlay == ReaderOverlay.Hidden) {
                                                                            toggleChrome()
                                                                        }
                                                                        return@onReaderGesture
                                                                    }
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
                                        Spacer(Modifier.height(FlowTokens.Space.XS))
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
                            .height(EdgeBandTopHeight)
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
                            .height(EdgeBandBottomHeight)
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

                val overlaysQuiet = !scrollLocked &&
                    overlay != ReaderOverlay.Settings &&
                    overlay != ReaderOverlay.Toc
                val showPlayingPin = overlaysQuiet &&
                    tts.playing &&
                    tts.snippet.isNotBlank() &&
                    pinEdge != null
                val showJumpChip = overlaysQuiet &&
                    !tts.playing &&
                    pinEdge != null
                val edgeChipTop = (showPlayingPin || showJumpChip) && pinEdge == PlaybackPinEdge.Top
                val edgeChipBottom = (showPlayingPin || showJumpChip) && pinEdge == PlaybackPinEdge.Bottom
                val pinWordRange = run {
                    val spoken = tts.sentence ?: return@run null
                    val word = tts.wordHighlight ?: return@run null
                    if (word.first < spoken.start || word.last >= spoken.end) return@run null
                    val localStart = word.first - spoken.start
                    val localEndExclusive = word.last + 1 - spoken.start
                    if (localStart >= localEndExclusive) null
                    else localStart until localEndExclusive
                }
                val chromeOpen = !scrollLocked && overlay == ReaderOverlay.Chrome

                // Top chrome layer: title + optional edge chip, stacked with a relative [PinGap].
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .zIndex(8f)
                        .padding(top = ChromeScreenPad),
                ) {
                    TitleBannerCard(
                        visible = chromeOpen,
                        title = ui.title,
                        chapter = chapterName,
                        progress = progress,
                        storedPath = ui.storedPath,
                        modifier = Modifier.fillMaxWidth(),
                        onBack = { leave.value() },
                        onSettings = { overlay = ReaderOverlay.Settings },
                    )
                    if (edgeChipTop) {
                        if (chromeOpen) Spacer(Modifier.height(PinGap))
                        val chipMod = Modifier
                            .fillMaxWidth()
                            .chromeStackCollapse(chromeOpen)
                        if (showPlayingPin) {
                            PlaybackPinCard(
                                snippet = tts.snippet,
                                bodyStyle = bodyStyle,
                                wordRangeInSnippet = pinWordRange,
                                modifier = chipMod,
                                onTap = {
                                    onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.SingleTap)
                                },
                                onDoubleTap = {
                                    onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.DoubleTap)
                                },
                            )
                        } else {
                            JumpToSavedChip(
                                modifier = chipMod,
                                onClick = { jumpToSavedPosition() },
                            )
                        }
                    }
                }

                // Bottom chrome layer: optional edge chip + media, stacked with a relative [PinGap].
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(8f)
                        .padding(bottom = ChromeScreenPad),
                ) {
                    if (edgeChipBottom) {
                        val chipMod = Modifier.fillMaxWidth()
                        if (showPlayingPin) {
                            PlaybackPinCard(
                                snippet = tts.snippet,
                                bodyStyle = bodyStyle,
                                wordRangeInSnippet = pinWordRange,
                                modifier = chipMod,
                                onTap = {
                                    onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.SingleTap)
                                },
                                onDoubleTap = {
                                    onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.DoubleTap)
                                },
                            )
                        } else {
                            JumpToSavedChip(
                                modifier = chipMod,
                                onClick = { jumpToSavedPosition() },
                            )
                        }
                        if (chromeOpen) Spacer(Modifier.height(PinGap))
                    }
                    MediaControlCard(
                        visible = chromeOpen,
                        playing = tts.playing,
                        error = tts.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .chromeStackCollapse(edgeChipBottom && chromeOpen),
                        onPlay = { playResumingSavedPosition() },
                        onPause = { vm.tts.pause() },
                        onPrev = { vm.tts.skipPrev() },
                        onNext = { vm.tts.skipNext() },
                        onToc = { overlay = ReaderOverlay.Toc },
                        onScrollLock = { enableScrollLock() },
                    )
                }

                ScrollLockUnlockButton(
                    visible = scrollLocked,
                    onUnlock = { scrollLocked = false },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .zIndex(9f)
                        .padding(bottom = ChromeScreenPad),
                )
                }
            }
        }

        SettingsOverlay(
            visible = overlay == ReaderOverlay.Settings && filterEditor == null,
            appearance = appearance,
            appearanceCallbacks = appearanceCallbacks,
            tts = TtsSettingsState(
                engineKey = tts.engineKey,
                voiceId = tts.voiceId,
                engines = tts.engines,
                voices = tts.voices,
                speed = tts.speed,
                pitch = tts.pitch,
                prefetchCount = tts.prefetchCount,
                doubleTapPlay = tts.doubleTapPlay,
                autoScrollWithTts = tts.autoScrollWithTts,
                keepAliveUnderlay = tts.keepAliveUnderlay,
                sentenceGapMs = tts.sentenceGapMs,
                highlightSyncMs = tts.highlightSyncMs,
            ),
            ttsCallbacks = TtsSettingsCallbacks(
                onEngine = { vm.tts.setEngine(it) },
                onVoice = { vm.tts.setVoice(it) },
                onSpeed = { vm.tts.setSpeed(it) },
                onPitch = { vm.tts.setPitch(it) },
                onPrefetchCount = { vm.tts.setPrefetchCount(it) },
                onDoubleTapPlay = { vm.tts.setDoubleTapPlay(it) },
                onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
                onKeepAliveUnderlay = { vm.tts.setKeepAliveUnderlay(it) },
                onSentenceGapMs = { vm.tts.setSentenceGapMs(it) },
                onHighlightSyncMs = { vm.tts.setHighlightSyncMs(it) },
            ),
            filters = FilterSettingsState(
                filtersGlobal = ui.filtersGlobal,
                filtersGroups = ui.filtersGroups,
                filtersLocal = ui.filtersLocal,
                filterScopes = listOf(
                    FilterScope.Global,
                    FilterScope.Groups,
                    FilterScope.Local,
                ),
            ),
            filterCallbacks = FilterSettingsCallbacks(
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
            ),
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
                scope.launch {
                    vm.jumpToChapter(ci)
                    // After RR seek the loaded stream starts at relative 0; otherwise map ToC → list.
                    val rel = vm.ui.value.locus.chapterIndex
                    val target = items.indexOfFirst { it.chapterIndex == rel }
                        .takeIf { it >= 0 }
                        ?: 0
                    centerItem(target)
                }
            },
            onDismiss = { overlay = ReaderOverlay.Hidden },
        )
    }
}

@Composable
private fun JumpToSavedChip(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        ReaderPanelSurface(
            modifier = Modifier.clickable(onClick = onClick),
            matchReaderWidth = false,
        ) {
            Text(
                "Jump back to saved position",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(
                    horizontal = FlowTokens.Space.M,
                    vertical = FlowTokens.Space.S,
                ),
            )
        }
    }
}

@Composable
private fun PlaybackPinCard(
    snippet: String,
    bodyStyle: TextStyle,
    wordRangeInSnippet: IntRange?,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val sentenceHighlightColor = colors.secondary.copy(alpha = 0.40f)
    val wordHighlightColor = colors.primary.copy(alpha = 0.40f)
    val sentenceRange = 0 until snippet.length
    var textLayout by remember(snippet, bodyStyle.textAlign, bodyStyle.letterSpacing) {
        mutableStateOf<TextLayoutResult?>(null)
    }
    ReaderPanelSurface(
        modifier = modifier.pointerInput(snippet) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { onDoubleTap() },
            )
        },
        matchReaderWidth = true,
        borderColor = colors.secondary,
    ) {
        Text(
            snippet,
            style = bodyStyle,
            color = colors.onBackground,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .padding(
                    horizontal = FlowTokens.Space.L,
                    vertical = FlowTokens.Space.M,
                )
                .drawBehind {
                    val layout = textLayout ?: return@drawBehind
                    val radius = 6.dp.toPx()
                    val pad = HighlightSidePad.toPx()
                    drawTtsHighlightRange(layout, sentenceRange, sentenceHighlightColor, pad, radius)
                    wordRangeInSnippet?.let {
                        drawTtsHighlightRange(layout, it, wordHighlightColor, pad, radius)
                    }
                },
        )
    }
}

/** Rounded TTS highlight pills; clamps to visible lines (e.g. pin card maxLines). */
private fun DrawScope.drawTtsHighlightRange(
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
        val activity = view.context as? Activity
        if (activity != null) {
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            val previous = controller.systemBarsBehavior
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            onDispose {
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = previous
            }
        } else {
            onDispose { }
        }
    }
    return remember(view) {
        {
            val activity = view.context as? Activity
            if (activity != null) {
                WindowCompat.getInsetsController(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
