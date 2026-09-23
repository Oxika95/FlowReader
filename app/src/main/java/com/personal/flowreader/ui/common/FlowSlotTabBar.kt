package com.personal.flowreader.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.personal.flowreader.ui.library.LibraryTabSlots
import com.personal.flowreader.ui.theme.FlowTokens

/** One slot in [FlowSlotTabBar]: text label and/or custom (icon) content. */
data class FlowSlotTab(
    val selected: Boolean,
    val onClick: () -> Unit,
    /** When non-null, used for min-width measurement (text tabs). Null = trailing icon slot. */
    val measureLabel: String? = null,
    val content: @Composable (selected: Boolean) -> Unit,
)

/**
 * Shared slot-measured tab bar used by Library and Royal Road list tabs.
 * Layout math lives in [LibraryTabSlots]; chrome matches [FlowTabSlotHeader].
 */
@Composable
fun FlowSlotTabBar(
    tabs: List<FlowSlotTab>,
    inset: Dp = FlowTokens.ScreenGutter,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val indicator = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val innerPadPx = with(density) { FlowTabMetrics.InnerPad.roundToPx() }
        val iconMinPx = with(density) { 22.dp.roundToPx() } + innerPadPx * 2
        val minWidths = IntArray(tabs.size) { i ->
            val label = tabs[i].measureLabel
            if (label != null) {
                measurer.measure(
                    text = label,
                    style = labelStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width + innerPadPx * 2
            } else {
                iconMinPx
            }
        }
        val layout = LibraryTabSlots.layout(
            availablePx = constraints.maxWidth,
            insetPx = with(density) { inset.roundToPx() },
            gapPx = with(density) { FlowTabMetrics.MinGap.roundToPx() },
            minWidthsPx = minWidths,
        )
        Row(
            modifier = Modifier
                .height(FlowTabMetrics.BarHeight)
                .padding(horizontal = inset)
                .then(
                    if (layout.overflow) {
                        Modifier.horizontalScroll(scroll)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(FlowTabMetrics.MinGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { index, tab ->
                FlowTabSlotHeader(
                    selected = tab.selected,
                    onClick = tab.onClick,
                    indicator = indicator,
                    modifier = Modifier
                        .width(with(density) { layout.slotWidthsPx[index].toDp() })
                        .fillMaxHeight(),
                    innerPad = FlowTabMetrics.InnerPad,
                    indicatorHeight = FlowTabMetrics.IndicatorHeight,
                ) {
                    tab.content(tab.selected)
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
fun FlowSlotTabLabel(text: String, selected: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
    )
}
