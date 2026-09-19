package com.personal.flowreader.ui.open

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OpenBookScreen(
    vm: OpenBookViewModel,
    onOpened: (String) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openUri(uri, onOpened)
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Flow Reader", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Open a book. Vertical scroll, TTS, pin-card when you wander.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
            Button(
                onClick = { picker.launch(arrayOf("application/epub+zip", "text/plain", "*/*")) },
                enabled = !ui.busy,
            ) {
                Text("Open EPUB or TXT")
            }
            if (ui.lastId != null && ui.lastTitle != null) {
                OutlinedButton(
                    onClick = { onOpened(ui.lastId!!) },
                    enabled = !ui.busy,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text("Continue ${ui.lastTitle}")
                }
            }
            if (ui.busy) {
                CircularProgressIndicator(Modifier.padding(top = 16.dp))
            }
            ui.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
