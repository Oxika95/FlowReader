package com.personal.flowreader.ui.plugin

import com.personal.flowreader.plugin.api.PluginErrorCode
import com.personal.flowreader.plugin.api.PluginException
import com.personal.flowreader.plugin.sync.SyncReport

/** Wording for a sync that could not reach the site; one source for Sync now and pull-to-refresh. */
internal fun signedOutSyncMessage(pluginName: String) = "Sign in to $pluginName to sync"

internal fun failedListsSyncMessage(listTitles: List<String>) =
    "Could not load ${listTitles.joinToString(" and ")}. " +
        "Open site from Account to refresh the session, then Sync now."

/** The user-visible problem in [report], or null when every list loaded and the account was signed in. */
internal fun SyncReport.problem(pluginName: String, listTitle: (String) -> String): PluginException? = when {
    signedOut -> PluginException(PluginErrorCode.AuthRequired, signedOutSyncMessage(pluginName))
    failedLists.isNotEmpty() ->
        PluginException(PluginErrorCode.Network, failedListsSyncMessage(failedLists.map(listTitle)))
    else -> null
}
