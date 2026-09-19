package com.personal.flowreader.ui.reader

/**
 * Single table of reader touch outcomes. Detectors only classify the gesture;
 * [resolveTouch] decides what happens so handlers stay aware of each other.
 */
internal enum class ReaderOverlay {
    Hidden,
    Chrome,
    Settings,
    Toc,
}

internal enum class ReaderTouchTarget {
    BodyText,
    BodyGap,
    EdgeBand,
    RightEdgeBack,
    Pin,
}

internal enum class ReaderGestureKind {
    SingleTap,
    DoubleTap,
    SwipeBack,
}

internal sealed class ReaderTouchAction {
    data object ToggleChrome : ReaderTouchAction()
    data object DismissOverlay : ReaderTouchAction()
    data object ClearSelection : ReaderTouchAction()
    data object DoubleTapPlay : ReaderTouchAction()
    data object ResumeFollow : ReaderTouchAction()
    data object NavigateBack : ReaderTouchAction()
}

/**
 * Resolve a classified gesture into at most one action.
 * Returns null when the gesture should be ignored (e.g. double-tap on a gap).
 */
internal fun resolveTouch(
    target: ReaderTouchTarget,
    kind: ReaderGestureKind,
    overlay: ReaderOverlay,
    selectionActive: Boolean,
): ReaderTouchAction? = when (kind) {
    ReaderGestureKind.SwipeBack -> when {
        target != ReaderTouchTarget.RightEdgeBack -> null
        selectionActive -> ReaderTouchAction.ClearSelection
        else -> ReaderTouchAction.NavigateBack
    }

    ReaderGestureKind.SingleTap -> when (target) {
        ReaderTouchTarget.Pin -> ReaderTouchAction.ResumeFollow
        ReaderTouchTarget.RightEdgeBack -> null
        ReaderTouchTarget.BodyText,
        ReaderTouchTarget.BodyGap,
        ReaderTouchTarget.EdgeBand,
        -> when {
            selectionActive -> ReaderTouchAction.ClearSelection
            overlay == ReaderOverlay.Settings || overlay == ReaderOverlay.Toc ->
                ReaderTouchAction.DismissOverlay
            else -> ReaderTouchAction.ToggleChrome
        }
    }

    ReaderGestureKind.DoubleTap -> when (target) {
        ReaderTouchTarget.Pin -> ReaderTouchAction.ResumeFollow
        ReaderTouchTarget.BodyGap,
        ReaderTouchTarget.EdgeBand,
        ReaderTouchTarget.RightEdgeBack,
        -> null
        ReaderTouchTarget.BodyText -> when {
            selectionActive -> ReaderTouchAction.ClearSelection
            overlay == ReaderOverlay.Settings || overlay == ReaderOverlay.Toc ->
                ReaderTouchAction.DismissOverlay
            else -> ReaderTouchAction.DoubleTapPlay
        }
    }
}
