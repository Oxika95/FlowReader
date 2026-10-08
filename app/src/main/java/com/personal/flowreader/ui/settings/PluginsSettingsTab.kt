package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowToggleRow
import com.personal.flowreader.ui.design.controls.FlowSliderRow
import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.controls.FlowChipRow
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowSection
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.controls.FlowDropdownRow
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.card.FlowCardVariant
import com.personal.flowreader.ui.design.card.FlowActionRow
import com.personal.flowreader.ui.design.card.FlowTextAction
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import com.personal.flowreader.ui.design.card.FlowConfirmCard
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.repo.PluginRepo
import com.personal.flowreader.plugin.repo.RepoManager
import com.personal.flowreader.plugin.repo.RepoPlugin
import com.personal.flowreader.ui.plugin.PluginSettingsForm
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.launch

/** Settings > Import > Plugins: installed plugins (update / settings / uninstall), repositories, catalog. */
@Composable
internal fun PluginsSettingsTab() {
    val app = LocalContext.current.applicationContext as FlowApp
    val scope = rememberCoroutineScope()
    val installed by app.pluginManager.installed.collectAsState()
    val repos by app.pluginRepos.repos.collectAsState()
    var working by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var repoDraft by remember { mutableStateOf("") }
    var pendingRepo by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf<InstalledPlugin?>(null) }

    fun run(label: String, block: suspend () -> String?) {
        if (working != null) return
        scope.launch {
            working = label
            status = runCatching { block() }.getOrElse { it.message ?: "$label failed" }
            working = null
        }
    }

    LaunchedEffect(Unit) {
        if (repos.isEmpty()) run("Refreshing") { app.pluginRepos.refresh(); null }
    }

    val catalog: List<RepoPlugin> = repos.flatMap { it.index?.plugins.orEmpty() }
        .groupBy { it.id }
        .map { (_, entries) -> entries.maxWith { a, b -> com.personal.flowreader.plugin.api.compareVersions(a.version, b.version) } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S)) {
        if (working != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("$working…", style = MaterialTheme.typography.bodySmall)
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        SectionTitle("Installed")
        if (installed.isEmpty()) Hint("No plugins installed.")
        installed.forEach { plugin ->
            val update = catalog.firstOrNull { it.id == plugin.id && app.pluginInstaller.updateAvailable(plugin, it) }
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${plugin.name}  ${plugin.manifest.version}", style = MaterialTheme.typography.bodyLarge)
                        if (plugin.manifest.description.isNotBlank()) Hint(plugin.manifest.description)
                        Hint("Can connect to: ${plugin.manifest.allowedHosts.joinToString(", ").ifBlank { "nothing" }}")
                    }
                }
                Row {
                    if (update != null) {
                        TextButton(
                            enabled = working == null,
                            onClick = { run("Updating ${plugin.name}") { app.pluginInstaller.install(update); "Updated ${plugin.name} to ${update.version}" } },
                        ) { Text("Update to ${update.version}") }
                    }
                    if (plugin.manifest.settings.isNotEmpty()) {
                        TextButton(onClick = { expanded = if (expanded == plugin.id) null else plugin.id }) {
                            Text(if (expanded == plugin.id) "Hide settings" else "Settings")
                        }
                    }
                    TextButton(enabled = working == null, onClick = { confirmUninstall = plugin }) { Text("Uninstall") }
                }
                if (expanded == plugin.id) {
                    PluginSettingsForm(pluginId = plugin.id, modifier = Modifier.padding(bottom = FlowTokens.Space.S))
                }
            }
            HorizontalDivider()
        }

        PluginCacheDefaultsSection(app)

        SectionTitle("New chapters")
        PluginUpdatesSection(app)
        HorizontalDivider()

        SectionTitle("Available")
        val available = catalog.filter { entry -> installed.none { it.id == entry.id } }
        if (available.isEmpty()) Hint(if (repos.any { it.index != null }) "Everything in your repositories is installed." else "Refresh repositories to see plugins.")
        available.forEach { entry ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${entry.name}  ${entry.version}", style = MaterialTheme.typography.bodyLarge)
                    if (entry.description.isNotBlank()) Hint(entry.description)
                    val repoName = repos.firstOrNull { it.url == entry.repoUrl }?.name ?: entry.repoUrl
                    Hint("From $repoName")
                }
                val compatible = app.pluginInstaller.isCompatible(entry)
                TextButton(
                    enabled = working == null && compatible,
                    onClick = { run("Installing ${entry.name}") { app.pluginInstaller.install(entry); "Installed ${entry.name}. Enable its tab from the library + button." } },
                ) { Text(if (compatible) "Install" else "Needs update") }
            }
        }

        SectionTitle("Repositories")
        repos.forEach { repo -> RepoRow(repo, enabled = working == null) { run("Removing") { app.pluginRepos.remove(repo.url); "Removed ${repo.name}" } } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = repoDraft,
                onValueChange = { repoDraft = it },
                label = { Text("Repository URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (repoDraft.isNotBlank()) pendingRepo = repoDraft }),
                modifier = Modifier.weight(1f),
            )
            IconButton(enabled = working == null, onClick = { run("Refreshing") { app.pluginRepos.refresh(); "Repositories refreshed" } }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh repositories")
            }
        }
        TextButton(enabled = repoDraft.isNotBlank() && working == null, onClick = { pendingRepo = repoDraft }) { Text("Add repository") }
        Hint("Plugins run in a sandbox and can only reach the sites they declare, but only add repositories you trust.")
    }

    pendingRepo?.let { raw ->
        val official = runCatching { RepoManager.normalize(raw) }.getOrNull() == RepoManager.OFFICIAL_REPO
        if (official) {
            LaunchedEffect(raw) {
                pendingRepo = null
                run("Adding") { app.pluginRepos.add(raw); repoDraft = ""; "Added repository" }
            }
        } else {
            FlowConfirmCard(
                visible = true,
                title = "Add third-party repository?",
                message = "Plugins from this repository are not reviewed by Flow Reader. They can read the " +
                    "sites they declare and anything you sign in to through them.",
                confirmLabel = "Add",
                onConfirm = {
                    pendingRepo = null
                    run("Adding") { app.pluginRepos.add(raw); repoDraft = ""; "Added repository" }
                },
                onDismiss = { pendingRepo = null },
            )
        }
    }

    confirmUninstall?.let { plugin ->
        FlowConfirmCard(
            visible = true,
            title = "Uninstall ${plugin.name}?",
            message = "Your downloaded stories and sign-in are kept, so reinstalling restores them.",
            confirmLabel = "Uninstall",
            destructive = true,
            onConfirm = {
                confirmUninstall = null
                run("Uninstalling") { app.pluginManager.uninstall(plugin.id); "Uninstalled ${plugin.name}" }
            },
            onDismiss = { confirmUninstall = null },
        )
    }
}

