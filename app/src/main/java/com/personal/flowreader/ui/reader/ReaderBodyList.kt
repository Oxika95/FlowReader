package com.personal.flowreader.ui.reader

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import com.personal.flowreader.data.BookPlace
import com.personal.flowreader.data.ReaderItem
import com.personal.flowreader.debug.DebugLog
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reader body list: Compose [LazyColumn] keyed by block id so prepend/trim can keep the
 * on-screen paragraph put. Scroll identity for the app is [BookPlace], not row numbers.
 * Sentence boxes and the home bar share this viewport (top = 0).
 */
internal class ReaderBodyController {
    var listState: LazyListState? = null
        private set
    var items: List<ReaderItem> = emptyList()
        private set
    /** When false, user drag/fling is ignored (scroll lock). */
    var userScrollEnabled: Boolean = true
    /** Top content padding (px). */
    var padTopPx: Int = 0
        private set
    /** Viewport height in px (the box that holds the list + home bar). */
    @Volatile
    var viewportHeightPx: Float = 0f
        private set
    /** Layout coordinates of the viewport box; sentence spans report into this space. */
    @Volatile
    var viewportCoords: LayoutCoordinates? = null

    private var pendingEpoch: Int = 0
    private val _listEpoch = MutableStateFlow(0)
    /** Bumps when [items] are applied and the first frame after has layout info. */
    val listEpoch: StateFlow<Int> = _listEpoch.asStateFlow()

    private var anchorKey: String? = null
    private var anchorOffset: Int = 0

    /** Sentence line boxes in viewport coordinates (top = 0). */
    private val sentenceSpans = ConcurrentHashMap<Int, SentenceSpan>()

    /** Bumped when spans change so pin-edge derived state can recompute. */
    private val _spanEpoch = MutableStateFlow(0)
    val spanEpoch: StateFlow<Int> = _spanEpoch.asStateFlow()

    fun attach(state: LazyListState) {
        listState = state
    }

    fun setPadTopPx(px: Int) {
        padTopPx = px.coerceAtLeast(0)
    }

    fun setViewportHeight(px: Float) {
        viewportHeightPx = px.coerceAtLeast(0f)
    }

    /**
     * Capture the first visible keyed row before [newItems] replace the list so a prepend
     * can restore scroll.
     */
    fun prepareItems(newItems: List<ReaderItem>) {
        if (newItems === items || (newItems.size == items.size && newItems == items)) return
        val first = listState?.layoutInfo?.visibleItemsInfo?.firstOrNull()
        if (first != null) {
            anchorKey = first.key as? String
            anchorOffset = first.offset
        } else {
            anchorKey = null
            anchorOffset = 0
        }
        items = newItems
        pendingEpoch++
    }

    /** After composition with the new [items], restore the anchored row if needed. */
    suspend fun settleAfterItemsChange() {
        val key = anchorKey
        val offset = anchorOffset
        val state = listState
        if (key != null && state != null) {
            val idx = items.indexOfFirst { it.block.id == key }
            if (idx >= 0) {
                val visible = state.layoutInfo.visibleItemsInfo.firstOrNull()
                if (visible == null || visible.key != key || visible.offset != offset) {
                    state.scrollToItem(idx, offset)
                }
            }
        }
        anchorKey = null
        withFrameNanos { }
        _listEpoch.value = pendingEpoch
    }

    fun isListCommitted(): Boolean = _listEpoch.value == pendingEpoch

    suspend fun awaitListCommitted() {
        if (isListCommitted()) return
        val want = pendingEpoch
        listEpoch.first { it >= want && isListCommitted() }
    }

    fun committedItems(): List<ReaderItem> = items

    fun adapterIndexOf(place: BookPlace): Int =
        ReaderHome.placeToAdapterIndex(items, place)

    fun blockIdAt(adapterIndex: Int): String? =
        items.getOrNull(adapterIndex)?.block?.id

    fun placeAt(adapterIndex: Int): BookPlace? {
        val item = items.getOrNull(adapterIndex) ?: return null
        return BookPlace(item.chapterIndex, item.blockIndex, 0)
    }

    fun firstVisibleAdapterIndex(): Int =
        listState?.firstVisibleItemIndex ?: -1

    fun lastVisibleAdapterIndex(): Int =
        listState?.layoutInfo?.visibleItemsInfo?.lastOrNull()?.index ?: -1

    fun firstVisibleBlockId(): String? {
        val i = firstVisibleAdapterIndex()
        if (i < 0) return null
        return blockIdAt(i)
    }

    fun visibleBlockIds(): List<String> {
        val info = listState?.layoutInfo?.visibleItemsInfo ?: return emptyList()
        return info.mapNotNull { blockIdAt(it.index) }
    }

    fun chapterByBlockId(): Map<String, Int> =
        items.associate { it.block.id to it.chapterIndex }

    /** Cancel in-flight user fling; programmatic scrolls use awaitScrollIdle. */
    suspend fun stopScroll() {
        if (listState?.isScrollInProgress == true) {
            listState?.scrollBy(0f)
        }
    }

    /**
     * Bring [place]'s row into view with [offsetPx] from the padding edge (list-local).
     * Used only to compose a far row before reading its sentence box.
     */
    suspend fun scrollToPlace(place: BookPlace, offsetPx: Int = 0) {
        val index = ReaderHome.placeToAdapterIndex(items, place)
        if (index < 0) {
            DebugLog.reader(
                "bodyScroll miss c=${place.chapterIndex} b=${place.blockIndex} committed=${isListCommitted()}",
            )
            return
        }
        listState?.scrollToItem(index, offsetPx)
        DebugLog.reader(
            "bodyScroll c=${place.chapterIndex} b=${place.blockIndex} index=$index offset=$offsetPx",
        )
    }

