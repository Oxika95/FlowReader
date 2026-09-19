package com.personal.flowreader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    private val pendingShare = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isShareIntent(intent)) {
            pendingShare.value = Intent(intent)
        }
        enableEdgeToEdge()
        setContent {
            val openVm: OpenBookViewModel = viewModel()
            val libraryVm: LibraryViewModel = viewModel()
            val openUi by openVm.ui.collectAsState()
            val shareIntent by pendingShare.collectAsState()

            LaunchedEffect(openUi.orientation) {
                requestedOrientation = when (openUi.orientation) {
                    ReaderOrientation.Auto -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    ReaderOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    ReaderOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            FlowTheme(openUi.theme, openUi.accentHue) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    val nav = rememberNavController()

                    LaunchedEffect(shareIntent) {
                        val incoming = shareIntent ?: return@LaunchedEffect
                        if (libraryVm.handleShareIntent(incoming)) {
                            pendingShare.value = null
                            setIntent(Intent(this@MainActivity, MainActivity::class.java))
                            nav.navigate("library") {
                                launchSingleTop = true
                                restoreState = true
                            }
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
                                onTheme = { openVm.setTheme(it) },
                                onAccentHue = { openVm.setAccentHue(it) },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isShareIntent(intent)) {
            pendingShare.value = Intent(intent)
        }
    }

    private fun isShareIntent(intent: Intent?): Boolean =
        intent?.action == Intent.ACTION_SEND
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
        fontScale = openUi.fontScale,
        fontFamily = openUi.fontFamily,
        lineSpacing = openUi.lineSpacing,
        orientation = openUi.orientation,
        onTheme = { openVm.setTheme(it) },
        onAccentHue = { openVm.setAccentHue(it) },
        onFontScale = { openVm.setFontScale(it) },
        onFontFamily = { openVm.setFontFamily(it) },
        onLineSpacing = { openVm.setLineSpacing(it) },
        onOrientation = { openVm.setOrientation(it) },
        onBack = onBack,
        onAdvanceQue = onAdvanceQue,
    )
}
