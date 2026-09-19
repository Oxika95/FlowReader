package com.personal.flowreader

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
import com.personal.flowreader.ui.open.OpenBookScreen
import com.personal.flowreader.ui.open.OpenBookViewModel
import com.personal.flowreader.ui.reader.ReaderScreen
import com.personal.flowreader.ui.reader.ReaderViewModel
import com.personal.flowreader.ui.theme.FlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val openVm: OpenBookViewModel = viewModel()
            val openUi by openVm.ui.collectAsState()

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
                    NavHost(
                        navController = nav,
                        startDestination = "open",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable("open") {
                            OpenBookScreen(
                                vm = openVm,
                                onOpened = { id -> nav.navigate("reader/$id") },
                            )
                        }
                        composable(
                            "reader/{bookId}",
                            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
                        ) {
                            val readerVm: ReaderViewModel = viewModel()
                            ReaderScreen(
                                vm = readerVm,
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
                                onBack = { nav.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
