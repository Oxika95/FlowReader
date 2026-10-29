package com.personal.flowreader.ui.reader

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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
import androidx.recyclerview.widget.RecyclerView
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.BookPlace
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.Locus
import com.personal.flowreader.debug.DebugLog
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
/** Reader body list top/bottom content pad (16 + 12). */
private val ReaderListVerticalPad = FlowTokens.Space.L + FlowTokens.Space.M
/** Horizontal inset for TTS highlight pills (matches typical line-box pad). */
internal val HighlightSidePad = 4.dp
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
    appearance: AppearanceSettingsState,
    appearanceCallbacks: AppearanceSettingsCallbacks,
    onBack: () -> Unit,
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
    val body = remember { ReaderBodyController() }
    val scope = rememberCoroutineScope()
    /** True while [scrollToHome] is moving the list; layout must not write [viewPlace]. */
    var homing by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(ReaderOverlay.Hidden) }
    var filterEditor by remember { mutableStateOf<FilterEditorSession?>(null) }
    /** Follow TTS scroll, hide chrome, and ignore taps until unlocked. */
    var scrollLocked by remember { mutableStateOf(false) }
    /** Skip the next TTS follow-scroll once when we center the list ourselves (double-tap / pin). */
    var suppressFollowScroll by remember { mutableStateOf(false) }
    var restoredScroll by remember(vm.bookId) { mutableStateOf(false) }
    /**
     * Ephemeral free-scroll [BookPlace]. Null = lock-step with the reading/spoken place.
     * Never persisted; cleared on jump-back / follow / any home jump.
     */
    var viewPlace by remember { mutableStateOf<BookPlace?>(null) }
    /** Bumped on body scroll so pin/edge derived state recomputes. */
    var scrollEpoch by remember { mutableIntStateOf(0) }
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
    // Queue: each item's title heads its document in the stream.
    val items = remember(doc, showChapterHeadingsInBody, ui.queue) {
        doc?.readingItems(includeChapterTitles = showChapterHeadingsInBody || ui.queue).orEmpty()
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
                blockIndexOf[s.chapterIndex to s.blockIndex] ?: -1
            } else {
                blockIndexOf[ui.locus.chapterIndex to ui.locus.blockIndex] ?: -1
            }
        }
    }

    val chapterIndex = ui.tocIndex
    val chapterName = ui.headerChapter
        ?: ui.tocTitles.getOrNull(chapterIndex)
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
    val pinEdge by remember(doc, items, blockIndexOf, allSentences, scrollEpoch) {
        derivedStateOf {
            scrollEpoch // dependency
            val si = if (tts.playing) {
                if (tts.sentence == null) return@derivedStateOf null
                tts.sentenceIndex
            } else {
                locusSentenceIndex.value
            }
            val s = allSentences.getOrNull(si) ?: return@derivedStateOf null
            val idx = blockIndexOf[s.chapterIndex to s.blockIndex] ?: return@derivedStateOf null
            val key = items.getOrNull(idx)?.block?.id ?: return@derivedStateOf null

            val first = body.firstVisibleAdapterIndex()
            val last = body.lastVisibleAdapterIndex()
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
                return@derivedStateOf null
            }
            val bounds = body.rowWindowBounds(idx)
            if (bounds == null) {
                return@derivedStateOf ReaderHome.edgeOfItem(idx, first, last)
            }
            val span = trackedSpan?.takeIf { it.sentenceIndex == si && it.blockId == key }
            if (span != null) {
                ReaderHome.edgeOf(span.top, span.bottom, listTopY, listTopY + listHeight)
            } else {
                ReaderHome.edgeOf(bounds.first, bounds.second, listTopY, listTopY + listHeight)
            }
        }
    }

    /** Spoken sentence while playing, otherwise the saved locus sentence. */
    fun trackedSentenceIndex(): Int =
        if (tts.playing && tts.sentence != null) tts.sentenceIndex else locusSentenceIndex.value

    val homeLookup = rememberUpdatedState(Triple(allSentences, blockIndexOf, items))

    /**
     * One camera move: put the target sentence center on the home bar Y.
     * Math first ([ReaderHome.scrollDelta]), then a single [ReaderBodyController.scrollBy].
     */
    suspend fun anchorPlaceAtHome(
        place: BookPlace,
        key: String,
        sentenceIndex: Int,
    ): Int {
        fun resolveIndex(): Int = body.adapterIndexOf(place)
        fun refreshListBox() {
            listTopY = body.viewTopInWindow().toFloat()
            listHeight = body.viewHeight().toFloat()
        }
        /** Span for [sentenceIndex] whose top is unchanged across two frames. */
        suspend fun awaitStableSpan(): SentenceSpan? = withTimeoutOrNull(HomeSpanTimeoutMs) {
            var lastTop = Float.NaN
            var stable: SentenceSpan? = null
            while (stable == null) {
                if (!body.isListCommitted()) body.awaitListCommitted()
                val span = trackedSpan
                if (span != null && span.blockId == key && span.sentenceIndex == sentenceIndex) {
                    if (!lastTop.isNaN() && abs(span.top - lastTop) < 1f) {
                        stable = span
                    } else {
                        lastTop = span.top
                    }
                } else {
                    lastTop = Float.NaN
                }
                withFrameNanos { }
            }
            stable
        }

        withTimeoutOrNull(HomeSpanTimeoutMs) { body.awaitListCommitted() }
        if (resolveIndex() < 0 || body.blockIdAt(resolveIndex()) != key) {
            DebugLog.reader("scrollToHome key=$key miss=noIndexAfterCommit")
            return 0
        }
        refreshListBox()
        if (listHeight <= 0f) {
            DebugLog.reader("scrollToHome key=$key miss=noViewport")
            return 0
        }

        // Far jump: row not composed — one LM scroll that parks row top on home (single move).
        // On-screen paths never enter here; they only scrollBy below.
        val index = resolveIndex()
        if (body.rowWindowBounds(index) == null && trackedSpan?.let {
                it.blockId == key && it.sentenceIndex == sentenceIndex
            } == null
        ) {
            val offset = ReaderHome.homePadOffset(listHeight, homePosition, body.padTopPx)
            body.scrollToPlace(place, offsetPx = offset)
            body.awaitScrollIdle(HomeSpanTimeoutMs)
            DebugLog.reader("scrollToHome park si=$sentenceIndex delta=$offset via=place")
            return offset
        }

        body.stopScroll()
        val span = awaitStableSpan()
        if (span == null) {
            DebugLog.reader("scrollToHome key=$key si=$sentenceIndex miss=noSpan")
            return 0
        }
        refreshListBox()
        val delta = ReaderHome.scrollDelta(span.top, span.bottom, listTopY, listHeight, homePosition)
        if (abs(delta) > 2f) {
            body.scrollBy(delta.toInt())
            body.awaitScrollIdle(HomeSpanTimeoutMs)
        }
        DebugLog.reader("scrollToHome park si=$sentenceIndex delta=${delta.toInt()}")
        return delta.toInt()
    }

    suspend fun scrollToHome(sentenceIndex: Int, reason: String, animated: Boolean = true) {
        val (sentences, blocks, listItems) = homeLookup.value
        val s = sentences.getOrNull(sentenceIndex) ?: run {
            DebugLog.reader("scrollToHome reason=$reason si=$sentenceIndex miss=noSentence")
            return
        }
        val place = BookPlace(s.chapterIndex, s.blockIndex, s.start)
        val key = blocks[s.chapterIndex to s.blockIndex]?.let { listItems.getOrNull(it)?.block?.id } ?: run {
            DebugLog.reader(
                "scrollToHome reason=$reason si=$sentenceIndex c=${s.chapterIndex} b=${s.blockIndex} miss=noKey",
            )
            return
        }
        val startIndex = body.adapterIndexOf(place).takeIf { it >= 0 }
            ?: ReaderHome.placeToAdapterIndex(listItems, place)
        if (startIndex < 0) {
            DebugLog.reader("scrollToHome reason=$reason key=$key miss=noIndex")
            return
        }
        val wasPlaced = body.rowWindowBounds(startIndex) != null
        DebugLog.reader(
            "scrollToHome start reason=$reason si=$sentenceIndex key=$key home=$homePosition placed=$wasPlaced",
        )
        viewPlace = null
        homing = true
        try {
            val delta = anchorPlaceAtHome(place, key, sentenceIndex)
            DebugLog.reader(
                "scrollToHome done reason=$reason si=$sentenceIndex key=$key home=$homePosition delta=$delta",
            )
        } finally {
            homing = false
            viewPlace = null
        }
    }

    fun jumpToSavedPosition() {
        if (items.isEmpty()) return
        scope.launch { scrollToHome(trackedSentenceIndex(), reason = "jumpBack") }
    }

    /**
     * Resume TTS at the saved playhead. If that sentence is off-screen, keep the viewport
     * where it is (now-playing pin will show) instead of auto-scrolling to it.
     */
    fun playResumingSavedPosition() {
        val playheadOffScreen = pinEdge != null
        if (playheadOffScreen) {
            suppressFollowScroll = true
            DebugLog.reader("suppressFollow set reason=playOffScreen")
        }
        vm.tts.play(follow = !playheadOffScreen)
    }

    // Put the current sentence home once the book finishes loading.
    LaunchedEffect(doc, ui.loading) {
        if (doc == null || ui.loading || restoredScroll || items.isEmpty()) return@LaunchedEffect
        try {
            scrollToHome(trackedSentenceIndex(), reason = "restore", animated = false)
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
            DebugLog.reader("suppressFollow consume si=${tts.sentenceIndex}")
            return@LaunchedEffect
        }
        scrollToHome(tts.sentenceIndex, reason = if (scrollLocked) "lockFollow" else "follow")
    }

    val ttsForScroll = rememberUpdatedState(tts)
    val scrollLockedForScroll = rememberUpdatedState(scrollLocked)
    val homingForView = rememberUpdatedState(homing)

    // Playback BookPlace is updated by TTS while playing, or by explicit double-tap / TOC — not by scroll.
    LaunchedEffect(tts.sentence, tts.playing) {
        if (!tts.playing) return@LaunchedEffect
        tts.sentence?.let { s ->
            vm.onSpoken(BookPlace(s.chapterIndex, s.blockIndex, s.start))
        }
    }

    // Follow lock-step: view place stays cleared so the list tracks the reading/spoken place.
    val followScrollActive = scrollLocked || (tts.playing && tts.following && tts.autoScrollWithTts)
    LaunchedEffect(followScrollActive) {
        if (followScrollActive) {
            viewPlace = null
        }
    }

    fun onBodyUserScroll() {
        if (scrollLockedForScroll.value) return
        val live = ttsForScroll.value
        if (live.playing && live.following) {
            DebugLog.reader("userScrolledAway")
            vm.tts.userScrolledAway()
        }
    }

    fun onBodyViewPlace(place: BookPlace) {
        scrollEpoch++
        listTopY = body.viewTopInWindow().toFloat()
        listHeight = body.viewHeight().toFloat()
        if (!homingForView.value && !followScrollActive) {
            viewPlace = place
        }
    }

    fun onBodyVisibleChapters(range: IntRange) {
        // Mid-jump list mutations (loadAdjacent) shift adapter indices under scrollToHome.
        if (homingForView.value) {
            DebugLog.reader("viewportSkip homing range=$range")
            return
        }
        vm.onViewport(range.first, range.last)
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
            scope.launch { scrollToHome(tts.sentenceIndex, reason = "scrollLock") }
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
        if (live.playing && live.autoScrollWithTts && !live.following) {
            suppressFollowScroll = true
            DebugLog.reader("suppressFollow set reason=resumeFollow")
        }
        vm.tts.followAgain()
        scope.launch { scrollToHome(trackedSentenceIndex(), reason = "resumeFollow") }
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
                        Box(
                            Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coords ->
                                    listTopY = coords.positionInWindow().y
                                    listHeight = coords.size.height.toFloat()
                                }
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
                                }
                                .pointerInput(selectionActive) {
                                    if (!selectionActive) return@pointerInput
                                    detectSelectionCancelGestures(
                                        onCancel = { dispatch(ReaderTouchAction.ClearSelection) },
                                    )
                                },
                        ) {
                            ReaderBodyList(
                                controller = body,
                                items = items,
                                userScrollEnabled = !scrollLocked,
                                contentPaddingTop = ReaderListVerticalPad,
                                contentPaddingBottom = ReaderListVerticalPad,
                                contentPaddingEnd = ReaderListEndPadding,
                                onUserScroll = { onBodyUserScroll() },
                                onViewPlace = { onBodyViewPlace(it) },
                                onVisibleChapters = { onBodyVisibleChapters(it) },
                                modifier = Modifier.fillMaxSize(),
                            ) { item ->
                                ReaderBodyRow(
                                    item = item,
                                    items = items,
                                    locusIndex = locusIndex,
                                    ttsPlaying = tts.playing,
                                    ttsSentence = tts.sentence,
                                    ttsWordHighlight = tts.wordHighlight,
                                    ttsReady = tts.readySentenceIndices,
                                    ttsGenerating = tts.generatingSentenceIndices,
                                    ttsDoubleTapPlay = tts.doubleTapPlay,
                                    ttsSentenceIndex = tts.sentenceIndex,
                                    allSentences = allSentences,
                                    replacedRangesByBlockId = ui.replacedRangesByBlockId,
                                    colors = colors,
                                    bodyStyle = bodyStyle,
                                    headingStyle = headingStyle,
                                    justifyText = justifyText,
                                    currentSentenceIndex = currentSentenceIndex,
                                    scrollLocked = scrollLocked,
                                    overlay = overlay,
                                    selectionActive = selectionActive,
                                    onTrackedSpan = { trackedSpan = it },
                                    onClearTrackedIf = { id -> if (trackedSpan?.blockId == id) trackedSpan = null },
                                    onGapTap = {
                                        onReaderGesture(ReaderTouchTarget.BodyGap, ReaderGestureKind.SingleTap)
                                    },
                                    onTextTap = {
                                        onReaderGesture(ReaderTouchTarget.BodyText, ReaderGestureKind.SingleTap)
                                    },
                                    onDoubleTapTitle = {
                                        val target = BookPlace(item.chapterIndex, 0, 0)
                                        suppressFollowScroll = true
                                        DebugLog.reader("suppressFollow set reason=doubleTapTitle")
                                        vm.jumpTo(target)
                                        if (tts.doubleTapPlay) vm.tts.play()
                                        scope.launch {
                                            scrollToHome(allSentences.indexAt(target), reason = "doubleTapTitle")
                                        }
                                    },
                                    onDoubleTapBody = { place, homeIndex, alreadySpeaking ->
                                        if (alreadySpeaking) {
                                            resumeFollow()
                                        } else {
                                            suppressFollowScroll = true
                                            DebugLog.reader("suppressFollow set reason=doubleTap")
                                            vm.jumpTo(place)
                                            if (tts.doubleTapPlay) vm.tts.play()
                                            scope.launch { scrollToHome(homeIndex, reason = "doubleTap") }
                                        }
                                    },
                                    onForceRegenerate = { vm.tts.forceRegenerateSentence(it) },
                                    isAlreadySpeaking = { si ->
                                        val live = vm.tts.state.value
                                        live.playing && live.sentence != null && si == live.sentenceIndex
                                    },
                                )
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
                                    scrollToHome(target, reason = "homeMarker")
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
                            onToc = {
                                if (ui.queue) vm.refreshQueue() else vm.refreshContents()
                                overlay = ReaderOverlay.Toc
                            },
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
            onRefreshEngines = { vm.tts.refreshEngines() },
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
                showClipSettings = tts.showClipSettings,
                showNetworkLanes = tts.showNetworkLanes,
                doubleTapPlay = tts.doubleTapPlay,
                mobileDataFallback = tts.mobileDataFallback,
                autoPlayOnShare = tts.autoPlayOnShare,
                shareInterruptsPlayback = tts.shareInterruptsPlayback,
                autoScrollWithTts = tts.autoScrollWithTts,
                underlayMode = tts.underlayMode,
                minSignal = tts.minSignal,
                underlayBtAddress = tts.underlayBtAddress,
                underlayBtName = tts.underlayBtName,
                underlayBtActive = tts.underlayBtActive,
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
                onAutoPlayOnShare = { vm.tts.setAutoPlayOnShare(it) },
                onShareInterruptsPlayback = { vm.tts.setShareInterruptsPlayback(it) },
                onAutoScrollWithTts = { vm.tts.setAutoScrollWithTts(it) },
                onUnderlayMode = { vm.tts.setUnderlayMode(it) },
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

        ReaderBookCard(bookId = ui.currentBookId.ifBlank { vm.bookId }, openRequests = bookCardRequests)

        val onTocRow: (Int) -> Unit = { ci ->
            overlay = ReaderOverlay.Hidden
            suppressFollowScroll = true
            DebugLog.reader("suppressFollow set reason=toc row=$ci")
            scope.launch {
                // Jump path: locus + one home move. Homing blocks viewport while settling.
                homing = true
                try {
                    vm.jumpToChapter(ci)
                    val target = vm.ui.value.locus
                    withTimeoutOrNull(HomeSpanTimeoutMs) {
                        snapshotFlow {
                            ReaderHome.placeToAdapterIndex(homeLookup.value.third, target) >= 0
                        }.first { it }
                        body.awaitListCommitted()
                    }
                    val sentences = homeLookup.value.first
                    if (sentences.isNotEmpty()) {
                        scrollToHome(sentences.indexAt(vm.ui.value.locus), reason = "toc")
                    }
                } finally {
                    homing = false
                }
            }
        }
        if (ui.queue) {
            QueueTocOverlay(
                visible = overlay == ReaderOverlay.Toc,
                items = ui.queueItems,
                currentRow = chapterIndex,
                currentQueId = ui.currentQueId,
                onRow = onTocRow,
                onReorder = vm::reorderQueue,
                onDelete = vm::removeQueue,
                onDismiss = { overlay = ReaderOverlay.Hidden },
            )
        } else {
            TocOverlay(
                visible = overlay == ReaderOverlay.Toc,
                chapters = chapters,
                levels = ui.tocLevels,
                locked = ui.tocLocked,
                downloaded = ui.tocDownloaded,
                chapterIndex = chapterIndex.coerceIn(0, (chapters.size - 1).coerceAtLeast(0)),
                onChapter = onTocRow,
                onDismiss = { overlay = ReaderOverlay.Hidden },
            )
        }
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
