package com.xayah.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationProgressThrottleTest {
    @Test
    fun publishesFirstUpdateAndLimitsIntermediateUpdates() {
        val throttle = NotificationProgressThrottle()
        assertTrue(throttle.shouldUpdate(1, 100, 0))
        assertFalse(throttle.shouldUpdate(2, 100, 100))
        assertFalse(throttle.shouldUpdate(3, 100, 999))
        assertTrue(throttle.shouldUpdate(4, 100, 1000))
    }

    @Test
    fun publishesCompletionImmediately() {
        val throttle = NotificationProgressThrottle()
        assertTrue(throttle.shouldUpdate(1, 2, 0))
        assertTrue(throttle.shouldUpdate(2, 2, 1))
    }
}
