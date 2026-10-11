package com.personal.flowreader.ui.design.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.launch
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.design.controls.FlowDot
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Tab bar hierarchy. A secondary bar always sits under a primary bar or card header. */
enum class FlowTabLevel {
    /** Top-level destinations: Library tabs, Settings sections. */
    Primary,
    /** Sections inside a primary tab: plugin lists, Settings sub-tabs. */
    Secondary,
}

/**
 * One tab. Build with [text], [icon] or [action]; a bar is just a `List<FlowTab>`.
 * Never draw a custom tab row — add entries to the list instead.
 */
@Immutable
class FlowTab private constructor(
    val key: Any,
    val label: String?,
    val icon: ImageVector?,
    val contentDescription: String?,
    val selected: Boolean,
    val onClick: () -> Unit,
    /** New items inside this tab; above 0 shows an accent dot (no number). */
    val badge: Int? = null,
    val image: ImageBitmap? = null,
) {
    companion object {
        /** Text destination tab. */
        fun text(label: String, selected: Boolean, onClick: () -> Unit, key: Any = label, badge: Int? = null): FlowTab =
            FlowTab(key, label, null, null, selected, onClick, badge)

        /** Icon destination tab (e.g. Files, Queue, plugin Search, plugin lists). Long-press shows [contentDescription]. */
        fun icon(
            icon: ImageVector,
            contentDescription: String,
            selected: Boolean,
            onClick: () -> Unit,
            key: Any = contentDescription,
            badge: Int? = null,
        ): FlowTab = FlowTab(key, null, icon, contentDescription, selected, onClick, badge)

        /**
         * Image destination tab (a plugin's `icon.png`). Only the image's shape is used: it is tinted like
         * an icon tab, so it must be a silhouette on a transparent background. Long-press shows [contentDescription].
         */
        fun image(
            image: ImageBitmap,
            contentDescription: String,
            selected: Boolean,
            onClick: () -> Unit,
            key: Any = contentDescription,
            badge: Int? = null,
        ): FlowTab = FlowTab(key, null, null, contentDescription, selected, onClick, badge, image)

        /**
         * Icon tab that opens a card instead of switching content (Library "+", plugin Account).
         * Shows as selected while its card is [open]. Keep action tabs last in the bar.
         */
        fun action(
            icon: ImageVector,
            contentDescription: String,
            open: Boolean,
            onClick: () -> Unit,
            key: Any = contentDescription,
        ): FlowTab = FlowTab(key, null, icon, contentDescription, open, onClick)
    }
}

/** Text tabs from [labels], selecting by index. */
fun flowTextTabs(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit): List<FlowTab> =
    labels.mapIndexed { i, label -> FlowTab.text(label, selected = i == selectedIndex, onClick = { onSelect(i) }) }

/**
 * The one tab bar. Slots are measured to fit their labels and share leftover width;
 * the bar scrolls (and auto-scrolls to the selected tab) when they cannot fit.
 * Both levels keep a 48dp touch target; [FlowTabLevel.Secondary] uses lighter type and
 * a thinner indicator. Tabs expose `Role.Tab` semantics.
 */
