package com.personal.flowreader.ui.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment

/** Enter/exit specs for every animated Flow surface. See `docs/ui-system/layers.md`. */
object FlowMotion {
    const val Short = 150
    const val Medium = 250

    private const val CardScale = 0.96f

    fun scrimEnter(): EnterTransition = fadeIn(tween(Medium))
    fun scrimExit(): ExitTransition = fadeOut(tween(Medium))

    fun fullscreenEnter(): EnterTransition =
        fadeIn(tween(Medium)) + scaleIn(tween(Medium), initialScale = CardScale)

    fun fullscreenExit(): ExitTransition =
        fadeOut(tween(Short)) + scaleOut(tween(Short), targetScale = CardScale)

    /** Docked floating card; [fromTop] slides from the top edge, otherwise from the bottom. */
    fun dockEnter(fromTop: Boolean): EnterTransition =
        fadeIn(tween(Medium)) +
            slideInVertically(tween(Medium)) { if (fromTop) -it / 2 else it / 2 } +
            expandVertically(tween(Medium), expandFrom = if (fromTop) Alignment.Top else Alignment.Bottom, clip = false)

    fun dockExit(fromTop: Boolean): ExitTransition =
        fadeOut(tween(Medium)) +
            slideOutVertically(tween(Medium)) { if (fromTop) -it / 2 else it / 2 } +
            shrinkVertically(tween(Medium), shrinkTowards = if (fromTop) Alignment.Top else Alignment.Bottom, clip = false)

    /** Small standalone controls (FAB, unlock button, bubbles). */
    fun popEnter(): EnterTransition = fadeIn(tween(Short)) + scaleIn(tween(Short))
    fun popExit(): ExitTransition = fadeOut(tween(Short)) + scaleOut(tween(Short))

    /** Inline expand/collapse (collapsible sections, pane switches). */
    fun expandEnter(): EnterTransition = fadeIn(tween(Short)) + expandVertically(tween(Medium))
    fun expandExit(): ExitTransition = fadeOut(tween(Short)) + shrinkVertically(tween(Medium))
}
