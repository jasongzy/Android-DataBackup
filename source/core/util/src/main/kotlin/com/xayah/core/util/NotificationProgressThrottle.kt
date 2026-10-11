package com.xayah.core.util

import android.os.SystemClock

class NotificationProgressThrottle {
    private var lastUpdate: Long? = null

    @Synchronized
    fun shouldUpdate(current: Int, total: Int, now: Long = SystemClock.elapsedRealtime()): Boolean {
        if (lastUpdate != null && current < total && now - lastUpdate!! < 1000) return false
        lastUpdate = now
        return true
    }
}
