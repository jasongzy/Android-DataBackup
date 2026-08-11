package com.xayah.core.datastore

import android.content.Context
import com.xayah.core.model.ConfigurationSettings
import com.xayah.core.model.KillAppOption
import com.xayah.core.model.ThemeType
import kotlinx.coroutines.flow.first

suspend fun Context.readConfigurationSettings(): ConfigurationSettings {
    return ConfigurationSettings(
        monet = readMonet().first(),
        themeType = readThemeType().first().name,
        language = readLanguage().first(),
        autoScreenOff = readAutoScreenOff().first(),
        loadSystemApps = readLoadSystemApps().first(),
        checkKeystore = readCheckKeystore().first(),
        compressionLevel = readCompressionLevel().first(),
        killAppOption = readKillAppOption().first().name,
        fastSameVersionBackup = readFastSameVersionBackup().first(),
        backupItself = readBackupItself().first(),
        backupConfigs = readBackupConfigs().first(),
        compressionTest = readCompressionTest().first(),
        followSymlinks = readFollowSymlinks().first(),
        cleanRestoring = readCleanRestoring().first(),
        restorePermissions = readRestorePermissions().first(),
        restoreSsaid = readRestoreSsaid().first(),
    )
}

suspend fun Context.saveConfigurationSettings(settings: ConfigurationSettings) {
    saveMonet(settings.monet)
    saveThemeType(ThemeType.valueOf(settings.themeType))
    saveLanguage(settings.language)
    saveAutoScreenOff(settings.autoScreenOff)
    saveLoadSystemApps(settings.loadSystemApps)
    saveCheckKeystore(settings.checkKeystore)
    saveCompressionLevel(settings.compressionLevel)
    saveKillAppOption(KillAppOption.valueOf(settings.killAppOption))
    saveStoreBoolean(KeyFastSameVersionBackup, settings.fastSameVersionBackup)
    saveBackupItself(settings.backupItself)
    saveBackupConfigs(settings.backupConfigs)
    saveCompressionTest(settings.compressionTest)
    saveFollowSymlinks(settings.followSymlinks)
    saveCleanRestoring(settings.cleanRestoring)
    saveRestorePermissions(settings.restorePermissions)
    saveRestoreSsaid(settings.restoreSsaid)
}
