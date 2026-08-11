package com.xayah.core.model

data class App(
    val id: Long,
    val packageName: String,
    val userId: Int,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val preserveId: Long,
    val isSystemApp: Boolean,
    val isInstalled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val lastBackupTime: Long,
    val dataSizeBytes: Long,
    val selectionFlag: Int,
    val selected: Boolean,
)
