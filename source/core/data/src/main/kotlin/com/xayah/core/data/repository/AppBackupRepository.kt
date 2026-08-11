package com.xayah.core.data.repository

import android.content.Context
import com.xayah.core.database.dao.AppBackupDao
import com.xayah.core.model.AppBackupOverview
import com.xayah.core.model.BACKUP_MANIFEST_SCHEMA_VERSION
import com.xayah.core.model.BackupAppEntity
import com.xayah.core.model.BackupEngine
import com.xayah.core.model.BackupManifest
import com.xayah.core.model.BackupManifestFile
import com.xayah.core.model.BackupRevisionEntity
import com.xayah.core.model.BackupVerificationStatus
import com.xayah.core.model.DataType
import com.xayah.core.model.OpType
import com.xayah.core.model.database.PackageDataStates
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.rootservice.service.RemoteRootService
import com.xayah.core.util.PathUtil
import com.xayah.core.util.localBackupSaveDir
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppBackupDao,
    private val packageRepository: PackageRepository,
    private val rootService: RemoteRootService,
    private val pathUtil: PathUtil,
) {
    data class RestoreSelection(val cloudName: String, val backupDir: String)
    data class VerificationReport(
        val results: List<VerificationResult>,
    )
    data class VerificationResult(
        val revision: BackupRevisionEntity,
        val appLabel: String,
        val status: BackupVerificationStatus,
        val issues: List<VerificationIssue>,
    )
    data class VerificationIssue(
        val type: VerificationIssueType,
        val fileName: String? = null,
    )
    enum class VerificationIssueType {
        MANIFEST_MISSING,
        MANIFEST_INVALID,
        METADATA_MISMATCH,
        FILE_MISSING,
        FILE_SIZE_MISMATCH,
        FILE_CHECKSUM_MISMATCH,
        NOT_LOCAL,
    }
    data class CleanupReport(
        val deletedRevisionIds: Set<String>,
        val failedCount: Int,
    )
    data class RebuildReport(
        val scannedCount: Int,
        val results: List<RebuildResult>,
    )
    data class RebuildResult(
        val appLabel: String,
        val revision: BackupRevisionEntity,
    )

    fun observeApps(): Flow<List<AppBackupOverview>> = dao.observeApps()

    fun observeApp(packageName: String, userId: Int): Flow<BackupAppEntity?> =
        dao.observeApp(packageName, userId)

    fun observeRevisions(packageName: String, userId: Int): Flow<List<BackupRevisionEntity>> =
        dao.observeRevisions(packageName, userId)

    suspend fun syncInstalledApps(userId: Int, apps: List<PackageEntity>) {
        removeUnusedIcons(dao.replaceInstalledApps(userId, apps.map { it.toBackupApp() }))
    }

    suspend fun recordLegacyRevision(
        app: PackageEntity,
        createdAt: Long,
        repositoryId: String,
        contentMask: Int,
        sizeBytes: Long,
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
                contentMask = contentMask,
                sizeBytes = sizeBytes,
            )
        )
    }

    suspend fun getLatestVerifiedLegacyRevision(app: PackageEntity, repositoryId: String): PackageEntity? {
        val revision = dao.getLatestRevision(app.packageName, app.userId, repositoryId) ?: return null
        if (verifyRevision(revision) != BackupVerificationStatus.VALID) return null
        return findLegacyRevision(revision)
    }

    suspend fun writeManifest(app: PackageEntity, createdAt: Long, revisionDir: String): BackupManifest? {
        val manifestPath = PathUtil.getBackupManifestDst(revisionDir)
        val files = rootService.listFilePaths(revisionDir, listFiles = true, listDirs = false)
            .filterNot { it == manifestPath }
            .sorted()
            .map { path ->
                val digest = rootService.calculateSHA256(path) ?: return null
                BackupManifestFile(
                    name = PathUtil.getFileName(path),
                    sizeBytes = rootService.calculateSize(path),
                    sha256 = digest,
                )
            }
        if (files.isEmpty()) return null
        val contentMask = files.fold(0) { mask, file ->
            mask or when (file.name.substringBefore('.')) {
                DataType.PACKAGE_APK.type -> 1
                DataType.PACKAGE_USER.type -> 2
                DataType.PACKAGE_USER_DE.type -> 4
                DataType.PACKAGE_DATA.type -> 8
                DataType.PACKAGE_OBB.type -> 16
                DataType.PACKAGE_MEDIA.type -> 32
                else -> 0
            }
        }
        val manifest = BackupManifest(
            packageName = app.packageName,
            userId = app.userId,
            createdAt = createdAt,
            versionName = app.packageInfo.versionName,
            versionCode = app.packageInfo.versionCode,
            contentMask = contentMask,
            files = files,
        )
        return manifest.takeIf { rootService.writeJson(data = it, dst = manifestPath).isSuccess }
    }

    suspend fun verifyRevision(revision: BackupRevisionEntity): BackupVerificationStatus =
        inspectRevision(revision).status

    suspend fun inspectRevision(revision: BackupRevisionEntity): VerificationResult {
        val appLabel = dao.getApp(revision.packageName, revision.userId)?.label ?: revision.packageName
        val revisionDir = getLocalRevisionDir(revision) ?: return VerificationResult(
            revision = revision,
            appLabel = appLabel,
            status = BackupVerificationStatus.NOT_VERIFIED,
            issues = listOf(VerificationIssue(VerificationIssueType.NOT_LOCAL)),
        )
        val manifestPath = PathUtil.getBackupManifestDst(revisionDir)
        if (!rootService.exists(manifestPath)) {
            return VerificationResult(
                revision,
                appLabel,
                BackupVerificationStatus.DAMAGED,
                listOf(VerificationIssue(VerificationIssueType.MANIFEST_MISSING)),
            )
        }
        val manifest = rootService.readJson<BackupManifest>(manifestPath) ?: return VerificationResult(
            revision,
            appLabel,
            BackupVerificationStatus.DAMAGED,
            listOf(VerificationIssue(VerificationIssueType.MANIFEST_INVALID)),
        )
        val issues = mutableListOf<VerificationIssue>()
        if (
            manifest.schemaVersion != BACKUP_MANIFEST_SCHEMA_VERSION ||
            manifest.packageName != revision.packageName ||
            manifest.userId != revision.userId ||
            manifest.createdAt != revision.createdAt ||
            manifest.versionName != revision.appVersionName ||
            manifest.versionCode != revision.appVersionCode ||
            manifest.contentMask != revision.contentMask ||
            manifest.files.isNullOrEmpty()
        ) issues += VerificationIssue(VerificationIssueType.METADATA_MISMATCH)
        manifest.files.orEmpty().forEach { file ->
            val path = "$revisionDir/${file.name}"
            when {
                !rootService.exists(path) -> issues += VerificationIssue(VerificationIssueType.FILE_MISSING, file.name)
                rootService.calculateSize(path) != file.sizeBytes ->
                    issues += VerificationIssue(VerificationIssueType.FILE_SIZE_MISMATCH, file.name)
                rootService.calculateSHA256(path) != file.sha256 ->
                    issues += VerificationIssue(VerificationIssueType.FILE_CHECKSUM_MISMATCH, file.name)
            }
        }
        return VerificationResult(
            revision = revision,
            appLabel = appLabel,
            status = if (issues.isEmpty()) BackupVerificationStatus.VALID else BackupVerificationStatus.DAMAGED,
            issues = issues,
        )
    }

    suspend fun verifyAllLocal(onProgress: suspend (Int, Int) -> Unit): VerificationReport {
        val revisions = dao.getRevisions(":${context.localBackupSaveDir()}")
        val results = mutableListOf<VerificationResult>()
        onProgress(0, revisions.size)
        revisions.forEachIndexed { index, revision ->
            results += inspectRevision(revision)
            onProgress(index + 1, revisions.size)
        }
        return VerificationReport(results)
    }

    suspend fun deleteFailedLocalBackups(
        results: List<VerificationResult>,
        onProgress: suspend (Int, Int) -> Unit,
    ): CleanupReport {
        val failed = results.filter { it.status != BackupVerificationStatus.VALID }
        val deletedIds = mutableSetOf<String>()
        onProgress(0, failed.size)
        failed.forEachIndexed { index, result ->
            if (deleteRevision(result.revision)) deletedIds += result.revision.id
            onProgress(index + 1, failed.size)
        }
        return CleanupReport(
            deletedRevisionIds = deletedIds,
            failedCount = failed.size - deletedIds.size,
        )
    }

    suspend fun rebuildLocalIndex(onProgress: suspend (Int, Int, Int) -> Unit): RebuildReport {
        val backupDir = context.localBackupSaveDir()
        val repositoryId = ":$backupDir"
        val appsDir = pathUtil.getLocalBackupAppsDir()
        val rebuilt = mutableListOf<Pair<PackageEntity, BackupManifest>>()
        val revisionDirs = rootService.listFilePaths(appsDir, listFiles = false, listDirs = true)
            .flatMap { rootService.listFilePaths(it, listFiles = false, listDirs = true) }
        onProgress(0, revisionDirs.size, 0)
        revisionDirs.forEachIndexed { index, revisionDir ->
            val app = rootService.readJson<PackageEntity>(PathUtil.getPackageRestoreConfigDst(revisionDir))
            val manifest = rootService.readJson<BackupManifest>(PathUtil.getBackupManifestDst(revisionDir))
            if (
                app != null &&
                manifest != null &&
                manifest.schemaVersion == BACKUP_MANIFEST_SCHEMA_VERSION &&
                app.packageName == manifest.packageName &&
                app.userId == manifest.userId &&
                app.preserveId == manifest.createdAt &&
                app.packageInfo.versionName == manifest.versionName &&
                app.packageInfo.versionCode == manifest.versionCode &&
                manifest.files.isNullOrEmpty().not()
            ) {
                rebuilt += app.copy(
                    id = 0,
                    indexInfo = app.indexInfo.copy(
                        opType = OpType.RESTORE,
                        cloud = "",
                        backupDir = backupDir,
                    ),
                    extraInfo = app.extraInfo.copy(activated = false),
                ) to manifest
            }
            onProgress(index + 1, revisionDirs.size, rebuilt.size)
        }
        packageRepository.replaceLocalRestoreIndex(backupDir, rebuilt.map { it.first })
        val apps = rebuilt
            .distinctBy { (app, _) -> app.packageName to app.userId }
            .map { (app, _) ->
                dao.getApp(app.packageName, app.userId)?.takeIf(BackupAppEntity::isInstalled)
                    ?: app.toBackupApp(isInstalled = false)
            }
        val revisions = rebuilt.map { (app, manifest) ->
            app.toRevision(
                repositoryId = repositoryId,
                contentMask = manifest.contentMask,
                sizeBytes = manifest.files.orEmpty().sumOf(BackupManifestFile::sizeBytes),
            )
        }
        dao.replaceRepositoryIndex(repositoryId, apps, revisions)
        val removedPackages = dao.getUninstalledAppsWithoutRevisions()
        dao.deleteUninstalledAppsWithoutRevisions()
        removeUnusedIcons(removedPackages)
        return RebuildReport(
            scannedCount = revisionDirs.size,
            results = rebuilt.zip(revisions) { (app, _), revision ->
                RebuildResult(app.packageInfo.label, revision)
            },
        )
    }

    suspend fun deleteRevision(revision: BackupRevisionEntity): Boolean {
        val packageEntity = findLegacyRevision(revision)
        val deleted = if (packageEntity != null) {
            packageRepository.delete(packageEntity)
            packageRepository.getPackage(packageEntity.id) == null
        } else {
            val revisionDir = getLocalRevisionDir(revision) ?: return false
            rootService.exists(revisionDir).not() || rootService.deleteRecursively(revisionDir)
        }
        if (!deleted) return false
        dao.deleteRevision(revision.id)
        getLocalRevisionDir(revision)?.let { revisionDir ->
            val packageDir = PathUtil.getParentPath(revisionDir)
            if (rootService.exists(packageDir) && rootService.listFilePaths(packageDir).isEmpty()) {
                rootService.deleteRecursively(packageDir)
            }
        }
        val removedPackages = dao.getUninstalledAppsWithoutRevisions(revision.userId)
        dao.deleteUninstalledAppsWithoutRevisions(revision.userId)
        removeUnusedIcons(removedPackages)
        return true
    }

    private suspend fun removeUnusedIcons(packageNames: Collection<String>) {
        packageNames.distinct().forEach { packageName ->
            if (dao.containsPackage(packageName)) return@forEach
            listOf(
                pathUtil.getPackageIconPath(packageName, adaptive = false),
                pathUtil.getPackageIconPath(packageName, adaptive = true),
            ).forEach { path ->
                if (rootService.exists(path)) rootService.deleteRecursively(path)
            }
        }
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

    private fun getLocalRevisionDir(revision: BackupRevisionEntity): String? {
        if (revision.engine != BackupEngine.LEGACY) return null
        val cloud = revision.repositoryId.substringBefore(':')
        if (cloud.isNotEmpty()) return null
        val backupDir = revision.repositoryId.substringAfter(':')
        return "$backupDir/${PathUtil.getAppsRelativeDir()}/${revision.artifactId}"
    }

    private fun PackageEntity.toBackupApp(isInstalled: Boolean = true) = BackupAppEntity(
        packageName = packageName,
        userId = userId,
        label = packageInfo.label,
        versionName = packageInfo.versionName,
        versionCode = packageInfo.versionCode,
        firstInstallTime = packageInfo.firstInstallTime,
        lastUpdateTime = packageInfo.lastUpdateTime,
        isSystem = isSystemApp,
        isInstalled = isInstalled,
    )

    private fun PackageEntity.toRevision(
        repositoryId: String,
        contentMask: Int,
        sizeBytes: Long,
    ) = BackupRevisionEntity(
        packageName = packageName,
        userId = userId,
        createdAt = preserveId,
        appVersionName = packageInfo.versionName,
        appVersionCode = packageInfo.versionCode,
        engine = BackupEngine.LEGACY,
        repositoryId = repositoryId,
        artifactId = archivesRelativeDir,
        contentMask = contentMask,
        sizeBytes = sizeBytes,
    )
}
