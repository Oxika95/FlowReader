package com.personal.flowreader.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
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
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.settings.FilterRuleEditorOverlay
import com.personal.flowreader.ui.settings.SettingsOverlay
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.settings.FilterEditorSession
import com.personal.flowreader.ui.design.card.FlowFloatingCard
import com.personal.flowreader.ui.design.card.FlowFloatingChip
import com.personal.flowreader.ui.design.layer.DockEdge
import com.personal.flowreader.ui.design.layer.FlowDock
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Scroll fade mask height — gesture/visual constant, not on spacing ramp. */
private val EdgeFade = 96.dp
/** Full left margin from screen edge to text; bars are centered in this gutter. */
internal val RailGutterWidth = ReaderContentStartPadding
/** Hit strip for Android-style swipe-left back gesture. */
private val RightEdgeBackWidth = 24.dp
/** LazyColumn top/bottom content pad (16 + 12). */
private val ReaderListVerticalPad = FlowTokens.Space.L + FlowTokens.Space.M
/** Horizontal inset for TTS highlight pills (matches typical line-box pad). */
private val HighlightSidePad = 4.dp
/** Edge-band tap targets — gesture constants, not on spacing ramp. */
private val EdgeBandTopHeight = 56.dp
private val EdgeBandBottomHeight = 72.dp

/** Give up waiting for the tracked sentence's geometry and fall back to its paragraph. */
private const val HomeSpanTimeoutMs = 750L

