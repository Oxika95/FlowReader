package com.personal.flowreader.plugin.updates

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.personal.flowreader.FlowApp

/** Periodic (and "Check now") two-way sync and run of [UpdateChecker], then [UpdateNotifier]. */
class ChapterUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as FlowApp
        app.pluginSync.runAll()
        val updates = UpdateChecker(app).run()
        UpdateNotifier.post(app, updates)
        return Result.success()
    }
}
