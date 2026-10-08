package com.personal.flowreader.plugin.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.personal.flowreader.data.PluginUpdatePrefs
import java.util.concurrent.TimeUnit

/** Keeps the WorkManager schedule for [ChapterUpdateWorker] in line with the settings. */
object UpdateScheduler {
    private const val PERIODIC = "plugin_chapter_updates"
    private const val NOW = "plugin_chapter_updates_now"

    fun apply(context: Context, prefs: PluginUpdatePrefs) {
        val work = WorkManager.getInstance(context)
        if (prefs.intervalHours <= 0) {
            work.cancelUniqueWork(PERIODIC)
            return
        }
        val request = PeriodicWorkRequestBuilder<ChapterUpdateWorker>(prefs.intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints(prefs.wifiOnly))
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** One run as soon as the network allows (Settings "Check now"). */
    fun checkNow(context: Context, wifiOnly: Boolean) {
        val request = OneTimeWorkRequestBuilder<ChapterUpdateWorker>()
            .setConstraints(constraints(wifiOnly))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    private fun constraints(wifiOnly: Boolean): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()
}
