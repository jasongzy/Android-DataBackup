package com.xayah.core.data.repository

import com.xayah.core.database.dao.PackageDao
import com.xayah.core.model.database.PackageDataStates
import com.xayah.core.model.database.PackageEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRequestStore @Inject constructor(
    private val packageDao: PackageDao,
) {
    private val request = MutableStateFlow<List<PackageEntity>>(emptyList())

    val packages: StateFlow<List<PackageEntity>> = request

    suspend fun prepare(
        ids: Collection<Long>,
        dataStates: Map<Long, PackageDataStates> = emptyMap(),
    ) {
        require(ids.isNotEmpty())
        val packagesById = packageDao.queryByIds(ids.toList()).associateBy(PackageEntity::id)
        request.value = ids.mapNotNull { id ->
            packagesById[id]?.let { app ->
                app.copy(
                    extraInfo = app.extraInfo.copy(activated = false),
                    dataStates = dataStates[id]?.copy() ?: app.dataStates.copy(),
                )
            }
        }
        check(request.value.isNotEmpty())
    }

    fun clear() {
        request.value = emptyList()
    }
}
