package com.personal.flowreader.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.personal.flowreader.ui.theme.FlowTokens
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val GITHUB_REPO_URL = "https://github.com/Oxika95/FlowReader"
private const val GITHUB_RELEASES_URL = "https://github.com/Oxika95/FlowReader/releases"
private const val GITHUB_LATEST_API =
    "https://api.github.com/repos/Oxika95/FlowReader/releases/latest"

@Composable
internal fun AboutSettingsTab(
    debugEnabled: Boolean,
    onDebugEnabled: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "?" }
    }
    var latestRelease by remember { mutableStateOf<String?>(null) }
    var latestError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        latestError = null
        val result = withContext(Dispatchers.IO) { fetchLatestReleaseTag() }
        latestRelease = result.getOrNull()
        latestError = result.exceptionOrNull()?.message
    }

    // Scroll is owned by ReaderModalScaffold — do not nest verticalScroll here.
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Flow Reader",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(FlowTokens.Space.XS))
        Text(
            "Personal Android eReader with vertical flow reading and TTS.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(FlowTokens.Space.L))
        AboutLinkRow(
            label = "Version",
            value = versionName,
        )
        AboutLinkRow(
            label = "Latest release",
            value = latestRelease ?: latestError ?: "Checking…",
            onClick = latestRelease?.let {
                { openUrl(context, GITHUB_RELEASES_URL) }
            },
        )
        AboutLinkRow(
            label = "GitHub",
            value = "Oxika95/FlowReader",
            onClick = { openUrl(context, GITHUB_REPO_URL) },
        )

        Spacer(Modifier.height(FlowTokens.Space.L))
        SettingsToggleRow(
            title = "Debug mode",
            subtitle = "Show a draggable bug bubble to view and save synthesizer logs",
            checked = debugEnabled,
            onCheckedChange = onDebugEnabled,
        )
    }
}

@Composable
private fun AboutLinkRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(vertical = FlowTokens.Space.S),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun fetchLatestReleaseTag(): Result<String> = runCatching {
    val conn = (URL(GITHUB_LATEST_API).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 8_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "FlowReader")
    }
    try {
        val code = conn.responseCode
        if (code !in 200..299) {
            error("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val tag = JSONObject(body).optString("tag_name").ifBlank {
            JSONObject(body).optString("name")
        }
        tag.ifBlank { error("No release tag") }
    } finally {
        conn.disconnect()
    }
}
