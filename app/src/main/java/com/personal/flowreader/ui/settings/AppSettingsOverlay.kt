package com.personal.flowreader.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.personal.flowreader.ui.reader.ReaderModalScaffold
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Shared modal shell for app settings (library + reader). Callers supply tab content.
 */
@Composable
fun AppSettingsOverlay(
    visible: Boolean,
    title: String = "Settings",
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
        scrimAlpha = FlowTokens.ScrimStandard,
    ) {
        Row(
            modifier = Modifier
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
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }
        content()
    }
}
