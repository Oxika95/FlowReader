package com.personal.flowreader.library.plugin.royalroad

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.flowreader.library.plugin.LibraryPluginActions
import com.personal.flowreader.library.plugin.LibrarySourcePlugin
import com.personal.flowreader.library.plugin.SourceWork
import com.personal.flowreader.library.plugin.SourceWorkDetail

class RoyalRoadPlugin : LibrarySourcePlugin {
    override val id: String = ID
    override val title: String = "Royal Road"
    override val subtitle: String = "Web serials from royalroad.com"

    @Composable
    override fun TabContent(actions: LibraryPluginActions, modifier: Modifier) {
        val vm: RoyalRoadViewModel = viewModel()
        RoyalRoadTab(vm = vm, actions = actions, modifier = modifier)
    }

    companion object {
        const val ID = "royalroad"
    }
}

@Composable
internal fun RoyalRoadTab(
    vm: RoyalRoadViewModel,
    actions: LibraryPluginActions,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) {
        if (ui.works.isEmpty() && ui.fiction == null) vm.loadBrowse()
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (ui.busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (ui.fiction != null) {
            FictionPane(
                fiction = ui.fiction!!,
                busy = ui.busy,
                onBack = vm::closeFiction,
                onOpen = { index, queue -> vm.startReading(actions, index, queue) },
            )
        } else {
            BrowsePane(
                ui = ui,
                onQuery = vm::setQuery,
                onSearch = vm::search,
                onUrl = vm::setUrlDraft,
                onOpenUrl = vm::openUrl,
                onBrowse = vm::loadBrowse,
                onFollows = vm::loadFollows,
                onWork = vm::openWork,
                onShowLogin = { vm.setShowLogin(true) },
                onLogout = vm::logout,
            )
        }
        ui.error?.let { err ->
            Text(
                err,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (ui.showLogin) {
        AlertDialog(
            onDismissRequest = { if (!ui.busy) vm.setShowLogin(false) },
            title = { Text("Royal Road sign in") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ui.emailDraft,
                        onValueChange = vm::setEmailDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value = ui.passwordDraft,
                        onValueChange = vm::setPasswordDraft,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { vm.login() }),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = vm::login, enabled = !ui.busy) { Text("Sign in") }
            },
            dismissButton = {
                TextButton(onClick = { vm.setShowLogin(false) }, enabled = !ui.busy) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun BrowsePane(
    ui: RoyalRoadUi,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onUrl: (String) -> Unit,
    onOpenUrl: () -> Unit,
    onBrowse: (String) -> Unit,
    onFollows: () -> Unit,
    onWork: (SourceWork) -> Unit,
    onShowLogin: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                label = { Text("Search") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                trailingIcon = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                },
            )
            IconButton(onClick = if (ui.loggedIn) onLogout else onShowLogin) {
                Icon(
                    imageVector = when {
                        ui.loggedIn -> Icons.AutoMirrored.Filled.Logout
                        else -> Icons.AutoMirrored.Filled.Login
                    },
                    contentDescription = if (ui.loggedIn) "Sign out" else "Sign in",
                )
            }
        }
        if (ui.loggedIn && ui.loginEmail.isNotBlank()) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(ui.loginEmail, style = MaterialTheme.typography.bodySmall)
            }
        }
        OutlinedTextField(
            value = ui.urlDraft,
            onValueChange = onUrl,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            label = { Text("Fiction or chapter URL") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onOpenUrl() }),
            trailingIcon = {
                TextButton(onClick = onOpenUrl) { Text("Go") }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ui.loggedIn) {
                FilterChip(
                    selected = ui.following,
                    onClick = onFollows,
                    label = { Text("Follows") },
                )
            }
            RoyalRoadHtml.browseOrders.forEach { (id, label) ->
                FilterChip(
                    selected = !ui.following && ui.browseOrder == id,
                    onClick = { onBrowse(id) },
                    label = { Text(label) },
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(ui.works, key = { it.url }) { work ->
                WorkRow(work = work, onClick = { onWork(work) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WorkRow(work: SourceWork, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(work.title, style = MaterialTheme.typography.titleMedium)
        val sub = listOf(work.author, work.latestChapter).filter { it.isNotBlank() }.joinToString(" · ")
        if (sub.isNotEmpty()) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FictionPane(
    fiction: SourceWorkDetail,
    busy: Boolean,
    onBack: () -> Unit,
    onOpen: (startIndex: Int, queue: Boolean) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(fiction.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                if (fiction.author.isNotBlank()) {
                    Text(
                        fiction.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (fiction.synopsis.isNotBlank()) {
            Text(
                fiction.synopsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { onOpen(0, true) }, enabled = !busy && fiction.chapters.isNotEmpty()) {
                Text("Queue from start")
            }
            TextButton(onClick = { onOpen(0, false) }, enabled = !busy && fiction.chapters.isNotEmpty()) {
                Text("Open from start")
            }
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            itemsIndexed(fiction.chapters, key = { _, ch -> ch.url }) { index, chapter ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy) { onOpen(index, false) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(chapter.title, style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onOpen(index, true) }, enabled = !busy) {
                            Text("Queue")
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
