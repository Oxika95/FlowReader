package com.personal.flowreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderTouchPolicyTest {

    @Test
    fun singleTap_body_togglesChromeWhenIdle() {
        assertEquals(
            ReaderTouchAction.ToggleChrome,
            resolveTouch(
                ReaderTouchTarget.BodyText,
                ReaderGestureKind.SingleTap,
                ReaderOverlay.Hidden,
                selectionActive = false,
            ),
        )
    }

    @Test
    fun singleTap_clearsSelectionWhenActive() {
        assertEquals(
            ReaderTouchAction.ClearSelection,
            resolveTouch(
                ReaderTouchTarget.BodyGap,
                ReaderGestureKind.SingleTap,
                ReaderOverlay.Chrome,
                selectionActive = true,
            ),
        )
        assertEquals(
            ReaderTouchAction.ClearSelection,
            resolveTouch(
                ReaderTouchTarget.EdgeBand,
                ReaderGestureKind.SingleTap,
                ReaderOverlay.Hidden,
                selectionActive = true,
            ),
        )
    }

    @Test
    fun singleTap_dismissesSettingsOrToc() {
        assertEquals(
            ReaderTouchAction.DismissOverlay,
            resolveTouch(
                ReaderTouchTarget.BodyText,
                ReaderGestureKind.SingleTap,
                ReaderOverlay.Settings,
                selectionActive = false,
            ),
        )
    }

    @Test
    fun doubleTap_body_playWhenIdle_clearWhenSelecting() {
        assertEquals(
            ReaderTouchAction.DoubleTapPlay,
            resolveTouch(
                ReaderTouchTarget.BodyText,
                ReaderGestureKind.DoubleTap,
                ReaderOverlay.Hidden,
                selectionActive = false,
            ),
        )
        assertEquals(
            ReaderTouchAction.ClearSelection,
            resolveTouch(
                ReaderTouchTarget.BodyText,
                ReaderGestureKind.DoubleTap,
                ReaderOverlay.Hidden,
                selectionActive = true,
            ),
        )
    }

    @Test
    fun doubleTap_gap_ignored() {
        assertNull(
            resolveTouch(
                ReaderTouchTarget.BodyGap,
                ReaderGestureKind.DoubleTap,
                ReaderOverlay.Hidden,
                selectionActive = false,
            ),
        )
    }

    @Test
    fun swipeBack_clearsSelectionOrNavigates() {
        assertEquals(
            ReaderTouchAction.ClearSelection,
            resolveTouch(
                ReaderTouchTarget.RightEdgeBack,
                ReaderGestureKind.SwipeBack,
                ReaderOverlay.Chrome,
                selectionActive = true,
            ),
        )
        assertEquals(
            ReaderTouchAction.NavigateBack,
            resolveTouch(
                ReaderTouchTarget.RightEdgeBack,
                ReaderGestureKind.SwipeBack,
                ReaderOverlay.Hidden,
                selectionActive = false,
            ),
        )
    }

    @Test
    fun pin_alwaysResumesFollow() {
        assertEquals(
            ReaderTouchAction.ResumeFollow,
            resolveTouch(
                ReaderTouchTarget.Pin,
                ReaderGestureKind.SingleTap,
                ReaderOverlay.Hidden,
                selectionActive = false,
            ),
        )
        assertEquals(
            ReaderTouchAction.ResumeFollow,
            resolveTouch(
                ReaderTouchTarget.Pin,
                ReaderGestureKind.DoubleTap,
                ReaderOverlay.Chrome,
                selectionActive = true,
            ),
        )
    }
}
