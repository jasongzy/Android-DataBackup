package com.xayah.core.model

data class AppKey(val packageName: String, val userId: Int)

data class App(
    val id: Long,
    val packageName: String,
    val userId: Int,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val preserveId: Long,
    val isSystemApp: Boolean,
    val isUpdatedSystemApp: Boolean,
    val isXposedModule: Boolean,
    val isFrozen: Boolean,
    val isInstalled: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val lastBackupTime: Long,
    val dataSizeBytes: Long,
    val selectionFlag: Int,
    val selected: Boolean,
) {
    val key: AppKey
        get() = AppKey(packageName, userId)
}
