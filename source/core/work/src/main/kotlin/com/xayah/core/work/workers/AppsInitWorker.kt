package com.xayah.core.work.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkerParameters
import com.xayah.core.data.repository.AppsRepo
import com.xayah.core.data.repository.APP_REFRESH_WORK_TAG
import com.xayah.core.datastore.di.DbDispatchers.Default
import com.xayah.core.datastore.di.Dispatcher
import com.xayah.core.util.NotificationUtil
import com.xayah.core.work.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@HiltWorker
internal class AppsInitWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    @Dispatcher(Default) private val defaultDispatcher: CoroutineDispatcher,
    private val appsRepo: AppsRepo,
) : CoroutineWorker(appContext, workerParams) {
    private val mNotificationBuilder by lazy { NotificationUtil.getProgressNotificationBuilder(appContext) }
    private var mNotificationInfo: ForegroundInfo? = null

    override suspend fun getForegroundInfo(): ForegroundInfo {
        if (mNotificationInfo == null) {
            mNotificationInfo = NotificationUtil.createForegroundInfo(
                appContext,
                mNotificationBuilder,
                appContext.getString(R.string.initializing_app_list),
                ""
            )
        }

        return mNotificationInfo!!
    }

    override suspend fun doWork(): Result = withContext(defaultDispatcher) {
        appsRepo.fullInitialize { cur, max, content ->
            if (cur % 10 == 0 || cur == max - 1) {
                val progress = publishProgress(cur + 1, max, APP_INITIALIZE_STAGE, APP_REFRESH_STAGE_COUNT)
                mNotificationInfo = NotificationUtil.createForegroundInfo(
                    appContext,
                    mNotificationBuilder,
                    appContext.getString(R.string.initializing_app_list),
                    content,
                    progress.max,
                    progress.current,
                )
                setForeground(mNotificationInfo!!)
            }
        }
        publishProgress(1, 1, APP_INITIALIZE_STAGE, APP_REFRESH_STAGE_COUNT)
        Result.success()
    }

    companion object {
        fun buildRequest() = OneTimeWorkRequestBuilder<AppsInitWorker>()
            .addTag(APP_REFRESH_WORK_TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
    }
}
