package com.personal.flowreader.ui.design.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import com.personal.flowreader.ui.theme.FlowMotion
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.FlowType

/*
 * Settings-style controls used inside fullscreen card bodies. Every settings tab, editor and
 * sheet composes these instead of raw Material widgets so spacing, type and disabled states
 * match. See docs/ui-system/elements.md.
 */

/** Section heading inside a card body ("Voice", "Playback"). */
@Composable
fun FlowSection(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = FlowType.sectionTitle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(top = FlowTokens.Space.S),
    )
}

/** Small label above a control (slider, field, chip row). */
@Composable
fun FlowLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = FlowType.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = FlowTokens.Space.XS),
    )
}

/** Secondary explanatory text. */
@Composable
fun FlowHint(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        style = FlowType.hint,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Title + optional subtitle + Switch. The whole row toggles. */
@Composable
fun FlowToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val alpha = if (enabled) 1f else FlowTokens.Alpha.Disabled
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = FlowTokens.Space.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = FlowTokens.Space.M)) {
            Text(title, style = FlowType.rowTitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = FlowType.hint,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        // Row handles the toggle; the Switch is visual only.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Label + slider. [valueLabel] shows at the end of the label line; captions sit either side of
 * the track.
 */
@Composable
fun FlowSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: String? = null,
    startCaption: String? = null,
    endCaption: String? = null,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowLabel(label, Modifier.weight(1f))
            if (valueLabel != null) {
                Text(
                    valueLabel,
                    style = FlowType.label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(FlowTokens.Comp.SliderValueWidth)
                        .padding(bottom = FlowTokens.Space.XS),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (startCaption != null) Text(startCaption, style = FlowType.label)
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = FlowTokens.Space.S),
            )
            if (endCaption != null) Text(endCaption, style = FlowType.label)
        }
    }
}

/** Single-line (by default) outlined field with the Field shape. */
@Composable
fun FlowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        shape = FlowTokens.Shape.Field,
    )
}

/** Read-only field that opens a menu of [options]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> FlowDropdownRow(
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        if (label != null) FlowLabel(label)
        ExposedDropdownMenuBox(
            expanded = open && enabled,
            onExpandedChange = { if (enabled) open = it },
        ) {
            OutlinedTextField(
                value = optionLabel(selected),
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) },
                shape = FlowTokens.Shape.Field,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled),
            )
            ExposedDropdownMenu(expanded = open && enabled, onDismissRequest = { open = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

/** Wrapping row of chips with standard spacing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) { content() }
}

/** Selectable chip for [FlowChipRow]. */
@Composable
fun FlowChip(label: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = FlowType.label) },
    )
}

/** Single-select chip group: one chip per option. */
@Composable
fun <T> FlowChoiceChips(
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowChipRow(modifier) {
        options.forEach { option ->
            FlowChip(optionLabel(option), option == selected, { onSelect(option) }, enabled)
        }
    }
}

/** Inline expand/collapse region driven by the caller's [visible] state. */
@Composable
fun FlowCollapsibleBox(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = FlowMotion.expandEnter(),
        exit = FlowMotion.expandExit(),
    ) { content() }
}

/** Header row that expands/collapses [content]. */
@Composable
fun FlowCollapsible(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") { expanded = !expanded }
                .padding(vertical = FlowTokens.Space.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = FlowType.subsectionTitle)
                if (subtitle.isNotBlank()) FlowHint(subtitle)
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(start = FlowTokens.Space.S),
            )
        }
        AnimatedVisibility(expanded, enter = FlowMotion.expandEnter(), exit = FlowMotion.expandExit()) {
            Column(verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S), content = content)
        }
    }
}
