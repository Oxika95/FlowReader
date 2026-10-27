package com.personal.flowreader.ui.reader

import android.content.Context
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.personal.flowreader.data.BookPlace
import com.personal.flowreader.data.ReaderItem
import com.personal.flowreader.debug.DebugLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Reader body list: RecyclerView + DiffUtil so prepend/trim keep the on-screen text put.
 * Scroll identity for the app is [BookPlace], not adapter row numbers.
 */
internal class ReaderBodyController {
    var recycler: RecyclerView? = null
        private set
    var layoutManager: LinearLayoutManager? = null
        private set
    private var adapter: ListAdapter<ReaderItem, *>? = null
    var items: List<ReaderItem> = emptyList()
        private set
    private var pendingEpoch: Int = 0
    private val _listEpoch = MutableStateFlow(0)
    /** DiffUtil commit generation — collect to wait until the adapter matches [items]. */
    val listEpoch: StateFlow<Int> = _listEpoch.asStateFlow()
    /** When false, touch is ignored so scroll-lock cannot be dragged. */
    var userScrollEnabled: Boolean = true
    /** Top content padding (px) applied to the RecyclerView — LM offset is from this edge. */
    var padTopPx: Int = 0
        private set

    fun attach(recycler: RecyclerView, lm: LinearLayoutManager, adapter: ListAdapter<ReaderItem, *>) {
        this.recycler = recycler
        this.adapter = adapter
        this.layoutManager = lm
    }

    fun setPadTopPx(px: Int) {
        padTopPx = px.coerceAtLeast(0)
    }

    fun submitItems(newItems: List<ReaderItem>) {
        if (newItems === items || (newItems.size == items.size && newItems == items)) {
            return
        }
        items = newItems
        val epoch = ++pendingEpoch
        adapter?.submitList(newItems) {
            if (epoch == pendingEpoch) _listEpoch.value = epoch
        } ?: run { _listEpoch.value = epoch }
    }

    /** True when the RecyclerView adapter matches the latest [items] snapshot. */
    fun isListCommitted(): Boolean = _listEpoch.value == pendingEpoch

    /** Suspend until DiffUtil has applied the latest [submitItems] call. */
    suspend fun awaitListCommitted() {
        if (isListCommitted()) return
        val want = pendingEpoch
        listEpoch.first { it >= want && isListCommitted() }
    }

    /** Adapter list currently laid out — never the pending [items] mid-DiffUtil. */
    fun committedItems(): List<ReaderItem> = adapter?.currentList ?: items

    fun adapterIndexOf(place: BookPlace): Int =
        ReaderHome.placeToAdapterIndex(committedItems(), place)

    fun blockIdAt(adapterIndex: Int): String? =
        committedItems().getOrNull(adapterIndex)?.block?.id

    fun placeAt(adapterIndex: Int): BookPlace? {
        val item = committedItems().getOrNull(adapterIndex) ?: return null
        return BookPlace(item.chapterIndex, item.blockIndex, 0)
    }

    fun firstVisibleAdapterIndex(): Int =
        layoutManager?.findFirstVisibleItemPosition() ?: RecyclerView.NO_POSITION

    fun lastVisibleAdapterIndex(): Int =
        layoutManager?.findLastVisibleItemPosition() ?: RecyclerView.NO_POSITION

    fun firstVisibleBlockId(): String? {
        val i = firstVisibleAdapterIndex()
        if (i == RecyclerView.NO_POSITION) return null
        return blockIdAt(i)
    }

    fun visibleBlockIds(): List<String> {
        val lm = layoutManager ?: return emptyList()
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return emptyList()
        return (first..last).mapNotNull { blockIdAt(it) }
    }

    /** Chapter index by block id for the committed list (viewport reporting). */
    fun chapterByBlockId(): Map<String, Int> =
        committedItems().associate { it.block.id to it.chapterIndex }

    /** Kill fling/drag so a location jump is not overwritten by leftover momentum. */
    fun stopScroll() {
        recycler?.stopScroll()
    }

