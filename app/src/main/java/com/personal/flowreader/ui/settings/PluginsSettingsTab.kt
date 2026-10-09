package com.personal.flowreader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.InstalledPlugin
import com.personal.flowreader.plugin.repo.PluginRepo
import com.personal.flowreader.plugin.repo.PluginVersionUpdates
import com.personal.flowreader.plugin.repo.RepoManager
import com.personal.flowreader.plugin.repo.RepoPlugin
import com.personal.flowreader.ui.design.card.FlowConfirmCard
import com.personal.flowreader.ui.design.card.FlowFullscreenCard
import com.personal.flowreader.ui.design.controls.FlowCollapsible
import com.personal.flowreader.ui.design.controls.FlowHint
import com.personal.flowreader.ui.design.controls.FlowIconButton
import com.personal.flowreader.ui.design.controls.FlowListRow
import com.personal.flowreader.ui.design.controls.FlowSectionRow
import com.personal.flowreader.ui.design.controls.FlowTextField
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs
import com.personal.flowreader.ui.plugin.PluginAccountSettings
import com.personal.flowreader.ui.plugin.PluginSettingsForm
import com.personal.flowreader.ui.theme.FlowTokens
import kotlinx.coroutines.launch

/** Settings > Plugins: Installed (plugins, catalog, repositories), then one settings tab per plugin. */
@Composable
internal fun PluginsSettingsTab() {
    val app = LocalContext.current.applicationContext as FlowApp
    val installed by app.pluginManager.installed.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = installed.firstOrNull { it.id == selectedId }
    val tabs = listOf("Installed") + installed.map { it.name }
    val index = if (selected == null) 0 else installed.indexOf(selected) + 1
    FlowTabBar(
        tabs = flowTextTabs(tabs, index) { i -> selectedId = installed.getOrNull(i - 1)?.id },
        level = FlowTabLevel.Secondary,
        inset = FlowTokens.Space.None,
    )
    Spacer(Modifier.height(FlowTokens.Space.M))
    if (selected == null) {
        InstalledPluginsPane(onOpenSettings = { selectedId = it })
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S)) {
            FlowListRow(
                title = selected.name,
                meta = selected.manifest.version,
                info = pluginInfo(selected),
            )
            HorizontalDivider()
            if (selected.manifest.auth != null) {
                PluginAccountSettings(selected)
                HorizontalDivider()
            }
            PluginSettingsForm(pluginId = selected.id)
        }
    }
}

private fun pluginInfo(plugin: InstalledPlugin): String = listOfNotNull(
    plugin.manifest.description.ifBlank { null },
    "Can connect to: ${plugin.manifest.allowedHosts.joinToString(", ").ifBlank { "nothing" }}",
).joinToString("\n")

