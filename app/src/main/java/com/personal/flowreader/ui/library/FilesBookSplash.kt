package com.personal.flowreader.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.personal.flowreader.data.BookSource
import com.personal.flowreader.data.ProgressEntity
import com.personal.flowreader.ui.reader.ReaderModalScaffold
import com.personal.flowreader.ui.reader.ReaderPanelFeather
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val art by produceState<ImageBitmap?>(initialValue = null, book?.bookId, book?.storedPath) {
        value = book?.let { row ->
            withContext(Dispatchers.IO) {
                loadLibraryCoverBitmap(row, maxEdge = 768)?.asImageBitmap()
            }
        }
    }
    val canBlur = android.os.Build.VERSION.SDK_INT >= 31
    val cardBg = MaterialTheme.colorScheme.background
    ReaderModalScaffold(
        visible = book != null,
        contentPadding = PaddingValues(FlowTokens.Radius.None),
        onDismiss = onDismiss,
        feather = ReaderPanelFeather,
        scrimAlpha = FlowTokens.ScrimHero,
    ) {
        val row = book ?: return@ReaderModalScaffold
        val onCoverMuted = FlowTokens.CoverMutedWhite
        val sourceLabel = runCatching { BookSource.valueOf(row.sourceKind).label }
            .getOrDefault(BookSource.Imported.label)
        val progressPct = (row.readingProgress.coerceIn(0f, 1f) * 100f).toInt()
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        art?.let { it.width.toFloat() / it.height.toFloat() }
                            ?: FlowTokens.CoverAspect,
                    ),
            ) {
                val cover = art
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        alignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (canBlur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            row.title.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.weight(1f))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(FlowTokens.CoverGradientHeight)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        FlowTokens.CoverBandBlack,
                                    ),
                                ),
                            ),
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(FlowTokens.CoverBandBlack)
                            .padding(
                                start = FlowTokens.Space.L,
                                end = FlowTokens.Space.L,
                                top = FlowTokens.Space.XS,
                                bottom = FlowTokens.Space.M,
                            ),
                    ) {
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
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(cardBg)
                    .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.S),
                verticalArrangement = Arrangement.spacedBy(FlowTokens.SplashActionRowSpacing),
            ) {
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FlowTokens.Space.XS),
                        horizontalArrangement = Arrangement.spacedBy(FlowTokens.SplashActionRowSpacing),
                    ) {
                        OutlinedButton(
                            onClick = { shareBook(context, row) },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(FlowTokens.SplashSecondaryButtonHeight),
                            contentPadding = PaddingValues(
                                horizontal = FlowTokens.Space.XS,
                                vertical = FlowTokens.Radius.None,
                            ),
                        ) {
                            Text("Share", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = {
                                onRemove(row.bookId)
                                onDismiss()
                            },
                            enabled = !busy,
                            modifier = Modifier
                                .weight(1f)
                                .height(FlowTokens.SplashSecondaryButtonHeight),
                            contentPadding = PaddingValues(
                                horizontal = FlowTokens.Space.XS,
                                vertical = FlowTokens.Radius.None,
                            ),
                        ) {
                            Text("Remove", maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Button(
                        onClick = {
                            onOpen(row.bookId)
                            onDismiss()
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = FlowTokens.Space.XS)
                            .height(FlowTokens.SplashPrimaryButtonHeight),
                        contentPadding = PaddingValues(
                            horizontal = FlowTokens.Space.L,
                            vertical = FlowTokens.Radius.None,
                        ),
                    ) {
                        Text("Open")
                    }
                }
            }
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
