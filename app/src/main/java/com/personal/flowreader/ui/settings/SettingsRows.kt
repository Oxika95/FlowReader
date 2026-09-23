package com.personal.flowreader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.personal.flowreader.ui.theme.FlowTokens

/** Title + Close icon row used by settings / TOC / add / editor overlays. */
@Composable
fun ModalHeaderRow(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    closeContentDescription: String = "Close",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = FlowTokens.ModalHeaderStart,
                end = FlowTokens.ModalHeaderEnd,
                top = FlowTokens.ModalHeaderTop,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .padding(start = FlowTokens.ModalTitleStart),
        )
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = closeContentDescription)
        }
    }
}

/** Title + subtitle + Switch row (Audio / Sharing / filter editors). */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val subtitleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (enabled) {
                    Modifier.clickable { onCheckedChange(!checked) }
                } else {
                    Modifier
                },
            )
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** SettingsLabel + Slider with optional leading/trailing captions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: String? = null,
    startCaption: String? = null,
    endCaption: String? = null,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    SettingsLabel(text = label)
    if (valueLabel != null) {
        Text(
            valueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (startCaption != null) {
            Text(startCaption, style = MaterialTheme.typography.labelSmall)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = FlowTokens.Space.S),
        )
        if (endCaption != null) {
            Text(endCaption, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SettingsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = FlowTokens.Space.S),
    )
}
