package com.xayah.core.data.repository

import android.content.Context
import com.xayah.core.datastore.DashboardSortField
import com.xayah.core.datastore.readDashboardSortPreference
import com.xayah.core.datastore.saveDashboardSortPreference
import com.xayah.core.model.App
import com.xayah.core.model.File
import com.xayah.core.model.OpType
import com.xayah.core.model.SortType
import com.xayah.core.model.Target
import com.xayah.core.model.UserInfo
import com.xayah.core.model.database.LabelAppCrossRefEntity
import com.xayah.core.model.database.LabelFileCrossRefEntity
import com.xayah.core.util.module.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListDataRepo @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usersRepo: UsersRepo,
    private val appsRepo: AppsRepo,
    private val filesRepo: FilesRepo,
    private val labelsRepo: LabelsRepo,
    private val workRepo: WorkRepo,
) {
    private lateinit var target: Target
    private lateinit var listData: Flow<ListData>

    private lateinit var selected: Flow<Long>
    private lateinit var selectionMode: MutableStateFlow<Boolean>
    private lateinit var total: Flow<Long>
    private lateinit var searchQuery: MutableStateFlow<String>
    private lateinit var showFilterSheet: MutableStateFlow<Boolean>
    private lateinit var sortIndex: MutableStateFlow<Int>
    private lateinit var sortType: MutableStateFlow<SortType>
    private lateinit var isUpdating: Flow<Boolean>
    private lateinit var labelFilters: MutableStateFlow<Map<String, LabelFilterMode>>

    // Apps
    private lateinit var showDataItemsSheet: MutableStateFlow<Boolean>
    private lateinit var filters: MutableStateFlow<Filters>
    private lateinit var userIndex: MutableStateFlow<Int>
    private lateinit var userList: Flow<List<UserInfo>>
    private lateinit var userMap: Flow<Map<Int, Long>>
    private lateinit var appList: Flow<List<App>>
    private lateinit var selectedAppIds: MutableStateFlow<Set<Long>>
    private lateinit var pkgUserSet: Flow<Set<String>> // "${pkgName}-${userId}"
    private lateinit var labelAppRefs: Flow<List<LabelAppCrossRefEntity>> // Labels filtered app refs

    // Files
    private lateinit var fileList: Flow<List<File>>
    private lateinit var labelFileRefs: Flow<List<LabelFileCrossRefEntity>> // Labels filtered file refs

    fun initialize(target: Target, opType: OpType, cloudName: String, backupDir: String) {
        this.target = target
        when (target) {
            Target.Apps -> {
                selectedAppIds = MutableStateFlow(emptySet())
                selectionMode = MutableStateFlow(false)
                selected = selectedAppIds.map { it.size.toLong() }
                total = appsRepo.countApps(opType)
                searchQuery = MutableStateFlow("")
                showFilterSheet = MutableStateFlow(false)
                sortIndex = MutableStateFlow(0)
                sortType = MutableStateFlow(SortType.ASCENDING)
                isUpdating = when (opType) {
                    OpType.BACKUP -> workRepo.isAppRefreshRunning()
                    OpType.RESTORE -> combine(workRepo.isAppRefreshRunning(), workRepo.isLoadAppBackupsRunning()) { refresh, loadBackups -> refresh || loadBackups }
                }
                labelFilters = MutableStateFlow(emptyMap())
                labelAppRefs = labelFilters.map {
                    labelsRepo.getAppRefs(it.keys)
                }

                showDataItemsSheet = MutableStateFlow(false)
                filters = MutableStateFlow(
                    Filters(
                        cloud = cloudName,
                        backupDir = backupDir,
                        systemApps = runBlocking { appsRepo.getLoadSystemApps() },
                        nonSystemApps = true,
                        frozenApps = true,
                        unfrozenApps = true,
                        hasBackups = true,
                        hasNoBackups = true,
                        installedApps = true,
                        notInstalledApps = true,
                        hasApkBackup = false,
                        hasNoApkBackup = false,
                        hasDataBackup = false,
                        hasNoDataBackup = false,
                        hasOutdatedApkBackup = false,
                    )
                )
                userIndex = MutableStateFlow(0)
                userList = usersRepo.getUsers(opType)
                userMap = usersRepo.getUsersMap(opType, cloudName, backupDir)

                listData = getAppListData()
                pkgUserSet = when (opType) {
                    OpType.BACKUP -> {
                        appsRepo.getBackups(filters)
                    }

                    OpType.RESTORE -> {
                        appsRepo.getInstalledApps(userList)
                    }
                }
                appList = combine(
                    appsRepo.getApps(opType = opType, listData = listData, pkgUserSet = pkgUserSet, refs = labelAppRefs, labelFilters = labelFilters, cloudName = cloudName, backupDir = backupDir),
                    selectedAppIds,
                ) { apps, ids ->
                    apps.map { it.copy(selected = it.id in ids) }
                }
            }

            Target.Files -> {
                selectionMode = MutableStateFlow(false)
                selected = filesRepo.countSelectedFiles(opType)
                total = filesRepo.countFiles(opType)
                searchQuery = MutableStateFlow("")
                showFilterSheet = MutableStateFlow(false)
                sortIndex = MutableStateFlow(0)
                sortType = MutableStateFlow(SortType.ASCENDING)
                isUpdating = when (opType) {
                    OpType.BACKUP -> combine(workRepo.isAppRefreshRunning(), workRepo.isFastInitAndUpdateFilesRunning()) { refresh, fast -> refresh || fast }
                    OpType.RESTORE -> combine(workRepo.isAppRefreshRunning(), workRepo.isLoadFileBackupsRunning()) { refresh, loadBackups -> refresh || loadBackups }
                }
                labelFilters = MutableStateFlow(emptyMap())
                labelFileRefs = labelFilters.map {
                    labelsRepo.getFileRefs(it.keys)
                }

                listData = getFileListData()
                fileList = filesRepo.getFiles(opType = opType, listData = listData, refs = labelFileRefs, labelFilters = labelFilters, cloudName = cloudName, backupDir = backupDir)
            }
        }
    }

    private fun getAppListData(): Flow<ListData.Apps> = combine(
        combine(selected, selectionMode) { selected, selectionMode -> selected to selectionMode },
        total,
        searchQuery,
        showFilterSheet,
        sortIndex,
        sortType,
        isUpdating,
        labelFilters,
        showDataItemsSheet,
        filters,
        userIndex,
        userList,
        userMap,
    ) { selection, t, sQuery, sFSheet, sIndex, sType, iUpdating, lIds, sDISheet, filters, uIndex, uList, uMap ->
        ListData.Apps(selection.first, selection.second, t, sQuery, sFSheet, sIndex, sType, iUpdating, lIds, sDISheet, filters, uIndex, uList, uMap)
    }

    private fun getFileListData(): Flow<ListData.Files> = combine(
        selected,
        selectionMode,
        total,
        searchQuery,
        showFilterSheet,
        sortIndex,
        sortType,
        isUpdating,
        labelFilters,
    ) { s, mode, t, sQuery, sFSheet, sIndex, sType, iUpdating, lIds ->
        ListData.Files(s, mode, t, sQuery, sFSheet, sIndex, sType, iUpdating, lIds)
    }

    fun getListData(): Flow<ListData> = listData

    fun getAppList(): Flow<List<App>> = appList

    fun getSelectedAppIds(): StateFlow<Set<Long>> = selectedAppIds

    fun getFileList(): Flow<List<File>> = fileList

    suspend fun setFilters(block: (Filters) -> Filters) {
        filters.emit(block(filters.value))
    }

    suspend fun setAppSelected(id: Long, selected: Boolean) {
        selectedAppIds.emit(
            if (selected) selectedAppIds.value + id else selectedAppIds.value - id
        )
    }

    suspend fun enterAppSelection(id: Long) {
        selectionMode.emit(true)
        setAppSelected(id, true)
    }

    suspend fun selectApps(ids: Collection<Long>) {
        selectedAppIds.emit(selectedAppIds.value + ids)
    }

    suspend fun unselectApps(ids: Collection<Long>) {
        selectedAppIds.emit(selectedAppIds.value - ids.toSet())
    }

    suspend fun reverseAppSelection(ids: Collection<Long>) {
        val candidates = ids.toSet()
        selectedAppIds.emit((selectedAppIds.value - candidates) + (candidates - selectedAppIds.value))
    }

    suspend fun retainAppSelection(ids: Collection<Long>) {
        selectedAppIds.emit(selectedAppIds.value.intersect(ids.toSet()))
    }

    fun clearAppSelection() {
        selectedAppIds.value = emptySet()
        selectionMode.value = false
    }

    suspend fun setSortIndex(block: (Int) -> Int) {
        sortIndex.emit(block(sortIndex.value))
        saveAppSortPreference()
    }

    suspend fun loadAppSortPreference() {
        if (target != Target.Apps) return
        val preference = context.readDashboardSortPreference().first()
        sortIndex.emit(preference.field.toSortIndex())
        sortType.emit(if (preference.ascending) SortType.ASCENDING else SortType.DESCENDING)
    }

    suspend fun setSortType(block: (SortType) -> SortType) {
        sortType.emit(block(sortType.value))
        saveAppSortPreference()
    }

    suspend fun setSearchQuery(value: String) {
        searchQuery.emit(value)
    }

    suspend fun setUserIndex(value: Int) {
        userIndex.emit(value)
    }

    suspend fun setShowFilterSheet(value: Boolean) {
        showFilterSheet.emit(value)
    }

    suspend fun setShowDataItemsSheet(value: Boolean) {
        showDataItemsSheet.emit(value)
    }

    suspend fun cycleLabelFilter(label: String) {
        val filters = labelFilters.value.toMutableMap()
        when (filters[label]) {
            null -> filters[label] = LabelFilterMode.INCLUDE
            LabelFilterMode.INCLUDE -> filters[label] = LabelFilterMode.EXCLUDE
            LabelFilterMode.EXCLUDE -> filters.remove(label)
        }
        labelFilters.emit(filters)
    }

    suspend fun removeLabelFilter(label: String) {
        if (!::labelFilters.isInitialized) return
        labelFilters.emit(labelFilters.value - label)
    }

    suspend fun renameLabelFilter(oldLabel: String, newLabel: String) {
        if (!::labelFilters.isInitialized || oldLabel == newLabel) return
        val mode = labelFilters.value[oldLabel]
        val filters = labelFilters.value - oldLabel
        labelFilters.emit(if (mode == null) filters else filters + (newLabel to mode))
    }

    private suspend fun saveAppSortPreference() {
        if (target != Target.Apps) return
        context.saveDashboardSortPreference(
            field = sortIndex.value.toSortField(),
            ascending = sortType.value == SortType.ASCENDING,
        )
    }

    private fun DashboardSortField.toSortIndex(): Int = when (this) {
        DashboardSortField.INSTALLED -> 1
        DashboardSortField.DATA_SIZE -> 2
        DashboardSortField.UPDATED -> 3
        DashboardSortField.BACKED_UP -> 4
        DashboardSortField.NAME -> 0
    }

    private fun Int.toSortField(): DashboardSortField = when (this) {
        1 -> DashboardSortField.INSTALLED
        2 -> DashboardSortField.DATA_SIZE
        3 -> DashboardSortField.UPDATED
        4 -> DashboardSortField.BACKED_UP
        else -> DashboardSortField.NAME
    }
}

