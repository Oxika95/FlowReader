package com.personal.flowreader.ui.reader


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import com.personal.flowreader.data.AccentHue
import com.personal.flowreader.data.FilterApplyResult
import com.personal.flowreader.data.FilterMatchType
import com.personal.flowreader.data.FilterRule
import com.personal.flowreader.data.FilterScope
import com.personal.flowreader.data.ReaderFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.data.TextFilters
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineOption
import com.personal.flowreader.data.TtsPrefs
import com.personal.flowreader.data.TtsVoiceOption
import com.personal.flowreader.data.UiScale
import com.personal.flowreader.ui.common.rememberBookCover
import com.personal.flowreader.ui.settings.AppSettingsOverlay
import com.personal.flowreader.ui.settings.AppearanceSettingsCallbacks
import com.personal.flowreader.ui.settings.AppearanceSettingsState
import com.personal.flowreader.ui.settings.FilterSettingsCallbacks
import com.personal.flowreader.ui.settings.FilterSettingsState
import com.personal.flowreader.ui.settings.ModalHeaderRow
import com.personal.flowreader.ui.settings.SettingsToggleRow
import com.personal.flowreader.ui.settings.TtsSettingsCallbacks
import com.personal.flowreader.ui.settings.TtsSettingsState
import com.personal.flowreader.ui.theme.FlowTokens
import com.personal.flowreader.ui.theme.accentPrimary
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import com.personal.flowreader.ui.chrome.FloatingPanel
import com.personal.flowreader.ui.chrome.ReaderContentEndPadding
import com.personal.flowreader.ui.chrome.ReaderContentStartPadding
import com.personal.flowreader.ui.chrome.ReaderPanelFeather
import com.personal.flowreader.ui.chrome.ReaderPanelSurface
import com.personal.flowreader.ui.chrome.ReaderPanelShape


@Composable
internal fun TitleBannerCard(
    visible: Boolean,
    title: String,
    chapter: String,
    progress: Float,
    storedPath: String,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val cover by rememberBookCover(storedPath, maxEdge = 512)
    val cardBg = MaterialTheme.colorScheme.background
    val canBlur = Build.VERSION.SDK_INT >= 31
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
    ) {
        ReaderPanelSurface(
            modifier = Modifier.fillMaxWidth(),
            matchReaderWidth = true,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                BannerCoverUnderlay(
                    cover = cover,
                    blur = canBlur,
                    cardBg = cardBg,
                    modifier = Modifier
                        .matchParentSize()
                        .padding(bottom = FlowTokens.Comp.ProgressBar)
                        // Keep cover clear of back / settings hit targets.
                        .padding(horizontal = BannerCoverSideInset),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FlowTokens.Space.S, vertical = FlowTokens.Space.S)
                        .padding(bottom = FlowTokens.Comp.ProgressBar),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(FlowTokens.Icon.Hero),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(FlowTokens.Icon.M),
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = FlowTokens.Space.XS),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val textShadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            offset = Offset(0f, 1f),
                            blurRadius = 6f,
                        )
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            chapter,
                            style = MaterialTheme.typography.bodySmall.copy(shadow = textShadow),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                    // Match back control width so title stays centered on the card.
                    IconButton(
                        onClick = onSettings,
                        modifier = Modifier.size(FlowTokens.Icon.Hero),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(FlowTokens.Comp.ProgressBar)
                        .clip(
                            RoundedCornerShape(
                                bottomStart = FlowTokens.Radius.L,
                                bottomEnd = FlowTokens.Radius.L,
                            ),
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                )
            }
        }
    }
}

/** Horizontal inset so the cover band ends before the side icon buttons (matches primary control). */
private val BannerCoverSideInset = FlowTokens.Comp.ButtonPrimary

@Composable
private fun BannerCoverUnderlay(
    cover: ImageBitmap?,
    blur: Boolean,
    cardBg: Color,
    modifier: Modifier = Modifier,
) {
    if (cover == null) return
    Box(modifier) {
        Image(
            bitmap = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .then(if (blur) Modifier.blur(FlowTokens.CoverBlur) else Modifier),
        )
        // Soft side fades into the card; light center wash for title contrast.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to cardBg,
                            0.18f to cardBg.copy(alpha = 0.35f),
                            0.50f to cardBg.copy(alpha = 0.45f),
                            0.82f to cardBg.copy(alpha = 0.35f),
                            1f to cardBg,
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun MediaControlCard(
    visible: Boolean,
    playing: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToc: () -> Unit,
    onScrollLock: () -> Unit,
) {
    val playSize = FlowTokens.Comp.Fab
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        // Outer box sizes to the tall play control; the card itself stays short.
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            FloatingPanel(
                contentPadding = PaddingValues(
                    horizontal = FlowTokens.Space.S,
                    vertical = FlowTokens.Radius.None,
                ),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onPrev) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous sentence")
                        }
                        // Horizontal slot for the overlapping play control; does not set card height.
                        Spacer(Modifier.width(playSize))
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = "Next sentence")
                        }
                    }
                    IconButton(
                        onClick = onToc,
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Toc, contentDescription = "Contents")
                    }
                    IconButton(
                        onClick = onScrollLock,
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock scroll to TTS")
                    }
                }
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(
                            horizontal = FlowTokens.Space.XS,
                            vertical = FlowTokens.Space.XS,
                        ),
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    if (playing) onPause() else onPlay()
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(playSize),
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    modifier = Modifier.size(FlowTokens.Icon.XL),
                )
            }
        }
    }
}

/**
 * Floating unlock control that sits in the same bottom-end slot as the media card's
 * scroll-lock button while chrome is hidden in scroll-lock mode.
 */
@Composable
internal fun ScrollLockUnlockButton(
    visible: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playSize = FlowTokens.Comp.Fab
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(playSize),
            contentAlignment = Alignment.Center,
        ) {
            // Match MediaControlCard: reader gutter + panel content pad to the icon slot.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = ReaderContentStartPadding + FlowTokens.Space.S)
                    .size(FlowTokens.Icon.Hero)
                    .pointerInput(onUnlock) {
                        detectTapGestures(onDoubleTap = { onUnlock() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(FlowTokens.Comp.ButtonSecondary)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape,
                        ),
                )
                Icon(
                    Icons.Default.LockOpen,
                    contentDescription = "Double-tap to unlock scroll",
                )
            }
        }
    }
}