    suspend fun scrollBy(dyPx: Int) {
        if (dyPx == 0) return
        listState?.scrollBy(dyPx.toFloat())
    }

    fun isScrollInProgress(): Boolean =
        listState?.isScrollInProgress == true

    suspend fun awaitScrollIdle(timeoutMs: Long = 750L): Boolean {
        if (!isScrollInProgress()) return true
        val done = withTimeoutOrNull(timeoutMs) {
            while (isScrollInProgress()) {
                withFrameNanos { }
            }
            true
        }
        return done == true
    }

    /** Top/bottom of a row in viewport coordinates, or null if not composed. */
    fun rowViewportBounds(adapterIndex: Int): Pair<Float, Float>? {
        val info = listState?.layoutInfo?.visibleItemsInfo?.firstOrNull { it.index == adapterIndex }
            ?: return null
        val top = info.offset.toFloat()
        return top to (top + info.size)
    }

    fun putSentenceSpan(span: SentenceSpan) {
        val prev = sentenceSpans.put(span.sentenceIndex, span)
        if (prev == null ||
            prev.blockId != span.blockId ||
            kotlin.math.abs(prev.top - span.top) > 0.5f ||
            kotlin.math.abs(prev.bottom - span.bottom) > 0.5f
        ) {
            _spanEpoch.value++
        }
    }

    fun clearSentenceSpansForBlock(blockId: String) {
        val stale = sentenceSpans.filterValues { it.blockId == blockId }.keys
        if (stale.isEmpty()) return
        stale.forEach { sentenceSpans.remove(it) }
        _spanEpoch.value++
    }

    fun sentenceSpan(sentenceIndex: Int): SentenceSpan? = sentenceSpans[sentenceIndex]
}

/**
 * Full-bleed reader body. [content] paints one [ReaderItem] row (rail + text).
 * Keys keep the viewport stable when [items] grow or shrink at the front.
 */
@Composable
internal fun ReaderBodyList(
    controller: ReaderBodyController,
    items: List<ReaderItem>,
    userScrollEnabled: Boolean,
    contentPaddingTop: Dp,
    contentPaddingBottom: Dp,
    contentPaddingEnd: Dp,
    onUserScroll: () -> Unit,
    onViewPlace: (BookPlace) -> Unit,
    onVisibleChapters: (IntRange) -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn in the same box as the list (home bar). */
    homeOverlay: @Composable () -> Unit = {},
    content: @Composable (ReaderItem) -> Unit,
) {
    val density = LocalDensity.current
    val padTop = with(density) { contentPaddingTop.roundToPx() }
    val listState = rememberLazyListState()
    val onUserScrollState = rememberUpdatedState(onUserScroll)
    val onViewPlaceState = rememberUpdatedState(onViewPlace)
    val onVisibleState = rememberUpdatedState(onVisibleChapters)

    val lockScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (controller.userScrollEnabled) return Offset.Zero
                return available
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (controller.userScrollEnabled) return Velocity.Zero
                return available
            }
        }
    }

    LaunchedEffect(Unit) {
        controller.attach(listState)
    }

    SideEffect {
        controller.userScrollEnabled = userScrollEnabled
        controller.setPadTopPx(padTop)
    }
    // Capture the on-screen key before LazyColumn applies a prepend/trim.
    controller.prepareItems(items)

    LaunchedEffect(items) {
        controller.settleAfterItemsChange()
    }

    LaunchedEffect(userScrollEnabled) {
        if (!userScrollEnabled && listState.isScrollInProgress) {
            listState.scrollBy(0f)
        }
    }

    LaunchedEffect(listState, items) {
        snapshotFlow {
            if (!controller.isListCommitted()) return@snapshotFlow null
            val first = listState.firstVisibleItemIndex
            if (first !in items.indices) return@snapshotFlow null
            val place = controller.placeAt(first)
            val ids = controller.visibleBlockIds()
            val range = ReaderHome.visibleChapterRange(ids, controller.chapterByBlockId())
            Triple(place, range, listState.isScrollInProgress)
        }.distinctUntilChanged().collect { triple ->
            if (triple == null) return@collect
            val (place, range, _) = triple
            place?.let { onViewPlaceState.value(it) }
            range?.let { onVisibleState.value(it) }
        }
    }

    // Detect user drag via scroll-in-progress rising while enabled.
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(listState, userScrollEnabled) {
        snapshotFlow { listState.isScrollInProgress to controller.userScrollEnabled }
            .collect { (scrolling, enabled) ->
                if (enabled && scrolling && !wasDragging) {
                    onUserScrollState.value()
                }
                wasDragging = scrolling
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                controller.viewportCoords = coords
                controller.setViewportHeight(coords.size.height.toFloat())
            }
            .nestedScroll(lockScroll),
    ) {
        LazyColumn(
            state = listState,
            userScrollEnabled = userScrollEnabled,
            contentPadding = PaddingValues(
                top = contentPaddingTop,
                bottom = contentPaddingBottom,
                end = contentPaddingEnd,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = items,
                key = { _, item -> item.block.id },
            ) { _, item ->
                content(item)
            }
        }
        homeOverlay()
    }
}
