package com.xayah.core.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class BackupEngine {
    LEGACY,
    RUSTIC,
}

@Entity(
    tableName = "backup_apps",
    primaryKeys = ["packageName", "userId"],
    indices = [Index("label")],
)
data class BackupAppEntity(
    val packageName: String,
    val userId: Int,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isSystem: Boolean,
    val isInstalled: Boolean,
)

@Entity(
    tableName = "backup_revisions",
    foreignKeys = [
        ForeignKey(
            entity = BackupAppEntity::class,
            parentColumns = ["packageName", "userId"],
            childColumns = ["packageName", "userId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["packageName", "userId"]),
        Index(value = ["createdAt"]),
        Index(value = ["engine", "repositoryId", "artifactId"], unique = true),
    ],
)
data class BackupRevisionEntity(
    val packageName: String,
    val userId: Int,
    val createdAt: Long,
    val appVersionName: String,
    val appVersionCode: Long,
    val engine: BackupEngine,
    val repositoryId: String,
    val artifactId: String,
    val contentMask: Int,
    val sizeBytes: Long = 0,
    @PrimaryKey
    @ColumnInfo(name = "revisionId")
    val id: String = UUID.randomUUID().toString(),
)

data class AppBackupOverview(
    @Embedded val app: BackupAppEntity,
    val revisionCount: Int,
    val latestRevisionAt: Long?,
)
