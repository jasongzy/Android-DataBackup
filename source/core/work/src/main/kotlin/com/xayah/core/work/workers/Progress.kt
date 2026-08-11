package com.xayah.core.work.workers

import androidx.work.CoroutineWorker
import androidx.work.workDataOf
import com.xayah.core.data.repository.WORK_PROGRESS_CURRENT
import com.xayah.core.data.repository.WORK_PROGRESS_MAX

internal suspend fun CoroutineWorker.publishProgress(current: Int, max: Int) {
    if (max <= 0) return
    setProgress(
        workDataOf(
            WORK_PROGRESS_CURRENT to current.coerceIn(0, max),
            WORK_PROGRESS_MAX to max,
        )
    )
}
