package com.personal.flowreader.plugin.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.flowreader.FlowApp
import com.personal.flowreader.plugin.repo.PluginVersionUpdates

/** Periodic repository refresh; notifies once per newer version of an installed plugin. */
class PluginVersionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FlowApp
        val repos = app.pluginRepos.refresh()
        if (repos.none { it.index != null }) return Result.retry()
        val installed = app.pluginManager.installed.value.associate { it.id to it.manifest.version }
        val updates = PluginVersionUpdates.available(
            installed,
            PluginVersionUpdates.latestById(repos),
            app.pluginInstaller::isCompatible,
        )
        val notified = app.settings.notifiedPluginVersionsOnce()
        val pending = PluginVersionUpdates.stillPending(notified, installed)
        val fresh = updates.filter { it.key !in notified }
        val posted = fresh.isNotEmpty() &&
            app.settings.pluginVersionCheckPrefsOnce().notify &&
            UpdateNotifier.postPluginUpdates(app, updates)
        val next = if (posted) pending + fresh.map { it.key } else pending
        if (next != notified) app.settings.setNotifiedPluginVersions(next)
        return Result.success()
    }
}
