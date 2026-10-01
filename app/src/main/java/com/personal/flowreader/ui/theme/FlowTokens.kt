package com.personal.flowreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Layout and visual tokens for the Flow UI system. See `docs/ui-system/tokens.md`.
 *
 * Every token has one role. Do not borrow a token from another group because the value happens
 * to match (e.g. a radius as a padding, an icon size as a halo width) — add a named token instead.
 * Values are plain Dp; the app-wide UI scale is applied through LocalDensity in [FlowTheme].
 */
object FlowTokens {
    /** Spacing ramp for gaps and padding. */
    object Space {
        val None = 0.dp
        val Hair = 2.dp
        val XS = 4.dp
        val S = 8.dp
        val M = 12.dp
        val L = 16.dp
        val XL = 24.dp
        val XXL = 32.dp
    }

    /** Semantic padding roles. */
    object Pad {
        /** Screen edge gutter; also the horizontal inset of every card to the screen edge. */
        val Screen = Space.L
        /** Inner padding of a fullscreen card body. */
        val CardBody = Space.L
        /** Inner padding of floating dock cards. */
        val FloatingCard = Space.S
        /** Vertical padding of list and settings rows. */
        val RowV = Space.M
        /** Header row padding (start/end/top) of fullscreen cards. */
        val HeaderStart = Space.S
        val HeaderEnd = Space.XS
        val HeaderTop = Space.XS
        val HeaderTitleStart = Space.M
    }

    object Icon {
        val S = 16.dp
        val M = 20.dp
        val L = 24.dp
        val XL = 28.dp
        val Hero = 36.dp
    }

    /** Component sizes. */
    object Comp {
        val ChipHeight = 32.dp
        val ButtonSecondary = 40.dp
        val ButtonPrimary = 48.dp
        val FieldHeight = 56.dp
        /** Both tab levels keep the 48dp minimum touch target. */
        val TabBar = 48.dp
        val TabIndicatorPrimary = 3.dp
        val TabIndicatorSecondary = 2.dp
        val TabInnerPad = Space.S
        val TabGap = Space.L
        val TabIcon = Icon.L
        val ListRow = 104.dp
        val Fab = 56.dp
        val FabIcon = Icon.XL
        val CircleButton = Icon.Hero
        val CircleButtonIcon = Icon.M
        val ProgressBar = 3.dp
        val SliderValueWidth = 48.dp
        val AccentTrack = 22.dp
        val GridMinCell = 140.dp
        val SegmentStrip = Space.M
        /** Lazy list bottom padding so content clears the FAB. */
        val FabClearance = Fab + Space.L + Space.XS
        val DismissTarget = Fab
        /** Fullscreen card width cap on wide windows. */
        val CardMaxWidth = 640.dp
        /** Compact (confirm) card width cap. */
        val CompactCardMaxWidth = 420.dp
    }

    object Radius {
        val None = 0.dp
        val S = 8.dp
        val M = 12.dp
        val L = 16.dp
        val Pill = 50
    }

    object Shape {
        /** Every card surface (fullscreen, floating, display, media). */
        val Card = RoundedCornerShape(Radius.L)
        /** Text fields and inset sub-panels inside a card. */
        val Field = RoundedCornerShape(Radius.M)
        val Pill = RoundedCornerShape(Radius.Pill)
    }

    object Stroke {
        val Hairline = 1.dp
    }

    object Elevation {
        /** Flow surfaces are flat; separation comes from the border and feather. */
        val None = 0.dp
    }

    /** Soft page-colored halo drawn outside a card border (never counted in layout). */
    object Feather {
        val None = 0.dp
        val Panel = 20.dp
    }

    /** Scrim alpha over `colorScheme.scrim`. Only the top fullscreen card draws one. */
    object Scrim {
        const val Standard = 0.42f
        const val Hero = 0.66f
        const val Busy = 0.42f
        /** Scale applied to fullscreen cards under the top card. */
        const val ParentScale = 0.97f
    }

    /** Floating dock geometry. */
    object Dock {
        /** Distance from the screen edge to the first docked card border. */
        val EdgePad = Space.L
        /** Distance between docked card borders. */
        val Gap = Space.L
    }

    /** Alpha ramp for content on dark cover bands and translucent overlays. */
    object Alpha {
        const val Disabled = 0.38f
        const val Track = 0.12f
        const val TrackOnDark = 0.20f
        const val Divider = 0.5f
        const val OnCoverMuted = 0.78f
        const val CoverBand = 0.88f
        const val CircleOnCover = 0.72f
        const val CircleRingOnCover = 0.55f
        const val TextShadow = 0.55f
        const val SegmentBehind = 0.40f
        const val SegmentEmpty = 0.14f
    }

    // --- Cover / splash visuals ---

    const val CoverAspect = 2f / 3f
    val CoverBandBlack = Color.Black.copy(alpha = Alpha.CoverBand)
    val CoverMutedWhite = Color.White.copy(alpha = Alpha.OnCoverMuted)
    val CoverBlur = 6.dp
    val CoverGradientHeight = 72.dp
    val ShelfGradientHeight = 36.dp
    val HighlightRadius = 6.dp

    /** Neutral gray for cache-behind indicators (reader rail + segment strip). */
    val NeutralCacheGray = Color(0xFF8A8A8A)

    /** Splash title line-height multiplier (titleLarge.fontSize * this). */
    const val SplashTitleLineHeight = 1.15f

    /** Max decoded cover edge in px, by where the cover is shown. */
    object CoverEdge {
        const val Row = 384
        const val Tile = 512
        const val Banner = 512
        const val Hero = 768
    }
}
