package com.personal.flowreader.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.theme.FlowTokens

/** Shared tab-slot header used by the library bar and Royal Road sub-tabs. */
@Composable
fun FlowTabSlotHeader(
    selected: Boolean,
    onClick: () -> Unit,
    indicator: Color,
    modifier: Modifier = Modifier,
    innerPad: Dp = FlowTokens.Comp.TabInnerPad,
    indicatorHeight: Dp = FlowTokens.Comp.TabIndicator,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = innerPad),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(indicatorHeight)
                .background(if (selected) indicator else Color.Transparent),
        )
    }
}

object FlowTabMetrics {
    val MinGap = FlowTokens.Pad.Screen
    val InnerPad = FlowTokens.Comp.TabInnerPad
    val BarHeight = FlowTokens.Comp.TabBar
    val IndicatorHeight = FlowTokens.Comp.TabIndicator
}
