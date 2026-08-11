package com.xayah.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.xayah.core.model.AppBackupOverview
import com.xayah.core.model.BackupAppEntity
import com.xayah.core.model.BackupRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppBackupDao {
    @Transaction
    suspend fun replaceInstalledApps(userId: Int, apps: List<BackupAppEntity>): List<String> {
        markUserAppsUninstalled(userId)
        upsertApps(apps)
        val removedPackages = getUninstalledAppsWithoutRevisions(userId)
        deleteUninstalledAppsWithoutRevisions(userId)
        return removedPackages
    }

    @Upsert
    suspend fun upsertApps(apps: List<BackupAppEntity>)

    @Query("UPDATE backup_apps SET isInstalled = 0 WHERE userId = :userId")
    suspend fun markUserAppsUninstalled(userId: Int)

    @Query(
        """
        DELETE FROM backup_apps
        WHERE userId = :userId AND isInstalled = 0
            AND NOT EXISTS (
                SELECT 1 FROM backup_revisions
                WHERE backup_revisions.packageName = backup_apps.packageName
                    AND backup_revisions.userId = backup_apps.userId
            )
            AND NOT EXISTS (
                SELECT 1 FROM LabelAppCrossRefEntity
                WHERE LabelAppCrossRefEntity.packageName = backup_apps.packageName
                    AND LabelAppCrossRefEntity.userId = backup_apps.userId
                    AND LabelAppCrossRefEntity.preserveId = 0
            )
        """
    )
    suspend fun deleteUninstalledAppsWithoutRevisions(userId: Int)

    @Query(
        """
        SELECT packageName FROM backup_apps
        WHERE userId = :userId AND isInstalled = 0
            AND NOT EXISTS (
                SELECT 1 FROM backup_revisions
                WHERE backup_revisions.packageName = backup_apps.packageName
                    AND backup_revisions.userId = backup_apps.userId
            )
            AND NOT EXISTS (
                SELECT 1 FROM LabelAppCrossRefEntity
                WHERE LabelAppCrossRefEntity.packageName = backup_apps.packageName
                    AND LabelAppCrossRefEntity.userId = backup_apps.userId
                    AND LabelAppCrossRefEntity.preserveId = 0
            )
        """
    )
    suspend fun getUninstalledAppsWithoutRevisions(userId: Int): List<String>

    @Upsert
    suspend fun upsertRevision(revision: BackupRevisionEntity)

    @Upsert
    suspend fun upsertRevisions(revisions: List<BackupRevisionEntity>)

    @Transaction
    suspend fun replaceRepositoryIndex(
        repositoryId: String,
        apps: List<BackupAppEntity>,
        revisions: List<BackupRevisionEntity>,
    ) {
        deleteRevisions(repositoryId)
        upsertApps(apps)
        upsertRevisions(revisions)
    }

    @Query("SELECT * FROM backup_revisions WHERE packageName = :packageName AND userId = :userId AND repositoryId = :repositoryId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestRevision(packageName: String, userId: Int, repositoryId: String): BackupRevisionEntity?

    @Query("SELECT * FROM backup_revisions WHERE repositoryId = :repositoryId ORDER BY createdAt DESC")
    suspend fun getRevisions(repositoryId: String): List<BackupRevisionEntity>

    @Query("DELETE FROM backup_revisions WHERE repositoryId = :repositoryId")
    suspend fun deleteRevisions(repositoryId: String)

    @Query("DELETE FROM backup_revisions WHERE revisionId = :revisionId")
    suspend fun deleteRevision(revisionId: String)

    @Query(
        """
        DELETE FROM backup_apps
        WHERE isInstalled = 0
            AND NOT EXISTS (
                SELECT 1 FROM backup_revisions
                WHERE backup_revisions.packageName = backup_apps.packageName
                    AND backup_revisions.userId = backup_apps.userId
            )
            AND NOT EXISTS (
                SELECT 1 FROM LabelAppCrossRefEntity
                WHERE LabelAppCrossRefEntity.packageName = backup_apps.packageName
                    AND LabelAppCrossRefEntity.userId = backup_apps.userId
                    AND LabelAppCrossRefEntity.preserveId = 0
            )
        """
    )
    suspend fun deleteUninstalledAppsWithoutRevisions()

    @Query(
        """
        SELECT packageName FROM backup_apps
        WHERE isInstalled = 0
            AND NOT EXISTS (
                SELECT 1 FROM backup_revisions
                WHERE backup_revisions.packageName = backup_apps.packageName
                    AND backup_revisions.userId = backup_apps.userId
            )
            AND NOT EXISTS (
                SELECT 1 FROM LabelAppCrossRefEntity
                WHERE LabelAppCrossRefEntity.packageName = backup_apps.packageName
                    AND LabelAppCrossRefEntity.userId = backup_apps.userId
                    AND LabelAppCrossRefEntity.preserveId = 0
            )
        """
    )
    suspend fun getUninstalledAppsWithoutRevisions(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM backup_apps WHERE packageName = :packageName)")
    suspend fun containsPackage(packageName: String): Boolean

    @Query(
        """
        SELECT backup_apps.*, COUNT(backup_revisions.revisionId) AS revisionCount,
            MAX(backup_revisions.createdAt) AS latestRevisionAt,
            MAX(CASE WHEN (backup_revisions.contentMask & 1) != 0 THEN 1 ELSE 0 END) AS hasApkBackup,
            MAX(CASE WHEN (backup_revisions.contentMask & 62) != 0 THEN 1 ELSE 0 END) AS hasDataBackup,
            MAX(CASE WHEN (backup_revisions.contentMask & 1) != 0 THEN backup_revisions.appVersionCode END) AS latestApkVersionCode
        FROM backup_apps
        LEFT JOIN backup_revisions
            ON backup_apps.packageName = backup_revisions.packageName
            AND backup_apps.userId = backup_revisions.userId
        GROUP BY backup_apps.packageName, backup_apps.userId
        ORDER BY backup_apps.isInstalled DESC, backup_apps.label COLLATE NOCASE
        """
    )
    fun observeApps(): Flow<List<AppBackupOverview>>

    @Query("SELECT * FROM backup_apps WHERE packageName = :packageName AND userId = :userId LIMIT 1")
    fun observeApp(packageName: String, userId: Int): Flow<BackupAppEntity?>

    @Query("SELECT * FROM backup_apps WHERE packageName = :packageName AND userId = :userId LIMIT 1")
    suspend fun getApp(packageName: String, userId: Int): BackupAppEntity?

    @Query(
        """
        SELECT * FROM backup_revisions
        WHERE packageName = :packageName AND userId = :userId
        ORDER BY createdAt DESC
        """
    )
    fun observeRevisions(packageName: String, userId: Int): Flow<List<BackupRevisionEntity>>

}
