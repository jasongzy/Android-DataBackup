package com.xayah.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

private val KeyDashboardSortField = stringPreferencesKey("dashboard_sort_field")
private val KeyDashboardSortAscending = booleanPreferencesKey("dashboard_sort_ascending")

data class DashboardSortPreference(
    val field: DashboardSortField,
    val ascending: Boolean,
)

enum class DashboardSortField {
    NAME,
    INSTALLED,
    DATA_SIZE,
    UPDATED,
    BACKED_UP,
}

fun Context.readDashboardSortPreference() = dataStore.data.map { preferences ->
    DashboardSortPreference(
        field = preferences[KeyDashboardSortField]
            ?.let { runCatching { DashboardSortField.valueOf(it) }.getOrNull() }
            ?: DashboardSortField.UPDATED,
        ascending = preferences[KeyDashboardSortAscending] ?: false,
    )
}

suspend fun Context.saveDashboardSortPreference(field: DashboardSortField, ascending: Boolean) {
    dataStore.edit { preferences ->
        preferences[KeyDashboardSortField] = field.name
        preferences[KeyDashboardSortAscending] = ascending
    }
}
