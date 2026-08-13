package com.xayah.core.work.workers

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.xayah.core.work.WorkManagerInitializer

internal class AppRefreshFollowUpWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        WorkManagerInitializer.enqueueAppRefreshFollowUp(applicationContext)
        return Result.success()
    }

    companion object {
        fun buildRequest() = OneTimeWorkRequestBuilder<AppRefreshFollowUpWorker>().build()
    }
}