/** Cache level and cleanup new plugin stories start with; each story can change its own. */
@Composable
private fun PluginCacheDefaultsSection(app: FlowApp) {
    val scope = rememberCoroutineScope()
    var levelDraft by remember { mutableStateOf<String?>(null) }
    var cleanup by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val defaults = app.settings.pluginCacheDefaultsOnce()
        levelDraft = defaults.cacheLevel.toString()
        cleanup = defaults.cleanup
    }
    val draft = levelDraft ?: return
    SectionTitle("New stories")
    FlowTextField(
        value = draft,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            levelDraft = digits
            digits.toIntOrNull()?.let { scope.launch { app.settings.setPluginCacheLevel(it) } }
        },
        label = "Cache level",
        supportingText = "Chapters downloaded ahead of your reading position.",
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    FlowToggleRow(
        title = "Clean up old chapters",
        subtitle = "Delete chapters more than the cache level behind your position.",
        checked = cleanup,
        onCheckedChange = { on ->
            cleanup = on
            scope.launch { app.settings.setPluginCacheCleanup(on) }
        },
    )
    HorizontalDivider()
}

@Composable
private fun RepoRow(repo: PluginRepo, enabled: Boolean, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                repo.name + if (repo.official) " (official)" else "",
                style = MaterialTheme.typography.bodyLarge,
            )
            Hint(repo.url)
            when {
                repo.error != null -> Text(repo.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                repo.index != null -> Hint("${repo.index.plugins.size} plugins")
            }
        }
        IconButton(enabled = enabled, onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove ${repo.name}")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = FlowTokens.Space.S),
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
