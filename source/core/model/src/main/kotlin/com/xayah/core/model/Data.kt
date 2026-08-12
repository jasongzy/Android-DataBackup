package com.xayah.core.model

import com.google.gson.annotations.SerializedName
import com.xayah.core.model.database.CloudEntity
import com.xayah.core.model.database.LabelAppCrossRefEntity
import com.xayah.core.model.database.LabelEntity
import com.xayah.core.model.database.LabelFileCrossRefEntity

const val DefaultPreserveId = 0L

data class BlacklistAppItem(
    var packageName: String,
    var userId: Int,
)

data class BlacklistFileItem(
    var name: String,
    var path: String,
)

data class ConfigurationsBlacklist(
    var apps: List<BlacklistAppItem>,
    var files: List<BlacklistFileItem>,
)

data class AppNoteItem(
    val packageName: String,
    val userId: Int,
    val note: String,
)

data class Configurations(
    val blacklist: ConfigurationsBlacklist,
    var cloud: List<CloudEntity>,
    var labels: List<LabelEntity>,
    var labelColors: Map<String, Long>,
    var labelAppRefs: List<LabelAppCrossRefEntity>,
    var labelFileRefs: List<LabelFileCrossRefEntity>,
    var appNotes: List<AppNoteItem>? = null,
    var settings: ConfigurationSettings?,
)

data class ConfigurationSettings(
    val monet: Boolean,
    val themeType: String,
    val language: String,
    val autoScreenOff: Boolean,
    val loadSystemApps: Boolean,
    val checkKeystore: Boolean,
    val compressionLevel: Int,
    val killAppOption: String,
    val fastSameVersionBackup: Boolean,
    val backupItself: Boolean,
    val backupConfigs: Boolean,
    val compressionTest: Boolean,
    val followSymlinks: Boolean,
    val cleanRestoring: Boolean,
    val restorePermissions: Boolean,
    val restoreSsaid: Boolean,
)

data class ContributorItem(
    val name: String,
    var avatar: String,
    var desc: String,
    var link: String,
)

data class TranslatorItem(
    val email: String,
    @SerializedName("full_name") var fullName: String,
    @SerializedName("change_count") var changeCount: String,
)

/**
 * Action value(Int):
 * 0: Replace
 * 1: Remove
 */
data class TranslatorRevisionItem(
    val name: String,
    var avatar: String,
    var link: String,
    var actions: Map<String, Int>,
)

data class UserInfo(
    var id: Int,
    var name: String,
)
