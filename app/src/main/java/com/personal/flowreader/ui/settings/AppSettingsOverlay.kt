package com.personal.flowreader.ui.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
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
    closeContentDescription: String = "Close settings",
    content: @Composable ColumnScope.() -> Unit,
) {
    ReaderModalScaffold(
        visible = visible,
        contentPadding = PaddingValues(bottom = FlowTokens.ModalOuterPadding),
        onDismiss = onDismiss,
        scrimAlpha = FlowTokens.ScrimStandard,
    ) {
        ModalHeaderRow(
            title = title,
            onDismiss = onDismiss,
            closeContentDescription = closeContentDescription,
        )
        content()
    }
}
