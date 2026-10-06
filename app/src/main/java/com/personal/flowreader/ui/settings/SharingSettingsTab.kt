package com.personal.flowreader.ui.settings

import com.personal.flowreader.ui.design.controls.FlowLabel
import com.personal.flowreader.ui.design.tabs.FlowTabBar
import com.personal.flowreader.ui.design.tabs.FlowTabLevel
import com.personal.flowreader.ui.design.tabs.flowTextTabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.personal.flowreader.share.ParseRule
import com.personal.flowreader.share.ParseRules
import com.personal.flowreader.share.RouterContentKind
import com.personal.flowreader.share.RouterLanding
import com.personal.flowreader.share.RouterRule
import com.personal.flowreader.share.ShareAskMode
import com.personal.flowreader.share.SharePrefs
import com.personal.flowreader.ui.theme.FlowTokens
import java.util.UUID

data class SharePluginOption(
    val id: String,
    val title: String,
)

sealed class ImportRuleEditRequest {
    data class Router(
        val initial: RouterRule,
        val isNew: Boolean,
        val plugins: List<SharePluginOption>,
        val customTabs: List<SharePluginOption> = emptyList(),
        val onSave: (RouterRule) -> Unit,
    ) : ImportRuleEditRequest()

    data class Parse(
        val initial: ParseRule,
        val isNew: Boolean,
        val onSave: (ParseRule) -> Unit,
    ) : ImportRuleEditRequest()
}

class DomainRuleEditorState {
    var request by mutableStateOf<ImportRuleEditRequest?>(null)
}

@Composable
fun DomainRuleEditorHost(state: DomainRuleEditorState) {
    when (val req = state.request) {
        is ImportRuleEditRequest.Router -> RouterRuleEditorOverlay(
            initial = req.initial,
            isNew = req.isNew,
            plugins = req.plugins,
            customTabs = req.customTabs,
            onDismiss = { state.request = null },
            onSave = { saved ->
                req.onSave(saved)
                state.request = null
            },
        )
        is ImportRuleEditRequest.Parse -> ParseRuleEditorOverlay(
            initial = req.initial,
            isNew = req.isNew,
            onDismiss = { state.request = null },
            onSave = { saved ->
                req.onSave(saved)
                state.request = null
            },
        )
        null -> Unit
    }
}

@Composable
fun SharingSettingsTab(
    prefs: SharePrefs,
    routerRules: List<RouterRule>,
    parseRules: List<ParseRule>,
    overlayAllowed: Boolean,
    plugins: List<SharePluginOption>,
    customTabs: List<SharePluginOption> = emptyList(),
    editorState: DomainRuleEditorState,
    onManualOverride: (Boolean) -> Unit,
    onSaveRouterRules: (List<RouterRule>) -> Unit,
    onSaveParseRules: (List<ParseRule>) -> Unit,
) {
    var subTab by remember { mutableIntStateOf(0) }
    val customTitles = remember(customTabs) {
        customTabs.associate { it.id to it.title }
    }

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FlowTokens.Space.S),
    ) {
        FlowTabBar(tabs = flowTextTabs(listOf("Router", "Parser", "Plugins"), subTab) { subTab = it }, level = FlowTabLevel.Secondary, inset = FlowTokens.Space.None)
        Spacer(Modifier.height(FlowTokens.Space.S))

        when (subTab) {
            0 -> RouterPane(
                prefs = prefs,
                rules = routerRules,
                overlayAllowed = overlayAllowed,
                plugins = plugins,
                customTitles = customTitles,
                onManualOverride = onManualOverride,
                onSaveRules = onSaveRouterRules,
                onAdd = {
                    editorState.request = ImportRuleEditRequest.Router(
                        initial = RouterRule(
                            kind = RouterContentKind.Url,
                            hostPattern = "*",
                            parseUrl = true,
                            destination = RouterLanding.Queue,
                            order = routerRules.size,
                        ),
                        isNew = true,
                        plugins = plugins,
                        customTabs = customTabs,
                        onSave = { saved ->
                            onSaveRouterRules(
                                routerRules + saved.copy(
                                    id = UUID.randomUUID().toString(),
                                    order = routerRules.size,
                                ),
                            )
                        },
                    )
                },
                onEdit = { rule ->
                    editorState.request = ImportRuleEditRequest.Router(
                        initial = rule,
                        isNew = false,
                        plugins = plugins,
                        customTabs = customTabs,
                        onSave = { saved ->
                            onSaveRouterRules(routerRules.map { if (it.id == saved.id) saved else it })
                        },
                    )
                },
            )
            1 -> ParserPane(
                rules = parseRules,
                onSaveRules = onSaveParseRules,
                onAdd = {
                    editorState.request = ImportRuleEditRequest.Parse(
                        initial = ParseRule(hostPattern = "", order = parseRules.size),
                        isNew = true,
                        onSave = { saved ->
                            onSaveParseRules(
                                parseRules + saved.copy(
                                    id = UUID.randomUUID().toString(),
                                    order = parseRules.size,
                                ),
                            )
                        },
                    )
                },
                onEdit = { rule ->
                    editorState.request = ImportRuleEditRequest.Parse(
                        initial = rule,
                        isNew = false,
                        onSave = { saved ->
                            onSaveParseRules(parseRules.map { if (it.id == saved.id) saved else it })
                        },
                    )
                },
            )
            else -> PluginsSettingsTab()
        }
    }
}

@Composable
private fun RouterPane(
    prefs: SharePrefs,
    rules: List<RouterRule>,
    overlayAllowed: Boolean,
    plugins: List<SharePluginOption>,
    customTitles: Map<String, String>,
    onManualOverride: (Boolean) -> Unit,
    onSaveRules: (List<RouterRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (RouterRule) -> Unit,
) {
    val manualOverride = prefs.askMode == ShareAskMode.Ask

    FlowLabel("Share")
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Manual Override", style = MaterialTheme.typography.bodyLarge)
            Text(
                "When a URL matches a Plugin rule, ask Plugin vs the next URL rule. Needs display-over permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (manualOverride && !overlayAllowed) {
                Text(
                    "Overlay permission still needed — grant it when prompted, or toggle again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = FlowTokens.Space.XS),
                )
            }
        }
        Switch(checked = manualOverride, onCheckedChange = onManualOverride)
    }

    Spacer(Modifier.height(FlowTokens.Space.M))
    RuleListHeader(
        title = "Rules",
        emptyHint = "No rules. Seed covers book files → Files, raw text → Queue, URLs → Parse → Queue, and plugin sites → Plugin.",
        rulesEmpty = rules.isEmpty(),
        onAdd = onAdd,
        content = {
            ReorderableRouterList(
                rules = rules,
                plugins = plugins,
                customTitles = customTitles,
                onSave = onSaveRules,
                onEdit = onEdit,
            )
        },
    )
}

@Composable
private fun ParserPane(
    rules: List<ParseRule>,
    onSaveRules: (List<ParseRule>) -> Unit,
    onAdd: () -> Unit,
    onEdit: (ParseRule) -> Unit,
) {
    RuleListHeader(
        title = "Parse rules",
        emptyHint = "No parse rules. URL rules with Parse on use Default heuristics when nothing matches.",
        rulesEmpty = rules.isEmpty(),
        onAdd = onAdd,
        content = {
            ReorderableParseList(
                rules = rules,
                onSave = onSaveRules,
                onEdit = onEdit,
            )
        },
    )
}