@Composable
private fun InstalledPluginsPane(onOpenSettings: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as FlowApp
    val scope = rememberCoroutineScope()
    val installed by app.pluginManager.installed.collectAsState()
    val repos by app.pluginRepos.repos.collectAsState()
    var working by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var repoDraft by remember { mutableStateOf("") }
    var pendingRepo by remember { mutableStateOf<String?>(null) }
    var confirmUninstall by remember { mutableStateOf<InstalledPlugin?>(null) }
    var showAvailable by remember { mutableStateOf(false) }

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

    val catalog: List<RepoPlugin> = PluginVersionUpdates.latestById(repos)
    val available = catalog.filter { entry -> installed.none { it.id == entry.id } }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.XS)) {
        if (working != null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            FlowHint("$working…")
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        FlowSectionRow("Installed") {
            FlowIconButton(Icons.Filled.Add, "Add plugin", onClick = { showAvailable = true }, enabled = working == null)
        }
        if (installed.isEmpty()) FlowHint("No plugins installed. Tap + to add one.")
        installed.forEach { plugin ->
            val update = catalog.firstOrNull { it.id == plugin.id && app.pluginInstaller.updateAvailable(plugin, it) }
            FlowListRow(
                title = plugin.name,
                meta = plugin.manifest.version,
                subtitle = update?.let { "Update available: ${it.version}" }.orEmpty(),
                info = pluginInfo(plugin),
            ) {
                if (update != null) {
                    FlowIconButton(
                        Icons.Filled.SystemUpdateAlt,
                        "Update ${plugin.name} to ${update.version}",
                        enabled = working == null,
                        onClick = { run("Updating ${plugin.name}") { app.pluginInstaller.install(update); "Updated ${plugin.name} to ${update.version}" } },
                    )
                }
                if (plugin.manifest.settings.isNotEmpty() || plugin.manifest.auth != null) {
                    FlowIconButton(Icons.Filled.Settings, "${plugin.name} settings", onClick = { onOpenSettings(plugin.id) })
                }
                FlowIconButton(Icons.Filled.Delete, "Uninstall ${plugin.name}", enabled = working == null, onClick = { confirmUninstall = plugin })
            }
        }

        Spacer(Modifier.height(FlowTokens.Space.S))
        HorizontalDivider()
        FlowCollapsible("Advanced") {
            FlowSectionRow(
                "Repositories",
                info = "Plugins run in a sandbox and can only reach the sites they declare, but only add repositories you trust.",
            ) {
                FlowIconButton(
                    Icons.Filled.Refresh,
                    "Refresh repositories",
                    enabled = working == null,
                    onClick = { run("Refreshing") { app.pluginRepos.refresh(); "Repositories refreshed" } },
                )
            }
            repos.forEach { repo ->
                RepoRow(repo, enabled = working == null) { run("Removing") { app.pluginRepos.remove(repo.url); "Removed ${repo.name}" } }
            }
            FlowTextField(
                value = repoDraft,
                onValueChange = { repoDraft = it },
                label = "Repository URL",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (repoDraft.isNotBlank()) pendingRepo = repoDraft }),
                trailingIcon = {
                    FlowIconButton(
                        Icons.Filled.Add,
                        "Add repository",
                        enabled = repoDraft.isNotBlank() && working == null,
                        onClick = { pendingRepo = repoDraft },
                    )
                },
            )
        }
    }

    FlowFullscreenCard(
        visible = showAvailable,
        onDismiss = { showAvailable = false },
        title = "Add plugin",
    ) {
        if (available.isEmpty()) {
            FlowHint(if (repos.any { it.index != null }) "Everything in your repositories is installed." else "Refresh repositories to see plugins.")
        }
        available.forEach { entry ->
            val compatible = app.pluginInstaller.isCompatible(entry)
            val repoName = repos.firstOrNull { it.url == entry.repoUrl }?.name ?: entry.repoUrl
            FlowListRow(
                title = entry.name,
                meta = entry.version,
                subtitle = if (compatible) "From $repoName" else "Needs a newer app",
                info = entry.description,
            ) {
                FlowIconButton(
                    Icons.Filled.Download,
                    "Install ${entry.name}",
                    enabled = working == null && compatible,
                    onClick = {
                        showAvailable = false
                        run("Installing ${entry.name}") { app.pluginInstaller.install(entry); "Installed ${entry.name}. Enable its tab from the library + button." }
                    },
                )
            }
        }
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

@Composable
private fun RepoRow(repo: PluginRepo, enabled: Boolean, onRemove: () -> Unit) {
    FlowListRow(
        title = repo.name,
        meta = listOfNotNull(
            "official".takeIf { repo.official },
            repo.index?.plugins?.size?.let { if (it == 1) "1 plugin" else "$it plugins" },
        ).joinToString(" · "),
        subtitle = repo.error ?: repo.url,
        subtitleError = repo.error != null,
        info = if (repo.error != null) repo.url else "",
    ) {
        FlowIconButton(Icons.Filled.Delete, "Remove ${repo.name}", enabled = enabled, onClick = onRemove)
    }
}
