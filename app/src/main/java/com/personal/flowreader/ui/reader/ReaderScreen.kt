package com.personal.flowreader.ui.reader

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.personal.flowreader.data.BlockKind
import com.personal.flowreader.data.Locus
import com.personal.flowreader.data.ThemeMode
import com.personal.flowreader.data.TtsEngineKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private val LocusGray = Color(0x52808080)
private val EdgeFade = 96.dp

@Composable
fun ReaderScreen(
    vm: ReaderViewModel,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onBack: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val tts by vm.tts.state.collectAsState()
    val doc = ui.doc
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var programmatic by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(false) }
    var chromeTick by remember { mutableStateOf(0) }
    val items = doc?.items.orEmpty()

    ImmersiveSystemBars(enabled = true)

    val locusIndex by remember(ui.locus, tts.sentence, doc) {
        derivedStateOf {
            val s = tts.sentence
            if (s != null) {
                items.indexOfFirst { it.chapterIndex == s.chapterIndex && it.blockIndex == s.blockIndex }
                    .coerceAtLeast(0)
            } else {
                ui.locus.flatIndex(doc ?: return@derivedStateOf 0)
            }
        }
    }

    val onScreen by remember {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.any { it.index == locusIndex }
        }
    }
    val pinTop by remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            locusIndex < first
        }
    }

    LaunchedEffect(tts.following, locusIndex, tts.playing) {
        if (tts.following && tts.playing && items.isNotEmpty()) {
            programmatic = true
            listState.animateScrollToItem(locusIndex.coerceIn(0, items.lastIndex))
            programmatic = false
        }
    }

    val scrolledAway = rememberUpdatedState(vm.tts)
    LaunchedEffect(listState, tts.playing) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { moving ->
                if (moving && !programmatic && tts.playing) {
                    scrolledAway.value.userScrolledAway()
                }
            }
    }

    LaunchedEffect(tts.sentence) {
        tts.sentence?.let { s ->
            vm.onLocus(Locus(s.chapterIndex, s.blockIndex, s.start))
        }
    }

    LaunchedEffect(chromeVisible, chromeTick) {
        if (!chromeVisible) return@LaunchedEffect
        delay(4_000)
        chromeVisible = false
    }

    fun bumpChrome() {
        chromeVisible = true
        chromeTick++
    }

    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        when {
            ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            ui.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(ui.error!!, color = MaterialTheme.colorScheme.error)
            }
            doc != null -> {
                val fadePx = with(LocalDensity.current) { EdgeFade.toPx() }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val stop = (fadePx / size.height).coerceIn(0f, 0.5f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    stop to Color.Black,
                                    1f - stop to Color.Black,
                                    1f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 28.dp,
                        bottom = 28.dp,
                    ),
                ) {
                    itemsIndexed(items, key = { _, it -> it.block.id }) { index, item ->
                        val active = index == locusIndex
                        val sentence = tts.sentence
                        val highlight = active && sentence != null &&
                            sentence.chapterIndex == item.chapterIndex &&
                            sentence.blockIndex == item.blockIndex
                        val text = buildAnnotatedString {
                            val raw = item.block.text
                            if (highlight) {
                                val start = sentence!!.start.coerceIn(0, raw.length)
                                val end = sentence.end.coerceIn(start, raw.length)
                                append(raw.substring(0, start))
                                withStyle(
                                    SpanStyle(
                                        background = Color(0x66FFC107),
                                        fontWeight = FontWeight.Medium,
                                    ),
                                ) {
                                    append(raw.substring(start, end))
                                }
                                append(raw.substring(end))
                            } else {
                                append(raw)
                            }
                        }
                        val style = when (item.block.kind) {
                            BlockKind.Heading -> MaterialTheme.typography.headlineSmall
                            BlockKind.Quote -> MaterialTheme.typography.bodyLarge
                            BlockKind.Paragraph -> MaterialTheme.typography.bodyLarge
                        }
                        Text(
                            text,
                            style = style,
                            color = colors.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (active) LocusGray else Color.Transparent,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable {
                                    val loc = Locus(item.chapterIndex, item.blockIndex, 0)
                                    vm.onLocus(loc)
                                    vm.tts.jumpTo(loc)
                                    bumpChrome()
                                }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (!chromeVisible) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable { bumpChrome() },
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .clickable { bumpChrome() },
                    )
                }

                if (chromeVisible) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        color = colors.surface.copy(alpha = 0.94f),
                        contentColor = colors.onSurface,
                    ) {
                        Row(
                            Modifier
                                .statusBarsPadding()
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Text(
                                ui.title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = {
                                onCycleTheme()
                                bumpChrome()
                            }) {
                                Text(themeMode.name)
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                        TtsBar(
                            playing = tts.playing,
                            engine = tts.engine,
                            speed = tts.speed,
                            error = tts.error,
                            onInteract = { bumpChrome() },
                            onPlay = { vm.tts.play() },
                            onPause = { vm.tts.pause() },
                            onPrev = { vm.tts.skipPrev() },
                            onNext = { vm.tts.skipNext() },
                            onEngine = {
                                vm.tts.setEngine(
                                    if (tts.engine == TtsEngineKind.System) {
                                        TtsEngineKind.Edge
                                    } else {
                                        TtsEngineKind.System
                                    },
                                )
                            },
                            onSpeed = {
                                val options = floatArrayOf(0.8f, 1f, 1.25f, 1.5f, 2f)
                                val i = options.indexOfFirst { it == tts.speed }
                                vm.tts.setSpeed(options[(i + 1) % options.size])
                            },
                        )
                    }
                }
            }
        }

        val showPin = tts.playing && !tts.following && !onScreen && tts.snippet.isNotBlank()
        if (showPin) {
            val align = if (pinTop) Alignment.TopCenter else Alignment.BottomCenter
            Card(
                modifier = Modifier
                    .align(align)
                    .padding(horizontal = 16.dp, vertical = if (chromeVisible) 88.dp else 24.dp)
                    .fillMaxWidth()
                    .clickable {
                        vm.tts.followAgain()
                        scope.launch {
                            programmatic = true
                            listState.animateScrollToItem(locusIndex.coerceIn(0, items.lastIndex))
                            programmatic = false
                        }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(
                    tts.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ImmersiveSystemBars(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(enabled) {
        val window = (view.context as Activity).window
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.systemBarsBehavior
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previous
        }
    }
}

@Composable
private fun TtsBar(
    playing: Boolean,
    engine: TtsEngineKind,
    speed: Float,
    error: String?,
    onInteract: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onEngine: () -> Unit,
    onSpeed: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    onInteract()
                    onPrev()
                }) { Icon(Icons.Default.SkipPrevious, "Previous") }
                IconButton(onClick = {
                    onInteract()
                    if (playing) onPause() else onPlay()
                }) {
                    Icon(
                        if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (playing) "Pause" else "Play",
                    )
                }
                IconButton(onClick = {
                    onInteract()
                    onNext()
                }) { Icon(Icons.Default.SkipNext, "Next") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    onInteract()
                    onEngine()
                }) { Text(if (engine == TtsEngineKind.Edge) "Edge" else "System") }
                TextButton(onClick = {
                    onInteract()
                    onSpeed()
                }) { Text("${speed}x") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