    /**
     * Park [place]'s row top [offsetPx] below the padding edge (LM).
     * For home: pass [ReaderHome.homePadOffset] so the top sits on the marker Y.
     * Stops any in-flight scroll first.
     */
    fun scrollToPlace(place: BookPlace, offsetPx: Int = 0) {
        stopScroll()
        val committed = committedItems()
        val index = ReaderHome.placeToAdapterIndex(committed, place)
        if (index < 0) {
            DebugLog.reader(
                "bodyScroll miss c=${place.chapterIndex} b=${place.blockIndex} committed=${isListCommitted()}",
            )
            return
        }
        layoutManager?.scrollToPositionWithOffset(index, offsetPx)
        DebugLog.reader(
            "bodyScroll c=${place.chapterIndex} b=${place.blockIndex} index=$index offset=$offsetPx",
        )
    }

    fun scrollBy(dyPx: Int) {
        recycler?.scrollBy(0, dyPx)
    }

    fun smoothScrollBy(dyPx: Int) {
        recycler?.smoothScrollBy(0, dyPx)
    }

    fun isScrollInProgress(): Boolean {
        val rv = recycler ?: return false
        return rv.scrollState != RecyclerView.SCROLL_STATE_IDLE
    }

    /**
     * Wait until the RecyclerView is idle. Polls each frame — [scrollState] is not Compose
     * snapshot state, so [snapshotFlow] would hang forever after the first "still scrolling" sample.
     */
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

    fun viewTopInWindow(): Int {
        val loc = IntArray(2)
        recycler?.getLocationInWindow(loc)
        return loc[1]
    }

    fun viewHeight(): Int = recycler?.height ?: 0

    /**
     * Top/bottom of an adapter row in window coordinates, or null if not laid out.
     */
    fun rowWindowBounds(adapterIndex: Int): Pair<Float, Float>? {
        val lm = layoutManager ?: return null
        val child = lm.findViewByPosition(adapterIndex) ?: return null
        val loc = IntArray(2)
        child.getLocationInWindow(loc)
        val top = loc[1].toFloat()
        return top to (top + child.height)
    }

}

