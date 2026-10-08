package com.personal.flowreader.ui.design.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.Box
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Standard icon button: 48dp target, [FlowTokens.Comp.TabIcon]-sized glyph unless [iconSize] set. */
@Composable
fun FlowIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    iconSize: androidx.compose.ui.unit.Dp = FlowTokens.Icon.L,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (tint == Color.Unspecified) androidx.compose.material3.LocalContentColor.current else tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/** Where a [FlowCircleButton] sits, which decides its colors. */
enum class FlowCircleStyle {
    /** On a dark cover band (media card rail). */
    OnCover,
    /** On the page (reader unlock control). */
    OnPage,
}

/**
 * Small circular toggle/action (36dp). [selected] fills with the accent.
 * Uses M3 Surface so ripple is clipped to the circle and semantics are set.
 */
@Composable
fun FlowCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    style: FlowCircleStyle = FlowCircleStyle.OnCover,
) {
    val accent = MaterialTheme.colorScheme.primary
    val bg = when {
        selected -> accent
        style == FlowCircleStyle.OnCover -> Color.Black.copy(alpha = FlowTokens.Alpha.CircleOnCover)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val tint = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        style == FlowCircleStyle.OnCover -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val ring = when {
        selected -> accent
        style == FlowCircleStyle.OnCover -> Color.White.copy(alpha = FlowTokens.Alpha.CircleRingOnCover)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = bg,
        border = BorderStroke(FlowTokens.Stroke.Hairline, ring),
        modifier = modifier
            .size(FlowTokens.Comp.CircleButton)
            .semantics {
                role = Role.Button
                if (selected) stateDescription = "On"
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = if (enabled) 1f else FlowTokens.Alpha.Disabled),
                modifier = Modifier.size(FlowTokens.Comp.CircleButtonIcon),
            )
        }
    }
}

/** The screen FAB. Place it in `FlowScreen(fab = { ... })`, never position it yourself. */
@Composable
fun FlowFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(FlowTokens.Comp.Fab),
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(FlowTokens.Comp.FabIcon))
    }
}

/**
 * Card-footer secondary button (outlined, 40dp), sharing a row with siblings. With
 * [onLongClick], holding past the long-press timeout runs it instead of [onClick].
 */
@Composable
fun RowScope.FlowSecondaryButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    var longFired by remember { mutableStateOf(false) }
    if (onLongClick != null) {
        val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
        val haptic = LocalHapticFeedback.current
        val longClick by rememberUpdatedState(onLongClick)
        LaunchedEffect(interaction, timeout) {
            interaction.interactions.collectLatest { i ->
                if (i is PressInteraction.Press) {
                    longFired = false
                    delay(timeout)
                    longFired = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    longClick()
                }
            }
        }
    }
    OutlinedButton(
        onClick = { if (longFired) longFired = false else onClick() },
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier
            .weight(1f)
            .height(FlowTokens.Comp.ButtonSecondary),
        colors = if (destructive) {
            ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.outlinedButtonColors()
        },
        contentPadding = PaddingValues(horizontal = FlowTokens.Space.XS, vertical = FlowTokens.Space.None),
    ) {
        Text(label, maxLines = 1, style = FlowType.action)
    }
}

/** Card-footer primary button (filled, full width, 48dp). One per card. */
@Composable
fun FlowPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(FlowTokens.Comp.ButtonPrimary),
        contentPadding = PaddingValues(horizontal = FlowTokens.Space.L, vertical = FlowTokens.Space.None),
    ) {
        Text(label, style = FlowType.action)
    }
}
