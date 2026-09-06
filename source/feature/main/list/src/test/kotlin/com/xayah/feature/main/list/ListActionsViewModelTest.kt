package com.xayah.feature.main.list

import com.xayah.core.model.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ListActionsViewModelTest {
    @Test
    fun selectedRangeIncludesEndpointsAndItemsBetweenThem() {
        val keys = (1..5).map { AppKey("app.$it", 0) }

        assertEquals(keys.subList(1, 5), keysInSelectedRange(keys, setOf(keys[1], keys[4])))
        assertEquals(emptyList<AppKey>(), keysInSelectedRange(keys, setOf(keys[2])))
    }
}
