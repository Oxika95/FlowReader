package com.personal.flowreader.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.ui.common.BookHeroPrimaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButton
import com.personal.flowreader.ui.common.BookHeroSecondaryButtonRow
import com.personal.flowreader.ui.common.BookHeroSplashButtons
import com.personal.flowreader.ui.common.BookHeroSplashShell
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.theme.FlowTokens

/**
 * Pared-down Files media card: cover + meta + Open / Share / Remove.
 * Same shell language as the Royal Road story splash, without RR-only actions.
 */
@Composable
fun FilesBookSplash(
    book: ProgressEntity?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val context = LocalContext.current
    val cover by rememberBookCover(book, maxEdge = 768)
    BookHeroSplashShell(
        visible = book != null,
        onDismiss = onDismiss,
        art = cover,
        coverBandPadding = PaddingValues(
            start = FlowTokens.Space.L,
            end = FlowTokens.Space.L,
            top = FlowTokens.Space.XS,
            bottom = FlowTokens.Space.M,
        ),
        placeholder = {
            Text(
                book?.title?.firstOrNull()?.uppercase()?.toString() ?: "?",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.Center),
            )
        },
        coverBand = coverBand@{
            val row = book ?: return@coverBand
            val onCoverMuted = FlowTokens.CoverMutedWhite
            val sourceLabel = runCatching { BookSource.valueOf(row.sourceKind).label }
                .getOrDefault(BookSource.Imported.label)
            val progressPct = (row.readingProgress.coerceIn(0f, 1f) * 100f).toInt()
            Text(
                row.title,
                style = MaterialTheme.typography.titleLarge.copy(
                    lineHeight = MaterialTheme.typography.titleLarge.fontSize *
                        FlowTokens.SplashTitleLineHeight,
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                libraryBookSubtitle(row),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.padding(top = FlowTokens.Space.XS),
            )
            Text(
                "$sourceLabel · $progressPct% read",
                style = MaterialTheme.typography.bodySmall,
                color = onCoverMuted,
                modifier = Modifier.padding(top = FlowTokens.Space.XS),
            )
            LinearProgressIndicator(
                progress = { row.readingProgress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FlowTokens.Space.M)
                    .height(FlowTokens.Comp.ProgressBar),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.2f),
            )
        },
    ) {
        val row = book ?: return@BookHeroSplashShell
        BookHeroSplashButtons {
            BookHeroSecondaryButtonRow {
                BookHeroSecondaryButton(
                    onClick = { shareBook(context, row) },
                    enabled = !busy,
                    label = "Share",
                )
                BookHeroSecondaryButton(
                    onClick = {
                        onRemove(row.bookId)
                        onDismiss()
                    },
                    enabled = !busy,
                    label = "Remove",
                )
            }
            BookHeroPrimaryButton(
                onClick = {
                    onOpen(row.bookId)
                    onDismiss()
                },
                enabled = !busy,
                label = "Open",
            )
        }
    }
}

private fun shareBook(context: android.content.Context, book: ProgressEntity) {
    val uri = book.sourceUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
    val send = Intent(Intent.ACTION_SEND).apply {
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, book.title)
            putExtra(Intent.EXTRA_TEXT, book.title)
        }
    }
    context.startActivity(Intent.createChooser(send, "Share book"))
}