private class ReaderBodyAdapter(
    private val content: @Composable (ReaderItem) -> Unit,
) : ListAdapter<ReaderItem, ReaderBodyAdapter.Holder>(Diff) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long =
        getItem(position).block.id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val frame = HeightStickyFrame(parent.context)
        frame.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        val composeView = ReaderBodyComposeView(parent.context)
        frame.addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        return Holder(frame, composeView)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(getItem(position), content)
    }

    override fun onViewRecycled(holder: Holder) {
        // Keep last composition so recycled rows do not collapse to height 0 before rebind
        // (zero-height inserts above the viewport shift the camera on prepend).
        super.onViewRecycled(holder)
    }

    class Holder(
        private val frame: HeightStickyFrame,
        private val composeView: ReaderBodyComposeView,
    ) : RecyclerView.ViewHolder(frame) {
        fun bind(item: ReaderItem, content: @Composable (ReaderItem) -> Unit) {
            frame.onBindBlockId(item.block.id)
            composeView.setRow(item, content)
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReaderItem>() {
        override fun areItemsTheSame(a: ReaderItem, b: ReaderItem): Boolean =
            a.block.id == b.block.id

        override fun areContentsTheSame(a: ReaderItem, b: ReaderItem): Boolean =
            a == b
    }
}

/**
 * Holds last non-zero height so DiffUtil prepend does not treat rows as zero-tall while Compose
 * catches up (zero-height inserts shift the camera). Resets when rebound to a different block.
 */
private class HeightStickyFrame(context: Context) : FrameLayout(context) {
    private var lastHeight = 0
    private var boundBlockId: String? = null

    fun onBindBlockId(blockId: String) {
        if (boundBlockId != null && boundBlockId != blockId) lastHeight = 0
        boundBlockId = blockId
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (measuredHeight > 0) {
            lastHeight = measuredHeight
        } else if (lastHeight > 0) {
            setMeasuredDimension(measuredWidth, lastHeight)
        }
    }
}

private class ReaderBodyComposeView(context: Context) : AbstractComposeView(context) {
    private var row by mutableStateOf<ReaderItem?>(null)
    private var rowContent by mutableStateOf<(@Composable (ReaderItem) -> Unit)?>(null)

    init {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    }

    fun setRow(item: ReaderItem, content: @Composable (ReaderItem) -> Unit) {
        row = item
        rowContent = content
        if (isAttachedToWindow) {
            // Compose before the next measure so WRAP_CONTENT is not 0 on first prepend layout.
            createComposition()
        }
    }

    @Composable
    override fun Content() {
        val item = row
        val content = rowContent
        if (item != null && content != null) {
            content(item)
        }
    }
}

/**
 * Full-bleed reader body. [content] paints one [ReaderItem] row (rail + text).
 * DiffUtil keeps the viewport stable when [items] grow or shrink at the front.
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
    content: @Composable (ReaderItem) -> Unit,
) {
    val density = LocalDensity.current
    val padTop = with(density) { contentPaddingTop.roundToPx() }
    val padBottom = with(density) { contentPaddingBottom.roundToPx() }
    val padEnd = with(density) { contentPaddingEnd.roundToPx() }
    val contentState = rememberUpdatedContent(content)

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val lm = LinearLayoutManager(context)
            val adapter = ReaderBodyAdapter { item -> contentState.value(item) }
            val rv = RecyclerView(context).apply {
                layoutManager = lm
                this.adapter = adapter
                itemAnimator = null
                clipToPadding = false
                overScrollMode = RecyclerView.OVER_SCROLL_NEVER
                updatePadding(top = padTop, bottom = padBottom, right = padEnd)
                isNestedScrollingEnabled = true
            }
            controller.attach(rv, lm, adapter)
            controller.setPadTopPx(padTop)
            rv.addOnItemTouchListener(
                object : RecyclerView.SimpleOnItemTouchListener() {
                    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean =
                        !controller.userScrollEnabled
                },
            )
            rv.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            onUserScroll()
                        }
                    }

                    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                        // Pending DiffUtil list must not drive viewport — positions still match the old rows.
                        if (!controller.isListCommitted()) return
                        val first = controller.firstVisibleAdapterIndex()
                        if (first != RecyclerView.NO_POSITION) {
                            controller.placeAt(first)?.let(onViewPlace)
                        }
                        val ids = controller.visibleBlockIds()
                        ReaderHome.visibleChapterRange(ids, controller.chapterByBlockId())
                            ?.let(onVisibleChapters)
                    }
                },
            )
            rv
        },
        update = { rv ->
            controller.userScrollEnabled = userScrollEnabled
            controller.setPadTopPx(padTop)
            rv.updatePadding(top = padTop, bottom = padBottom, right = padEnd)
            controller.submitItems(items)
            if (!userScrollEnabled && rv.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                rv.stopScroll()
            }
        },
    )

    // When scroll lock toggles, stop coasting.
    LaunchedEffect(userScrollEnabled) {
        if (!userScrollEnabled) {
            controller.recycler?.stopScroll()
        }
    }

    // Report viewport once the committed list is on screen (before first user scroll).
    LaunchedEffect(items) {
        snapshotFlow {
            if (!controller.isListCommitted()) return@snapshotFlow null
            val ids = controller.visibleBlockIds()
            ReaderHome.visibleChapterRange(ids, controller.chapterByBlockId())
        }.distinctUntilChanged().collect { range ->
            range?.let(onVisibleChapters)
        }
    }
}

@Composable
private fun rememberUpdatedContent(
    content: @Composable (ReaderItem) -> Unit,
): MutableState<@Composable (ReaderItem) -> Unit> {
    val state = remember { mutableStateOf(content) }
    state.value = content
    return state
}
