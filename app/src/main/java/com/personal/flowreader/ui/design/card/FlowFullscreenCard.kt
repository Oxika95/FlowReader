package com.personal.flowreader.ui.design.card

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.ui.design.layer.FlowOverlaySpec
import com.personal.flowreader.ui.design.layer.rememberFlowOverlay
import com.personal.flowreader.ui.design.surface.FlowSurface
import com.personal.flowreader.ui.design.surface.FlowSurfaceStyle
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/** Fullscreen card flavors. */
enum class FlowCardVariant {
    /** Settings, editors, sheets, lists: header + body + optional footer. */
    Standard,
    /** Cover-led cards (media card): no header, no body padding, darker scrim, feathered edge. */
    Hero,
    /** Short confirmations (replaces AlertDialog): narrow, header + message + footer. */
    Compact,
}

/**
 * The one fullscreen card. Registers with the window's overlay stack while [visible]:
 * opening another card on top stacks it (Back and scrim tap dismiss only the top card).
 *
 * Slots, top to bottom: [title] header (optional back arrow and close button), pinned [tabs],
 * scrolling body ([content]), pinned [footer] (use [FlowActionRow]).
 *
 * Height follows content; once it reaches the window the body scrolls.
 * Set [scrollable] false when the body is a lazy list; give it `Modifier.weight(1f, fill = false)`.
 * See `docs/ui-system/cards.md`.
 */
@Composable
fun FlowFullscreenCard(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    variant: FlowCardVariant = FlowCardVariant.Standard,
    dismissible: Boolean = true,
    onBack: (() -> Unit)? = null,
    showClose: Boolean = variant != FlowCardVariant.Hero,
    scrollable: Boolean = true,
    bodyPadding: PaddingValues = defaultBodyPadding(variant),
    bodySpacing: Arrangement.Vertical = Arrangement.spacedBy(FlowTokens.Space.S),
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    tabs: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = FlowOverlaySpec(
        scrimAlpha = if (variant == FlowCardVariant.Hero) FlowTokens.Scrim.Hero else FlowTokens.Scrim.Standard,
        dismissible = dismissible,
        onDismiss = onDismiss,
    )
    rememberFlowOverlay(visible = visible, spec = spec) {
        FlowSurface(
            style = if (variant == FlowCardVariant.Hero) FlowSurfaceStyle.Panel else FlowSurfaceStyle.Flat,
            modifier = modifier
                .widthIn(
                    max = if (variant == FlowCardVariant.Compact) {
                        FlowTokens.Comp.CompactCardMaxWidth
                    } else {
                        FlowTokens.Comp.CardMaxWidth
                    },
                )
                .fillMaxWidth()
                // Taps inside the card never reach the scrim.
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            Column(Modifier.fillMaxWidth()) {
                val hasHeader = title != null || onBack != null || showClose
                if (hasHeader) {
                    FlowCardHeader(
                        title = title.orEmpty(),
                        onClose = if (showClose && dismissible) onDismiss else null,
                        onBack = onBack,
                        actions = headerActions,
                    )
                }
                tabs?.invoke()
                val scroll = rememberScrollState()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .then(
                            if (scrollable) {
                                Modifier.verticalScroll(scroll, enabled = scroll.maxValue > 0)
                            } else {
                                Modifier
                            },
                        )
                        .padding(bodyPadding),
                    verticalArrangement = bodySpacing,
                    content = content,
                )
                if (footer != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = FlowTokens.Space.S,
                                end = FlowTokens.Space.S,
                                bottom = FlowTokens.Space.S,
                            ),
                    ) { footer() }
                }
            }
        }
    }
}

private fun defaultBodyPadding(variant: FlowCardVariant): PaddingValues = when (variant) {
    FlowCardVariant.Hero -> PaddingValues(FlowTokens.Space.None)
    else -> PaddingValues(
        start = FlowTokens.Pad.CardBody,
        end = FlowTokens.Pad.CardBody,
        top = FlowTokens.Space.XS,
        bottom = FlowTokens.Pad.CardBody,
    )
}

/**
 * Fullscreen card header: optional back arrow, title, [actions] (icon toggles, before Close),
 * close button (top-right, "Close").
 * Drawn by [FlowFullscreenCard]; use directly only inside custom hero content.
 */
@Composable
fun FlowCardHeader(
    title: String,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FlowTokens.Pad.HeaderStart,
                end = FlowTokens.Pad.HeaderEnd,
                top = FlowTokens.Pad.HeaderTop,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
        Text(
            title,
            style = FlowType.cardTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack == null) FlowTokens.Pad.HeaderTitleStart else FlowTokens.Space.None),
        )
        actions?.invoke(this)
        if (onClose != null) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        } else {
            // Keep header height stable without a close button.
            Spacer(Modifier.size(FlowTokens.Comp.ButtonPrimary))
        }
    }
}

/**
 * Standard card footer: [start] holds secondary/destructive actions, [end] holds
 * Cancel then the confirming action (rightmost). Use [FlowTextAction] for each action.
 */
@Composable
fun FlowActionRow(
    modifier: Modifier = Modifier,
    start: @Composable RowScope.() -> Unit = {},
    end: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        start()
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.XS),
            verticalAlignment = Alignment.CenterVertically,
            content = end,
        )
    }
}

/** Text action for [FlowActionRow]. [destructive] tints with `colorScheme.error`. */
@Composable
fun FlowTextAction(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        colors = if (destructive) {
            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.textButtonColors()
        },
    ) {
        Text(label, style = FlowType.action)
    }
}

/**
 * Compact confirmation card (replaces AlertDialog). Stacks over the card that opened it.
 * Pass an empty [dismissLabel] for an info card with a single confirm action.
 */
@Composable
fun FlowConfirmCard(
    visible: Boolean,
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    FlowFullscreenCard(
        visible = visible,
        onDismiss = onDismiss,
        title = title,
        variant = FlowCardVariant.Compact,
        footer = {
            FlowActionRow {
                if (dismissLabel.isNotEmpty()) FlowTextAction(dismissLabel, onDismiss)
                extraActions()
                FlowTextAction(confirmLabel, onConfirm, destructive = destructive)
            }
        },
    ) {
        Text(message, style = FlowType.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
