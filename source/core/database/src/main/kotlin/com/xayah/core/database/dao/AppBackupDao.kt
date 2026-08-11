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
    suspend fun replaceInstalledApps(userId: Int, apps: List<BackupAppEntity>) {
        markUserAppsUninstalled(userId)
        upsertApps(apps)
        deleteUninstalledAppsWithoutRevisions(userId)
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
        """
    )
    suspend fun deleteUninstalledAppsWithoutRevisions(userId: Int)

    @Upsert
    suspend fun upsertRevision(revision: BackupRevisionEntity)

    @Query("DELETE FROM backup_revisions WHERE revisionId = :revisionId")
    suspend fun deleteRevision(revisionId: String)

    @Query(
        """
        SELECT backup_apps.*, COUNT(backup_revisions.revisionId) AS revisionCount,
            MAX(backup_revisions.createdAt) AS latestRevisionAt
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

    @Query(
        """
        SELECT * FROM backup_revisions
        WHERE packageName = :packageName AND userId = :userId
        ORDER BY createdAt DESC
        """
    )
    fun observeRevisions(packageName: String, userId: Int): Flow<List<BackupRevisionEntity>>

}