@Composable
fun FlowTabBar(
    tabs: List<FlowTab>,
    modifier: Modifier = Modifier,
    level: FlowTabLevel = FlowTabLevel.Primary,
    inset: Dp = FlowTokens.Pad.Screen,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val baseStyle = if (level == FlowTabLevel.Primary) FlowType.tabPrimary else FlowType.tabSecondary
    val measureStyle = baseStyle.copy(fontWeight = FontWeight.SemiBold)
    val indicatorHeight = if (level == FlowTabLevel.Primary) {
        FlowTokens.Comp.TabIndicatorPrimary
    } else {
        FlowTokens.Comp.TabIndicatorSecondary
    }
    val dividerColor = if (level == FlowTabLevel.Primary) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = FlowTokens.Alpha.Divider)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val innerPadPx = with(density) { FlowTokens.Comp.TabInnerPad.roundToPx() }
        val gapPx = with(density) { FlowTokens.Comp.TabGap.roundToPx() }
        val iconMinPx = with(density) { FlowTokens.Comp.TabIcon.roundToPx() } + innerPadPx * 2
        val minWidths = IntArray(tabs.size) { i ->
            val label = tabs[i].label
            if (label != null) {
                measurer.measure(text = label, style = measureStyle, maxLines = 1, softWrap = false)
                    .size.width + innerPadPx * 2
            } else {
                iconMinPx
            }
        }
        val slots = FlowTabLayout.layout(
            availablePx = constraints.maxWidth,
            insetPx = with(density) { inset.roundToPx() },
            gapPx = gapPx,
            minWidthsPx = minWidths,
        )
        val selectedIndex = tabs.indexOfFirst { it.selected }
        if (slots.overflow) {
            LaunchedEffect(selectedIndex, tabs.size) {
                if (selectedIndex >= 0) {
                    scroll.animateScrollTo(FlowTabLayout.offsetOf(selectedIndex, slots.slotWidthsPx, gapPx))
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = dividerColor,
        )
        Row(
            modifier = Modifier
                .height(FlowTokens.Comp.TabBar)
                .padding(horizontal = inset)
                .then(if (slots.overflow) Modifier.horizontalScroll(scroll) else Modifier.fillMaxWidth()),
            horizontalArrangement = Arrangement.spacedBy(FlowTokens.Comp.TabGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { index, tab ->
                FlowTabSlot(
                    tab = tab,
                    style = baseStyle,
                    indicatorHeight = indicatorHeight,
                    modifier = Modifier
                        .width(with(density) { slots.slotWidthsPx[index].toDp() })
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun FlowTabSlot(
    tab: FlowTab,
    style: TextStyle,
    indicatorHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val hint = tab.contentDescription
    if (tab.label != null || hint == null) {
        FlowTabContent(
            tab,
            style,
            indicatorHeight,
            modifier.selectable(selected = tab.selected, role = Role.Tab, onClick = tab.onClick),
        )
        return
    }
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(hint) } },
        state = tooltip,
        modifier = modifier,
        enableUserInput = false,
    ) {
        FlowTabContent(
            tab,
            style,
            indicatorHeight,
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    role = Role.Tab,
                    onClick = tab.onClick,
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .semantics { selected = tab.selected },
        )
    }
}

@Composable
private fun FlowTabContent(
    tab: FlowTab,
    style: TextStyle,
    indicatorHeight: Dp,
    modifier: Modifier,
) {
    val selectedFraction by animateFloatAsState(if (tab.selected) 1f else 0f, label = "tabIndicator")
    val contentColor by animateColorAsState(
        if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = FlowTokens.Comp.TabInnerPad),
            contentAlignment = Alignment.Center,
        ) {
            val label = tab.label
            val icon = tab.icon
            val image = tab.image
            Box {
                if (label != null) {
                    Text(
                        label,
                        style = style,
                        fontWeight = if (tab.selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                } else if (icon != null) {
                    Icon(
                        icon,
                        contentDescription = tab.contentDescription,
                        tint = contentColor,
                        modifier = Modifier.size(FlowTokens.Comp.TabIcon),
                    )
                } else if (image != null) {
                    Image(
                        image,
                        contentDescription = tab.contentDescription,
                        colorFilter = ColorFilter.tint(contentColor),
                        modifier = Modifier.size(FlowTokens.Comp.TabIcon),
                    )
                }
                if ((tab.badge ?: 0) > 0) {
                    FlowDot(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = FlowTokens.Comp.TabDot, y = -FlowTokens.Comp.TabDot / 2)
                            .semantics { stateDescription = "New chapters" },
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth(0.5f + 0.5f * selectedFraction)
                .height(indicatorHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = selectedFraction)),
        )
    }
}
