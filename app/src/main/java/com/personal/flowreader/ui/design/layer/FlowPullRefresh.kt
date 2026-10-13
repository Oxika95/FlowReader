package com.personal.flowreader.ui.design.layer

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pull down on a list area to refresh it. [content] must scroll vertically (a list, grid, or
 * `FlowEmptyState`) for the pull to register.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowPullRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}
