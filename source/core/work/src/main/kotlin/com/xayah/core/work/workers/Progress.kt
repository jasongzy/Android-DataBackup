package com.xayah.core.work.workers

import androidx.work.CoroutineWorker
import androidx.work.workDataOf
import com.xayah.core.data.repository.WORK_PROGRESS_CURRENT
import com.xayah.core.data.repository.WORK_PROGRESS_MAX

internal data class WorkerProgress(val current: Int, val max: Int)

internal suspend fun CoroutineWorker.publishProgress(
    current: Int,
    max: Int,
    stage: Int = 0,
    stageCount: Int = 1,
): WorkerProgress {
    val stageProgress = if (max > 0) current.toFloat().div(max).coerceIn(0f, 1f) else 1f
    val overallProgress = ((stage + stageProgress) / stageCount).coerceIn(0f, 1f)
    val progress = WorkerProgress(
        current = (overallProgress * WORK_PROGRESS_SCALE).toInt(),
        max = WORK_PROGRESS_SCALE,
    )
    setProgress(
        workDataOf(
            WORK_PROGRESS_CURRENT to progress.current,
            WORK_PROGRESS_MAX to progress.max,
        )
    )
    return progress
}

private const val WORK_PROGRESS_SCALE = 1_000
internal const val APP_REFRESH_STAGE_COUNT = 2
internal const val APP_INITIALIZE_STAGE = 0
internal const val APP_UPDATE_STAGE = 1
