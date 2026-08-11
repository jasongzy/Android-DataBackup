package com.xayah.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.map

private val KeyLabelColors = stringPreferencesKey("label_colors")
private val LabelColorsType = object : TypeToken<Map<String, Long>>() {}.type

fun Context.readLabelColors() = dataStore.data.map { preferences ->
    preferences[KeyLabelColors]?.let { Gson().fromJson<Map<String, Long>>(it, LabelColorsType) }.orEmpty()
}

suspend fun Context.saveLabelColor(label: String, colorArgb: Long) {
    dataStore.edit { preferences ->
        val colors = preferences[KeyLabelColors]
            ?.let { Gson().fromJson<Map<String, Long>>(it, LabelColorsType) }
            .orEmpty()
            .toMutableMap()
        colors[label] = colorArgb
        preferences[KeyLabelColors] = Gson().toJson(colors)
    }
}

suspend fun Context.saveLabelColors(labelColors: Map<String, Long>) {
    dataStore.edit { preferences ->
        val colors = preferences[KeyLabelColors]
            ?.let { Gson().fromJson<Map<String, Long>>(it, LabelColorsType) }
            .orEmpty()
            .toMutableMap()
        colors.putAll(labelColors)
        preferences[KeyLabelColors] = Gson().toJson(colors)
    }
}

suspend fun Context.deleteLabelColor(label: String) {
    dataStore.edit { preferences ->
        val colors = preferences[KeyLabelColors]
            ?.let { Gson().fromJson<Map<String, Long>>(it, LabelColorsType) }
            .orEmpty()
            .toMutableMap()
        colors.remove(label)
        preferences[KeyLabelColors] = Gson().toJson(colors)
    }
}
