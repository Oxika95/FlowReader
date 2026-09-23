package com.personal.flowreader.ui.chrome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.ui.theme.FlowTokens


/**
 * Scrim + centered scrolling card shared by the Settings and TOC modals: tap the scrim to
 * dismiss, taps inside the card are swallowed, and the card never outgrows the viewport.
 *
 * When [fillMaxCardHeight] is true the panel stretches to the available height (full-screen
 * splash). When [centerContent] is also true, short content is centered vertically in that panel.
 * Set [contentScrollable] to false when the caller manages its own scroll (e.g. pinned header).
 */
@Composable
internal fun ReaderModalScaffold(
    visible: Boolean,
    contentPadding: PaddingValues,
    onDismiss: () -> Unit,
    fillMaxCardHeight: Boolean = false,
    centerContent: Boolean = false,
    contentScrollable: Boolean = true,
    /** Soft halo drawn outside the card border; Radius.None keeps the hard-edged card. */
    feather: Dp = FlowTokens.Radius.None,
    scrimAlpha: Float = FlowTokens.ScrimStandard,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(enabled = visible, onBack = onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                // Status/navigation bars are hidden in the reader, so this is a no-op there and
                // keeps the card clear of the bars anywhere they are showing (e.g. the library).
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical)),
            contentAlignment = Alignment.Center,
        ) {
            // Leave room for the halo so a feathered card still clears the screen edges.
            val maxCardHeight = maxHeight - (ReaderOverlayVerticalPad + feather) * 2
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + scaleIn(initialScale = 0.96f),
                exit = fadeOut() + scaleOut(targetScale = 0.96f),
            ) {
                ReaderPanelSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxCardHeight)
                        .then(
                            if (fillMaxCardHeight) Modifier.fillMaxHeight()
                            else Modifier.wrapContentHeight(),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                    matchReaderWidth = true,
                    feather = feather,
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (fillMaxCardHeight) Modifier.height(maxCardHeight)
                                else Modifier.heightIn(max = maxCardHeight),
                            )
                            .then(
                                if (contentScrollable) {
                                    Modifier.verticalScroll(
                                        scrollState,
                                        // Keep layout scrollable for measurement, but only accept
                                        // drag/fling when content actually overflows the card.
                                        enabled = scrollState.maxValue > 0,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(contentPadding),
                        verticalArrangement = if (centerContent) {
                            Arrangement.Center
                        } else {
                            Arrangement.Top
                        },
                        content = content,
                    )
                }
            }
        }
    }
}
