package com.personal.flowreader.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.personal.flowreader.R
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.controls.FlowCircleButton
import com.personal.flowreader.ui.design.controls.FlowCircleStyle
import com.personal.flowreader.ui.theme.FlowLayer
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/**
 * List area with a media-card-style sort control above the FAB (bottom-end). Opens a compact
 * sort card (same stack as other app menus).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibrarySortOverlay(
    modes: List<LibrarySortMode>,
    selected: LibrarySortMode,
    onSelect: (LibrarySortMode) -> Unit,
    modifier: Modifier = Modifier,
    /** Dock height under the list (same as [LibraryBooksPane] bottomInset); FAB sits above it. */
    bottomInset: Dp = FlowTokens.Space.None,
    content: @Composable BoxScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        content()
        LibrarySortButton(
            selectedLabel = selected.label,
            onClick = { open = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .zIndex(FlowLayer.Action.z)
                .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility)
                .padding(
                    end = FlowTokens.Pad.Screen,
                    // Match FlowScreen FAB pad, then sit one Fab + gap above it.
                    bottom = bottomInset + FlowTokens.Pad.Screen +
                        FlowTokens.Comp.Fab + FlowTokens.Space.S,
                ),
        )
    }
    LibrarySortCard(
        visible = open,
        modes = modes,
        selected = selected,
        onSelect = onSelect,
        onDismiss = { open = false },
    )
}

/** Circle control matching the media-card rail ([FlowCircleButton] / OnCover). */
@Composable
fun LibrarySortButton(
    selectedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = ImageVector.vectorResource(R.drawable.ic_library_sort)
    FlowCircleButton(
        icon = icon,
        contentDescription = "Sort: $selectedLabel",
        onClick = onClick,
        style = FlowCircleStyle.OnCover,
        modifier = modifier,
    )
}

/** Compact fullscreen card listing sort modes (check on the active one). */
@Composable
fun LibrarySortCard(
    visible: Boolean,
    modes: List<LibrarySortMode>,
    selected: LibrarySortMode,
    onSelect: (LibrarySortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = "Sort",
        variant = FlowCardVariant.Compact,
        footer = { FlowActionRow { FlowTextAction("Close", onDismiss) } },
    ) {
        modes.forEach { mode ->
            Surface(
                onClick = {
                    onSelect(mode)
                    onDismiss()
                },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = FlowTokens.Space.M),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        mode.label,
                        style = FlowType.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (mode == selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(FlowTokens.Icon.L),
                        )
                    }
                }
            }
        }
    }
}
