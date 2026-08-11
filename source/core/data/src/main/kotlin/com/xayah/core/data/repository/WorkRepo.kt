package com.xayah.core.data.repository

import android.content.Context
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class WorkRepo @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isAppRefreshRunning() = combine(
        isWorkRunning(FULL_INIT_WORK_NAME),
        isWorkRunning(FULL_INIT_AND_UPDATE_APPS_WORK_NAME),
        isWorkRunning(FAST_INIT_AND_UPDATE_APPS_WORK_NAME),
    ) { fullInit, fullUpdate, fastUpdate ->
        fullInit || fullUpdate || fastUpdate
    }

    private fun isWorkRunning(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name).map {
        var allFinished = true
        it.forEach { work ->
            if (work.state.isFinished.not()) allFinished = false
        }
        allFinished.not()
    }

    fun getAppRefreshProgress() = combine(
        getWorkProgress(FULL_INIT_WORK_NAME),
        getWorkProgress(FULL_INIT_AND_UPDATE_APPS_WORK_NAME),
        getWorkProgress(FAST_INIT_AND_UPDATE_APPS_WORK_NAME),
    ) { fullInit, fullUpdate, fastUpdate ->
        fullInit ?: fullUpdate ?: fastUpdate
    }

    private fun getWorkProgress(name: String) = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(name).map { workInfos ->
        val work = workInfos.firstOrNull { it.state == androidx.work.WorkInfo.State.RUNNING } ?: return@map null
        val max = work.progress.getInt(WORK_PROGRESS_MAX, 0)
        if (max > 0) {
            work.progress.getInt(WORK_PROGRESS_CURRENT, 0).toFloat().div(max).coerceIn(0f, 1f)
        } else {
            null
        }
    }

    fun isLoadAppBackupsRunning() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(LOAD_APP_BACKUPS_WORK_NAME).map {
        var allFinished = true
        it.forEach { work ->
            if (work.state.isFinished.not()) allFinished = false
        }
        allFinished.not()
    }

    fun isFastInitAndUpdateFilesRunning() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(FAST_INIT_AND_UPDATE_FILES_WORK_NAME).map {
        var allFinished = true
        it.forEach { work ->
            if (work.state.isFinished.not()) allFinished = false
        }
        allFinished.not()
    }

    fun isLoadFileBackupsRunning() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(LOAD_FILE_BACKUPS_WORK_NAME).map {
        var allFinished = true
        it.forEach { work ->
            if (work.state.isFinished.not()) allFinished = false
        }
        allFinished.not()
    }
}

const val FULL_INIT_WORK_NAME = "DbFullInitWork"
const val FULL_INIT_AND_UPDATE_APPS_WORK_NAME = "DbFullInitAndUpdateAppsWork"
const val FAST_INIT_AND_UPDATE_APPS_WORK_NAME = "DbFastInitAndUpdateAppsWork"
const val FAST_INIT_AND_UPDATE_FILES_WORK_NAME = "DbFastInitAndUpdateFilesWork"
const val LOAD_APP_BACKUPS_WORK_NAME = "DbLoadAppBackupsWork"
const val LOAD_FILE_BACKUPS_WORK_NAME = "DbLoadFileBackupsWork"

const val INPUT_DATA_KEY_REGULAR = "InputDataKeyRegular"
const val INPUT_DATA_KEY_CLOUD_NAME = "InputDataKeyCloudName"
const val WORK_PROGRESS_CURRENT = "WorkProgressCurrent"
const val WORK_PROGRESS_MAX = "WorkProgressMax"
