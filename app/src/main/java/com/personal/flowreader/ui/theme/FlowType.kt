package com.personal.flowreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Typography roles for the Flow UI system. Components use these, not raw M3 styles,
 * so a role changes in one place. See `docs/ui-system/tokens.md`.
 */
object FlowType {
    /** Fullscreen card header title. */
    val cardTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)

    /** Section heading inside a card body. */
    val sectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)

    /** Collapsible / sub-section heading. */
    val subsectionTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall

    val tabPrimary: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall

    val tabSecondary: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge

    /** Row title in lists and settings. */
    val rowTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyLarge

    val body: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodyMedium

    /** Secondary explanatory text; pair with `onSurfaceVariant`. */
    val hint: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.bodySmall

    /** Field / slider labels above a control. */
    val label: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelMedium

    val action: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.labelLarge

    /** Display card title (row layout). */
    val displayTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleMedium

    /** Display card title (tile layout, on dark band). */
    val tileTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)

    /** Media card title on the cover band. */
    val mediaTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() {
            val base = MaterialTheme.typography.titleLarge
            return base.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = base.fontSize * FlowTokens.SplashTitleLineHeight,
            )
        }

    /** Cover placeholder initial. */
    val placeholder: TextStyle
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.typography.headlineSmall
}
