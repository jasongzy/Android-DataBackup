package com.xayah.feature.main.settings

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import com.xayah.core.util.FileUtil
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xayah.core.data.repository.DirectoryRepository
import com.xayah.core.model.database.DirectoryEntity
import com.xayah.core.work.WorkManagerInitializer
import com.xayah.core.ui.viewmodel.BaseViewModel
import com.xayah.core.ui.viewmodel.IndexUiEffect
import com.xayah.core.ui.viewmodel.UiIntent
import com.xayah.core.ui.viewmodel.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data object IndexUiState : UiState

sealed class IndexUiIntent : UiIntent {
    data object ClearCache : IndexUiIntent()
    data object LoadSystemApps : IndexUiIntent()
}

@ExperimentalMaterial3Api
@HiltViewModel
class IndexViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    directoryRepo: DirectoryRepository,
) : BaseViewModel<IndexUiState, IndexUiIntent, IndexUiEffect>(IndexUiState) {
    override suspend fun onEvent(state: IndexUiState, intent: IndexUiIntent) {
        when (intent) {
            IndexUiIntent.LoadSystemApps -> WorkManagerInitializer.fastInitializeAndUpdateApps(context)
            IndexUiIntent.ClearCache -> {
                val directories = buildList {
                    add(context.cacheDir)
                    addAll(context.externalCacheDirs.filterNotNull())
                }.distinctBy { it.absolutePath }
                val before = directories.sumOf { FileUtil.calculateSize(it.absolutePath) }
                directories.forEach { directory ->
                    directory.listFiles()?.forEach { it.deleteRecursively() }
                }
                val after = directories.sumOf { FileUtil.calculateSize(it.absolutePath) }
                val removed = Formatter.formatFileSize(context, (before - after).coerceAtLeast(0))
                withMainContext {
                    Toast.makeText(context, context.getString(R.string.cache_cleared_amount, removed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val _directory: Flow<DirectoryEntity?> = directoryRepo.querySelectedByDirectoryTypeFlow().flowOnIO()
    val directoryState: StateFlow<DirectoryEntity?> = _directory.stateInScope(null)
}