/** One TTS sentence in a block, with char offsets into the displayed paragraph text. */
internal data class BlockSentence(
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
    /** Root-Y lines of the tracked sentence, written from layout while its paragraph is composed. */
    var trackedSpan by remember { mutableStateOf<SentenceSpan?>(null) }
    var listTopY by remember { mutableFloatStateOf(0f) }
    var listHeight by remember { mutableFloatStateOf(0f) }
    val homePosition by rememberUpdatedState(appearance.homePosition)
    val view = LocalView.current
    DisposableEffect(keepScreenAwake, view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = keepScreenAwake
        onDispose { view.keepScreenOn = previous }
    }
    var selectionActive by remember { mutableStateOf(false) }
    var selectionEpoch by remember { mutableIntStateOf(0) }
    var bookCardRequests by remember { mutableIntStateOf(0) }
    val textToolbar = remember(view) {
        ReaderTextToolbar(
            view,
            onSelectionUiChanged = { selectionActive = it },
            onFilter = { text ->
                selectionEpoch++
                filterEditor = FilterEditorSession(
                    scope = FilterScope.Local,
                    rule = FilterRule(pattern = text),
                    isNew = true,
                )
            },
        )
    }
    fun clearTextSelection() {
        textToolbar.hide()
        selectionActive = false
        selectionEpoch++
    }
    val items = remember(doc, showChapterHeadingsInBody) {
        doc?.readingItems(includeChapterTitles = showChapterHeadingsInBody).orEmpty()
    }
    val allSentences = ui.sentences
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
            allSentences.indexAt(ui.locus)
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
        vm.queueAdvanced.collect { if (it.fromQueId == activeQueId) onAdvanceQue(it.bookId, it.queId) }
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
    val progress = ui.fraction

    /**
     * Where to park the edge chip (now-playing snippet or jump-back), or null while any line of
     * the tracked sentence is on-screen. Sentence-level: a visible paragraph whose tracked
     * sentence is scrolled past still shows the chip.
     */
    val pinEdge by remember(doc, items, blockIndexOf, allSentences) {
        derivedStateOf {
            val si = if (tts.playing) {
                if (tts.sentence == null) return@derivedStateOf null
                tts.sentenceIndex
            } else {
                locusSentenceIndex.value
            }
            val s = allSentences.getOrNull(si) ?: return@derivedStateOf null
            val idx = blockIndexOf[s.chapterIndex to s.blockIndex] ?: return@derivedStateOf null

            // Subscribe to scroll position (layoutInfo alone can miss some updates).
            listState.firstVisibleItemIndex
            listState.firstVisibleItemScrollOffset

            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@derivedStateOf null
            val placed = visible.firstOrNull { it.index == idx }
                ?: return@derivedStateOf ReaderHome.edgeOfItem(idx, visible.first().index, visible.last().index)
            val span = trackedSpan?.takeIf { it.sentenceIndex == si }
            if (span != null) {
                ReaderHome.edgeOf(span.top, span.bottom, listTopY, listTopY + listHeight)
            } else {
                // Geometry not reported yet: judge by the paragraph until the rail lays out.
                val top = (placed.offset - info.viewportStartOffset).toFloat()
                ReaderHome.edgeOf(top, top + placed.size, 0f, (info.viewportEndOffset - info.viewportStartOffset).toFloat())
            }
        }
    }

    /** Spoken sentence while playing, otherwise the saved locus sentence. */
    fun trackedSentenceIndex(): Int =
        if (tts.playing && tts.sentence != null) tts.sentenceIndex else locusSentenceIndex.value

    val homeLookup = rememberUpdatedState(allSentences to blockIndexOf)

    /**
     * Scroll so [sentenceIndex] sits on the home line. Every jump (follow, pin, jump-back,
     * double-tap, scroll lock, ToC, book open) goes through here.
     */
    suspend fun scrollToHome(sentenceIndex: Int, animated: Boolean = true) {
        val (sentences, blocks) = homeLookup.value
        val s = sentences.getOrNull(sentenceIndex) ?: return
        val itemIndex = blocks[s.chapterIndex to s.blockIndex] ?: return
        programmatic = true
        try {
            if (listState.layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) {
                // A span left over from the paragraph's previous composition is stale.
                trackedSpan = null
                listState.scrollToItem(itemIndex)
            }
            val span = withTimeoutOrNull(HomeSpanTimeoutMs) {
                snapshotFlow { trackedSpan }.first { it != null && it.sentenceIndex == sentenceIndex }
            }
            val delta = if (span != null) {
                ReaderHome.scrollDelta(span.top, span.bottom, listTopY, listHeight, homePosition)
            } else {
                val info = listState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == itemIndex } ?: return
                val top = (item.offset - info.viewportStartOffset).toFloat()
                val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                ReaderHome.scrollDelta(top, top + item.size, 0f, viewport, homePosition)
            }
            if (abs(delta) > 2f) {
                if (animated) listState.animateScrollBy(delta) else listState.scroll { scrollBy(delta) }
            }
            // Wait for idle so a settling scroll is not mistaken for a user fling.
            snapshotFlow { listState.isScrollInProgress }.first { !it }
        } finally {
            programmatic = false
        }
    }

    fun jumpToSavedPosition() {
        if (items.isEmpty()) return
        scope.launch { scrollToHome(trackedSentenceIndex()) }
    }

    /**
     * Resume TTS at the saved playhead. If that sentence is off-screen, keep the viewport
     * where it is (now-playing pin will show) instead of auto-scrolling to it.
     */
    fun playResumingSavedPosition() {
        val playheadOffScreen = pinEdge != null
        if (playheadOffScreen) suppressFollowScroll = true
        vm.tts.play(follow = !playheadOffScreen)
    }

    // Put the current sentence home once the book finishes loading.
    LaunchedEffect(doc, ui.loading) {
        if (doc == null || ui.loading || restoredScroll || items.isEmpty()) return@LaunchedEffect
        try {
            scrollToHome(trackedSentenceIndex(), animated = false)
        } finally {
            restoredScroll = true
        }
    }

    // Re-home on each spoken sentence while follow mode is on (or scroll-locked).
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
        if (tts.sentence == null) return@LaunchedEffect
        if (suppressFollowScroll) {
            suppressFollowScroll = false
            return@LaunchedEffect
        }
        scrollToHome(tts.sentenceIndex)
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

    LaunchedEffect(listState, items) {
        snapshotFlow {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.let { items.getOrNull(it.index) } ?: return@snapshotFlow null
            val last = visible.lastOrNull()?.let { items.getOrNull(it.index) } ?: return@snapshotFlow null
            first.chapterIndex to last.chapterIndex
        }.collect { range -> range?.let { vm.onViewport(it.first, it.second) } }
    }

    fun toggleChrome() {
        if (scrollLocked) return
        overlay = when (overlay) {
            ReaderOverlay.Hidden -> ReaderOverlay.Chrome
            ReaderOverlay.Chrome -> ReaderOverlay.Hidden
            ReaderOverlay.Settings, ReaderOverlay.Toc -> ReaderOverlay.Hidden
        }
    }

    fun enableScrollLock() {
        scrollLocked = true
        overlay = ReaderOverlay.Hidden
        filterEditor = null
        if (selectionActive) clearTextSelection()
        if (tts.playing && tts.sentence != null) {
            scope.launch { scrollToHome(tts.sentenceIndex) }
        }
    }

    fun disableScrollLock() {
        scrollLocked = false
        overlay = ReaderOverlay.Chrome
    }

    fun navigateBack() {
        when {
            scrollLocked -> disableScrollLock()
            selectionActive -> clearTextSelection()
            filterEditor != null -> filterEditor = null
            overlay == ReaderOverlay.Settings ||
                overlay == ReaderOverlay.Toc ||
                overlay == ReaderOverlay.Chrome -> overlay = ReaderOverlay.Hidden
            else -> leave.value()
        }
    }

    fun resumeFollow() {
        // Only when follow actually turns on: the follow effect re-runs and consumes the flag.
        val live = vm.tts.state.value
        if (live.playing && live.autoScrollWithTts && !live.following) suppressFollowScroll = true
        vm.tts.followAgain()
        scope.launch { scrollToHome(trackedSentenceIndex()) }
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
                        ReaderSelectionContainer(
                            enabled = !scrollLocked,
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
                                    .onGloballyPositioned { coords ->
                                        listTopY = coords.positionInRoot().y
                                        listHeight = coords.size.height.toFloat()
                                    }
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
                                        allSentences.inBlock(item.chapterIndex, item.blockIndex).mapNotNull { si ->
                                            allSentences.getOrNull(si)?.let { s -> BlockSentence(si, s.start, s.end) }
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
                                                onCurrentSpan = { trackedSpan = it },
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
                                                                        val target = Locus(item.chapterIndex, 0, 0)
                                                                        suppressFollowScroll = true
                                                                        vm.jumpTo(target)
                                                                        if (tts.doubleTapPlay) {
                                                                            vm.tts.play()
                                                                        }
                                                                        scope.launch {
                                                                            scrollToHome(allSentences.indexAt(target))
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
                                                                    val live = vm.tts.state.value
                                                                    if (live.playing && live.sentence != null &&
                                                                        sentence?.index == live.sentenceIndex
                                                                    ) {
                                                                        // Already speaking it: re-home and follow, don't restart.
                                                                        resumeFollow()
                                                                        return@onReaderGesture
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
                                                                    val homeIndex = sentence?.index
                                                                        ?: allSentences.indexAt(
                                                                            Locus(item.chapterIndex, item.blockIndex, charOffset),
                                                                        )
                                                                    scope.launch { scrollToHome(homeIndex) }
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

                if (appearance.showHomeMarker && !scrollLocked) {
                    HomeMarker(
                        position = appearance.homePosition,
                        textSize = bodyStyle.fontSize,
                        onPositionChange = { appearanceCallbacks.onHomePosition(it, false) },
                        onPositionCommitted = { position ->
                            appearanceCallbacks.onHomePosition(position, true)
                            // Settle the current sentence on the new line if the reader is on it.
                            if (pinEdge == null) {
                                val target = trackedSentenceIndex()
                                scope.launch {
                                    withFrameNanos { }
                                    scrollToHome(target)
                                }
                            }
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
                val edgeChip: @Composable () -> Unit = {
                    if (showPlayingPin) {
                        PlaybackPinCard(
                            snippet = tts.snippet,
                            bodyStyle = bodyStyle,
                            wordRangeInSnippet = pinWordRange,
                            onTap = { onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.SingleTap) },
                            onDoubleTap = { onReaderGesture(ReaderTouchTarget.Pin, ReaderGestureKind.DoubleTap) },
                        )
                    } else {
                        FlowFloatingChip("Jump back to saved position", onClick = { jumpToSavedPosition() })
                    }
                }

                // Top dock, edge first: title card, then the edge chip when the playhead is above.
                FlowDock(edge = DockEdge.Top, modifier = Modifier.align(Alignment.TopCenter)) {
                    Item(visible = chromeOpen) {
                        TitleBannerCard(
                            title = ui.title,
                            chapter = chapterName,
                            progress = progress,
                            storedPath = ui.storedPath,
                            onBack = { leave.value() },
                            onSettings = { overlay = ReaderOverlay.Settings },
                            onLongPress = { bookCardRequests++ },
                        )
                    }
                    Item(visible = edgeChipTop) { edgeChip() }
                }

                // Bottom dock, top to bottom: edge chip, then the media card (or the unlock control).
                FlowDock(edge = DockEdge.Bottom, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Item(visible = edgeChipBottom) { edgeChip() }
                    Item(visible = chromeOpen) {
                        MediaControlCard(
                            playing = tts.playing,
                            onPlay = { playResumingSavedPosition() },
                            onPause = { vm.tts.pause() },
                            onPrev = { vm.tts.skipPrev() },
                            onNext = { vm.tts.skipNext() },
                            onToc = { overlay = ReaderOverlay.Toc },
                            onScrollLock = { enableScrollLock() },
                        )
                    }
                    Item(visible = scrollLocked) {
                        ScrollLockControls(
                            playing = tts.playing,
                            onPlay = { vm.tts.play() },
                            onPause = { vm.tts.pause() },
                            onUnlock = { disableScrollLock() },
                        )
                    }
                }
                }
            }
        }

        SettingsOverlay(
            visible = overlay == ReaderOverlay.Settings,
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
                clipTargetChars = tts.clipTargetChars,
                clipFlexChars = tts.clipFlexChars,
                doubleTapPlay = tts.doubleTapPlay,
                mobileDataFallback = tts.mobileDataFallback,
                autoScrollWithTts = tts.autoScrollWithTts,
                minSignal = tts.minSignal,
                underlayBtAddress = tts.underlayBtAddress,
                underlayBtName = tts.underlayBtName,
                underlayBtConnected = tts.underlayBtConnected,
                sentenceGapMs = tts.sentenceGapMs,
                highlightSyncMs = tts.highlightSyncMs,
            ),
            ttsCallbacks = TtsSettingsCallbacks(
                onEngine = { vm.tts.setEngine(it) },
                onVoice = { vm.tts.setVoice(it) },
                onSpeed = { vm.tts.setSpeed(it) },
                onPitch = { vm.tts.setPitch(it) },
                onPrefetchCount = { vm.tts.setPrefetchCount(it) },
                onClipTargetChars = { vm.tts.setClipTargetChars(it) },
                onClipFlexChars = { vm.tts.setClipFlexChars(it) },
                onDoubleTapPlay = { vm.tts.setDoubleTapPlay(it) },
                onMobileDataFallback = { vm.tts.setMobileDataFallback(it) },
                onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
                onMinSignal = { level, persist -> vm.tts.setMinSignal(level, persist) },
                onUnderlayBtDevice = { address, name -> vm.tts.setUnderlayBtDevice(address, name) },
                underlayBondedDevices = { vm.tts.underlayBondedDevices() },
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
                onReorderFilters = { scope, ids -> vm.reorderFilters(scope, ids) },
                onDeleteFilters = { scope, ids -> vm.deleteFilters(scope, ids) },
            ),
            debugEnabled = appearance.debugEnabled,
            onDebugEnabled = appearanceCallbacks.onDebugEnabled,
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
                onSpeak = { vm.tts.speakPreview(it) },
                onDismiss = { filterEditor = null },
            )
        }

        ReaderBookCard(bookId = vm.bookId, openRequests = bookCardRequests)

        TocOverlay(
            visible = overlay == ReaderOverlay.Toc,
            chapters = chapters,
            chapterIndex = chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)),
            onChapter = { ci ->
                overlay = ReaderOverlay.Hidden
                suppressFollowScroll = true
                scope.launch {
                    vm.jumpToChapter(ci)
                    // A chapter outside the window swaps the doc; let the sentence lookup recompose first.
                    withFrameNanos { }
                    val sentences = homeLookup.value.first
                    if (sentences.isNotEmpty()) {
                        scrollToHome(sentences.indexAt(vm.ui.value.locus))
                    }
                }
            },
            onDismiss = { overlay = ReaderOverlay.Hidden },
        )
    }
}

/** Now-playing snippet as a floating card; tap / double-tap route through the reader gesture policy. */
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
    FlowFloatingCard(
        modifier = modifier.pointerInput(snippet) {
            detectTapGestures(
                onTap = { onTap() },
                onDoubleTap = { onDoubleTap() },
            )
        },
        contentPadding = PaddingValues(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.M),
        borderColor = colors.secondary,
    ) {
        Text(
            snippet,
            style = bodyStyle,
            color = colors.onBackground,
            onTextLayout = { textLayout = it },
            modifier = Modifier.drawBehind {
                val layout = textLayout ?: return@drawBehind
                val radius = FlowTokens.HighlightRadius.toPx()
                val pad = HighlightSidePad.toPx()
                drawTtsHighlightRange(layout, sentenceRange, sentenceHighlightColor, pad, radius)
                wordRangeInSnippet?.let {
                    drawTtsHighlightRange(layout, it, wordHighlightColor, pad, radius)
                }
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
