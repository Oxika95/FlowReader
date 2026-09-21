package com.personal.flowreader.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Shared layout / visual tokens so library, reader, and plugin screens stay aligned.
 * Values are plain Dp — app-wide scale is applied via LocalDensity in [FlowTheme].
 */
object FlowTokens {
    object Space {
        val Hair = 2.dp
        val XS = 4.dp
        val S = 8.dp
        val M = 12.dp
        val L = 16.dp
        val XL = 24.dp
        val XXL = 32.dp
    }

    object Pad {
        val Screen = Space.L
        val CardIn = Space.M
        val RowV = Space.M
        val ModalBody = Space.L
        val ChipIn = Space.S
        val ModalOuter = Space.S
        val ModalHeaderStart = Space.S
        val ModalHeaderEnd = Space.XS
        val ModalHeaderTop = Space.XS
        val ModalTitleStart = Space.M
    }

    object Icon {
        val S = 16.dp
        val M = 20.dp
        val L = 24.dp
        val XL = 28.dp
        val Hero = 36.dp
    }

    object Comp {
        val ChipHeight = 32.dp
        val ButtonSecondary = 40.dp
        val ButtonPrimary = 48.dp
        val FieldHeight = 56.dp
        val TabBar = 48.dp
        val TabIndicator = 3.dp
        val TabInnerPad = Space.S
        val ListRow = 104.dp
        val Fab = 56.dp
        val FabIcon = Icon.XL
        val ProgressBar = 3.dp
        val SliderValueWidth = 48.dp
        val AccentTrack = 22.dp
        val GridMinCell = 140.dp
        /** Extra Lazy list bottom padding so content clears the FAB. */
        val FabClearance = Fab + Space.L + Space.XS
        /** Snackbar lift above the FAB. */
        val SnackbarFabLift = Fab + Space.S
    }

    object Radius {
        val None = 0.dp
        val S = 8.dp
        val M = 12.dp
        val L = 16.dp
    }

    object Stroke {
        val Hairline = 1.dp
    }

    // --- Cover / splash / scrim (visual, not spacing ramp) ---

    const val CoverAspect = 2f / 3f
    val CoverBandBlack = Color.Black.copy(alpha = 0.88f)
    val CoverMutedWhite = Color.White.copy(alpha = 0.78f)
    val CoverBlur = 6.dp
    val CoverGradientHeight = 72.dp
    val ShelfGradientHeight = 36.dp

    /** Standard panels (settings, ToC, add overlays). */
    const val ScrimStandard = 0.42f
    /** Hero / story splash overlays. */
    const val ScrimHero = 0.66f

    /** Neutral gray for cache-behind indicators (reader rail + RR strip). */
    val NeutralCacheGray = Color(0xFF8A8A8A)

    /** Splash title line-height multiplier (titleLarge.fontSize * this). */
    const val SplashTitleLineHeight = 1.15f

    val SplashActionRowSpacing = Space.XS

    // --- Aliases for existing call sites (same values, clearer names preferred) ---

    val ScreenGutter = Pad.Screen
    val PanelRadius = Radius.L
    val PanelShape = RoundedCornerShape(PanelRadius)
    val ProgressBarHeight = Comp.ProgressBar
    val FabSize = Comp.Fab
    val FabIcon = Comp.FabIcon
    val FabClearance = Comp.FabClearance
    val ModalOuterPadding = Pad.ModalOuter
    val ModalBodyPadding = Pad.ModalBody
    val ModalHeaderStart = Pad.ModalHeaderStart
    val ModalHeaderEnd = Pad.ModalHeaderEnd
    val ModalHeaderTop = Pad.ModalHeaderTop
    val ModalTitleStart = Pad.ModalTitleStart
    val SplashSecondaryButtonHeight = Comp.ButtonSecondary
    val SplashPrimaryButtonHeight = Comp.ButtonPrimary
}
