package com.xayah.core.data.repository

import com.xayah.core.model.AppBackupOverview
import com.xayah.core.model.BackupAppEntity
import com.xayah.core.model.BackupEngine
import com.xayah.core.model.BackupRevisionEntity
import com.xayah.core.database.dao.AppBackupDao
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.model.database.PackageDataStates
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBackupRepository @Inject constructor(
    private val dao: AppBackupDao,
    private val packageRepository: PackageRepository,
    private val labelsRepo: LabelsRepo,
) {
    data class RestoreSelection(val cloudName: String, val backupDir: String)

    fun observeApps(): Flow<List<AppBackupOverview>> = dao.observeApps()

    fun observeApp(packageName: String, userId: Int): Flow<BackupAppEntity?> =
        dao.observeApp(packageName, userId)

    fun observeRevisions(packageName: String, userId: Int): Flow<List<BackupRevisionEntity>> =
        dao.observeRevisions(packageName, userId)

    suspend fun syncInstalledApps(userId: Int, apps: List<PackageEntity>) {
        dao.replaceInstalledApps(userId, apps.map { it.toBackupApp() })
    }

    suspend fun recordLegacyRevision(
        app: PackageEntity,
        createdAt: Long,
        repositoryId: String,
    ) {
        dao.upsertApps(listOf(app.toBackupApp()))
        dao.upsertRevision(
            BackupRevisionEntity(
                packageName = app.packageName,
                userId = app.userId,
                createdAt = createdAt,
                appVersionName = app.packageInfo.versionName,
                appVersionCode = app.packageInfo.versionCode,
                engine = BackupEngine.LEGACY,
                repositoryId = repositoryId,
                artifactId = app.archivesRelativeDir,
                contentMask = app.selectionFlag,
                sizeBytes = app.displayStatsBytes.toLong(),
            )
        )
    }

    suspend fun deleteRevision(revision: BackupRevisionEntity): Boolean {
        val packageEntity = findLegacyRevision(revision) ?: return false

        packageRepository.delete(packageEntity)
        if (packageRepository.getPackage(packageEntity.id) != null) return false
        dao.deleteRevision(revision.id)
        dao.deleteUninstalledAppsWithoutRevisions(revision.userId)
        labelsRepo.deleteOrphanedAppRefs()
        return true
    }

    suspend fun selectRevisionForRestore(revision: BackupRevisionEntity, dataStates: PackageDataStates): RestoreSelection? {
        val packageEntity = findLegacyRevision(revision) ?: return null
        packageRepository.selectOnlyForRestore(packageEntity.id, dataStates)
        return RestoreSelection(
            cloudName = packageEntity.indexInfo.cloud,
            backupDir = packageEntity.indexInfo.backupDir,
        )
    }

    private suspend fun findLegacyRevision(revision: BackupRevisionEntity): PackageEntity? {
        if (revision.engine != BackupEngine.LEGACY) return null
        val preserveId = revision.artifactId.substringAfterLast('@').toLongOrNull() ?: return null
        return packageRepository
            .getRevisions(revision.packageName, revision.userId, preserveId)
            .firstOrNull { "${it.indexInfo.cloud}:${it.indexInfo.backupDir}" == revision.repositoryId }
    }

    private fun PackageEntity.toBackupApp() = BackupAppEntity(
        packageName = packageName,
        userId = userId,
        label = packageInfo.label,
        versionName = packageInfo.versionName,
        versionCode = packageInfo.versionCode,
        firstInstallTime = packageInfo.firstInstallTime,
        lastUpdateTime = packageInfo.lastUpdateTime,
        isSystem = isSystemApp,
        isInstalled = true,
    )
}
