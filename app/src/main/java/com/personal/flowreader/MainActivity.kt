package com.personal.flowreader

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personal.flowreader.data.ReaderOrientation
import com.personal.flowreader.ui.library.LibraryScreen
import com.personal.flowreader.ui.library.LibraryViewModel
import com.personal.flowreader.ui.open.OpenBookViewModel
import com.personal.flowreader.ui.reader.ReaderScreen
import com.personal.flowreader.ui.reader.ReaderViewModel
import com.personal.flowreader.ui.theme.FlowTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingIncoming = MutableStateFlow<Intent?>(null)
    private var pendingNotificationCallback: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingNotificationCallback?.invoke()
        pendingNotificationCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isIncomingIntent(intent)) {
            pendingIncoming.value = Intent(intent)
        }
        (application as FlowApp).tts.setNotificationPermissionAsker { onDone ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                onDone()
                return@setNotificationPermissionAsker
            }
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                onDone()
                return@setNotificationPermissionAsker
            }
            pendingNotificationCallback = onDone
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        enableEdgeToEdge()
        setContent {
            val openVm: OpenBookViewModel = viewModel()
            val libraryVm: LibraryViewModel = viewModel()
            val openUi by openVm.ui.collectAsState()
            val libraryUi by libraryVm.ui.collectAsState()
            val incoming by pendingIncoming.collectAsState()

            LaunchedEffect(openUi.orientation) {
                requestedOrientation = when (openUi.orientation) {
                    ReaderOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ReaderOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ReaderOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            FlowTheme(openUi.theme, openUi.accentHue, openUi.uiScale) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    val nav = rememberNavController()

                    LaunchedEffect(incoming) {
                        val intent = incoming ?: return@LaunchedEffect
                        if (libraryVm.handleIncomingIntent(intent)) {
                            pendingIncoming.value = null
                            setIntent(Intent(this@MainActivity, MainActivity::class.java))
                        }
                    }

                    LaunchedEffect(libraryUi.pendingOpenBookId) {
                        val id = libraryUi.pendingOpenBookId ?: return@LaunchedEffect
                        libraryVm.consumePendingOpen()
                        nav.navigate("reader/$id") {
                            launchSingleTop = true
                        }
                    }

                    NavHost(
                        navController = nav,
                        startDestination = "library",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable("library") {
                            LibraryScreen(
                                vm = libraryVm,
                                themeMode = openUi.theme,
                                accentHue = openUi.accentHue,
                                uiScale = openUi.uiScale,
                                fontScale = openUi.fontScale,
                                fontFamily = openUi.fontFamily,
                                lineSpacing = openUi.lineSpacing,
                                justifyText = openUi.justifyText,
                                orientation = openUi.orientation,
                                showChapterHeadingsInBody = openUi.showChapterHeadingsInBody,
                                keepScreenAwake = openUi.keepScreenAwake,
                                onTheme = { openVm.setTheme(it) },
                                onAccentHue = { openVm.setAccentHue(it) },
                                onUiScale = { openVm.setUiScale(it) },
                                onFontScale = { openVm.setFontScale(it) },
                                onFontFamily = { openVm.setFontFamily(it) },
                                onLineSpacing = { openVm.setLineSpacing(it) },
                                onJustifyText = { openVm.setJustifyText(it) },
                                onOrientation = { openVm.setOrientation(it) },
                                onShowChapterHeadingsInBody = { openVm.setShowChapterHeadingsInBody(it) },
                                onKeepScreenAwake = { openVm.setKeepScreenAwake(it) },
                                onOpenBook = { id -> nav.navigate("reader/$id") },
                                onOpenQue = { bookId, queId ->
                                    nav.navigate("reader/$bookId/que/$queId")
                                },
                            )
                        }
                        composable(
                            route = "reader/{bookId}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                            ),
                        ) {
                            ReaderRoute(
                                openUi = openUi,
                                openVm = openVm,
                                queId = null,
                                onBack = { nav.popBackStack() },
                                onAdvanceQue = { _, _ -> },
                            )
                        }
                        composable(
                            route = "reader/{bookId}/que/{queId}?autoPlay={autoPlay}",
                            arguments = listOf(
                                navArgument("bookId") { type = NavType.StringType },
                                navArgument("queId") { type = NavType.StringType },
                                navArgument("autoPlay") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                            ),
                        ) { entry ->
                            val queId = entry.arguments?.getString("queId")
                            ReaderRoute(
                                openUi = openUi,
                                openVm = openVm,
                                queId = queId,
                                onBack = { nav.popBackStack() },
                                onAdvanceQue = { nextBookId, nextQueId ->
                                    nav.navigate("reader/$nextBookId/que/$nextQueId?autoPlay=1") {
                                        popUpTo("library") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        (application as FlowApp).tts.setNotificationPermissionAsker(null)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isIncomingIntent(intent)) {
            pendingIncoming.value = Intent(intent)
        }
    }

    private fun isIncomingIntent(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND || intent?.action == Intent.ACTION_VIEW
}

@androidx.compose.runtime.Composable
private fun ReaderRoute(
    openUi: com.personal.flowreader.ui.open.OpenUi,
    openVm: OpenBookViewModel,
    queId: String?,
    onBack: () -> Unit,
    onAdvanceQue: (String, String) -> Unit,
) {
    val readerVm: ReaderViewModel = viewModel()
    ReaderScreen(
        vm = readerVm,
        queId = queId,
        themeMode = openUi.theme,
        accentHue = openUi.accentHue,
        uiScale = openUi.uiScale,
        fontScale = openUi.fontScale,
        fontFamily = openUi.fontFamily,
        lineSpacing = openUi.lineSpacing,
        justifyText = openUi.justifyText,
        orientation = openUi.orientation,
        showChapterHeadingsInBody = openUi.showChapterHeadingsInBody,
        keepScreenAwake = openUi.keepScreenAwake,
        onTheme = { openVm.setTheme(it) },
        onAccentHue = { openVm.setAccentHue(it) },
        onUiScale = { openVm.setUiScale(it) },
        onFontScale = { openVm.setFontScale(it) },
        onFontFamily = { openVm.setFontFamily(it) },
        onLineSpacing = { openVm.setLineSpacing(it) },
        onJustifyText = { openVm.setJustifyText(it) },
        onOrientation = { openVm.setOrientation(it) },
        onShowChapterHeadingsInBody = { openVm.setShowChapterHeadingsInBody(it) },
        onKeepScreenAwake = { openVm.setKeepScreenAwake(it) },
        onBack = onBack,
        onAdvanceQue = onAdvanceQue,
    )
}