data class Filters(
    val cloud: String,
    val backupDir: String,
    val systemApps: Boolean,
    val nonSystemApps: Boolean,
    val frozenApps: Boolean,
    val unfrozenApps: Boolean,
    val hasBackups: Boolean,
    val hasNoBackups: Boolean,
    val installedApps: Boolean,
    val notInstalledApps: Boolean,
    val hasApkBackup: Boolean,
    val hasNoApkBackup: Boolean,
    val hasDataBackup: Boolean,
    val hasNoDataBackup: Boolean,
    val hasOutdatedApkBackup: Boolean,
)

sealed class ListData(
    open val selected: Long,
    open val selectionMode: Boolean,
    open val total: Long,
    open val searchQuery: String,
    open val showFilterSheet: Boolean,
    open val sortIndex: Int,
    open val sortType: SortType,
    open val isUpdating: Boolean,
    open val labelFilters: Map<String, LabelFilterMode>,
) {
    data class Apps(
        override val selected: Long,
        override val selectionMode: Boolean,
        override val total: Long,
        override val searchQuery: String,
        override val showFilterSheet: Boolean,
        override val sortIndex: Int,
        override val sortType: SortType,
        override val isUpdating: Boolean,
        override val labelFilters: Map<String, LabelFilterMode>,
        val showDataItemsSheet: Boolean,
        val filters: Filters,
        val userIndex: Int,
        val userList: List<UserInfo>,
        val userMap: Map<Int, Long>,
    ) : ListData(selected, selectionMode, total, searchQuery, showFilterSheet, sortIndex, sortType, isUpdating, labelFilters)

    data class Files(
        override val selected: Long,
        override val selectionMode: Boolean,
        override val total: Long,
        override val searchQuery: String,
        override val showFilterSheet: Boolean,
        override val sortIndex: Int,
        override val sortType: SortType,
        override val isUpdating: Boolean,
        override val labelFilters: Map<String, LabelFilterMode>,
    ) : ListData(selected, selectionMode, total, searchQuery, showFilterSheet, sortIndex, sortType, isUpdating, labelFilters)
}

enum class LabelFilterMode {
    INCLUDE,
    EXCLUDE,
}
