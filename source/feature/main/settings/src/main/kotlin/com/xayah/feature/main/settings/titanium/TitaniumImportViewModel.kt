package com.xayah.feature.main.settings.titanium

import android.app.Activity
import androidx.compose.material3.ExperimentalMaterial3Api
import com.xayah.core.data.repository.TitaniumImportRepository
import com.xayah.core.ui.viewmodel.BaseViewModel
import com.xayah.core.ui.viewmodel.IndexUiEffect
import com.xayah.core.ui.viewmodel.UiIntent
import com.xayah.core.ui.viewmodel.UiState
import com.xayah.libpickyou.PickYouLauncher
import com.xayah.libpickyou.ui.model.PermissionType
import com.xayah.libpickyou.ui.model.PickerType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

data class TitaniumImportUiState(
    val backupPath: String,
    val labelPath: String,
    val mode: ImportMode,
    val stage: ImportStage,
    val cancelling: Boolean,
    val completed: Int,
    val total: Int,
    val backupCandidates: List<TitaniumImportRepository.BackupCandidate>,
    val labelCandidates: List<TitaniumImportRepository.LabelCandidate>,
    val selectedBackupIds: Set<String>,
    val selectedLabels: Set<String>,
    val searchQuery: String,
    val backupResults: List<TitaniumImportRepository.BackupResult>,
    val labelResults: List<TitaniumImportRepository.LabelResult>,
    val error: String?,
) : UiState

enum class ImportMode { BACKUPS, LABELS }
enum class ImportStage { IDLE, SCANNING, PREVIEW, IMPORTING, COMPLETE }

val TitaniumImportUiState.isBusy: Boolean
    get() = stage == ImportStage.SCANNING || stage == ImportStage.IMPORTING

sealed class TitaniumImportIntent : UiIntent {
    data class SelectBackupPath(val activity: Activity) : TitaniumImportIntent()
    data class SelectLabelPath(val activity: Activity) : TitaniumImportIntent()
    data class SetMode(val mode: ImportMode) : TitaniumImportIntent()
    data class ToggleBackup(val id: String) : TitaniumImportIntent()
    data class ToggleBackupGroup(val packageName: String) : TitaniumImportIntent()
    data class ToggleLabel(val label: String) : TitaniumImportIntent()
    data class SelectAll(val selected: Boolean) : TitaniumImportIntent()
    data class SetSearchQuery(val value: String) : TitaniumImportIntent()
    data object Scan : TitaniumImportIntent()
    data object Import : TitaniumImportIntent()
}

@ExperimentalMaterial3Api
@HiltViewModel
class TitaniumImportViewModel @Inject constructor(
    private val repository: TitaniumImportRepository,
) : BaseViewModel<TitaniumImportUiState, TitaniumImportIntent, IndexUiEffect>(
    TitaniumImportUiState(
        backupPath = "",
        labelPath = "",
        mode = ImportMode.BACKUPS,
        stage = ImportStage.IDLE,
        cancelling = false,
        completed = 0,
        total = 0,
        backupCandidates = emptyList(),
        labelCandidates = emptyList(),
        selectedBackupIds = emptySet(),
        selectedLabels = emptySet(),
        searchQuery = "",
        backupResults = emptyList(),
        labelResults = emptyList(),
        error = null,
    )
) {
    private var operationJob: Job? = null
    private val modeStates = mutableMapOf<ImportMode, TitaniumImportUiState>()

    init {
        launchOnIO {
            emitState(
                uiState.value.copy(
                    backupPath = repository.detectBackupDir().orEmpty(),
                    labelPath = repository.detectLabelDatabase().orEmpty(),
                )
            )
        }
    }

    override suspend fun onEvent(state: TitaniumImportUiState, intent: TitaniumImportIntent) {
        when (intent) {
            is TitaniumImportIntent.SetMode -> setMode(state, intent.mode)
            is TitaniumImportIntent.SelectBackupPath -> selectPath(intent.activity, PickerType.DIRECTORY) { path ->
                resetCurrent(uiState.value.copy(backupPath = path))
            }
            is TitaniumImportIntent.SelectLabelPath -> selectPath(intent.activity, PickerType.FILE) { path ->
                resetCurrent(uiState.value.copy(labelPath = path))
            }
            is TitaniumImportIntent.ToggleBackup -> toggleBackup(state, intent.id)
            is TitaniumImportIntent.ToggleBackupGroup -> toggleBackupGroup(state, intent.packageName)
            is TitaniumImportIntent.ToggleLabel -> toggleLabel(state, intent.label)
            is TitaniumImportIntent.SelectAll -> selectAll(state, intent.selected)
            is TitaniumImportIntent.SetSearchQuery -> emitState(state.copy(searchQuery = intent.value))
            TitaniumImportIntent.Scan -> startScan(state)
            TitaniumImportIntent.Import -> startImport(state)
        }
    }

    fun cancelImport() {
        launchOnIO {
            emitState(uiState.value.copy(cancelling = true))
            operationJob?.cancel()
        }
    }

    fun discardPreview() {
        repository.clearPreviewCache()
    }

    private suspend fun setMode(state: TitaniumImportUiState, mode: ImportMode) {
        if (mode == state.mode) return
        modeStates[state.mode] = state
        val restored = modeStates[mode] ?: state.copy(
            mode = mode,
            stage = ImportStage.IDLE,
            cancelling = false,
            completed = 0,
            total = 0,
            backupCandidates = emptyList(),
            labelCandidates = emptyList(),
            selectedBackupIds = emptySet(),
            selectedLabels = emptySet(),
            searchQuery = "",
            backupResults = emptyList(),
            labelResults = emptyList(),
            error = null,
        )
        emitState(restored)
    }

    private suspend fun resetCurrent(state: TitaniumImportUiState) {
        if (state.mode == ImportMode.BACKUPS) repository.clearPreviewCache()
        modeStates.remove(state.mode)
        emitState(
            state.copy(
                stage = ImportStage.IDLE,
                cancelling = false,
                completed = 0,
                total = 0,
                backupCandidates = emptyList(),
                labelCandidates = emptyList(),
                selectedBackupIds = emptySet(),
                selectedLabels = emptySet(),
                searchQuery = "",
                backupResults = emptyList(),
                labelResults = emptyList(),
                error = null,
            )
        )
    }

    private fun startScan(state: TitaniumImportUiState) {
        if (operationJob?.isActive == true) return
        operationJob = launchOnIO {
            emitState(state.copy(stage = ImportStage.SCANNING, cancelling = false, completed = 0, total = 0, error = null))
            try {
                when (state.mode) {
                    ImportMode.BACKUPS -> {
                        val candidates = repository.scanBackups(state.backupPath) { completed, total ->
                            emitState(uiState.value.copy(completed = completed, total = total))
                        }
                        emitState(
                            uiState.value.copy(
                                stage = ImportStage.PREVIEW,
                                backupCandidates = candidates,
                                selectedBackupIds = candidates.filter { it.isComplete && !it.imported }.mapTo(mutableSetOf()) { it.id },
                            )
                        )
                    }
                    ImportMode.LABELS -> {
                        val candidates = repository.scanLabels(state.labelPath)
                        emitState(
                            uiState.value.copy(
                                stage = ImportStage.PREVIEW,
                                completed = candidates.size,
                                total = candidates.size,
                                labelCandidates = candidates,
                                selectedLabels = candidates.filterNot { it.imported }.mapTo(mutableSetOf()) { it.name },
                            )
                        )
                    }
                }
            } catch (error: CancellationException) {
                withContext(NonCancellable) {
                    if (state.mode == ImportMode.BACKUPS) repository.clearPreviewCache()
                    emitState(uiState.value.copy(stage = ImportStage.IDLE))
                }
            } catch (error: Throwable) {
                if (state.mode == ImportMode.BACKUPS) repository.clearPreviewCache()
                emitState(uiState.value.copy(stage = ImportStage.IDLE, error = error.message.orEmpty()))
            } finally {
                withContext(NonCancellable) { emitState(uiState.value.copy(cancelling = false)) }
            }
        }
    }

    private fun startImport(state: TitaniumImportUiState) {
        if (operationJob?.isActive == true || state.stage != ImportStage.PREVIEW) return
        operationJob = launchOnIO {
            emitState(state.copy(stage = ImportStage.IMPORTING, cancelling = false, completed = 0, total = 0, backupResults = emptyList(), labelResults = emptyList(), error = null))
            try {
                when (state.mode) {
                    ImportMode.BACKUPS -> repository.importBackups(
                        state.backupCandidates.filter { it.id in state.selectedBackupIds }
                    ) { completed, total, result ->
                        emitState(
                            uiState.value.copy(
                                completed = completed,
                                total = total,
                                backupResults = result?.let { uiState.value.backupResults + it } ?: uiState.value.backupResults,
                            )
                        )
                    }
                    ImportMode.LABELS -> repository.importLabels(
                        state.labelCandidates.filter { it.name in state.selectedLabels }
                    ) { completed, total, result ->
                        emitState(
                            uiState.value.copy(
                                completed = completed,
                                total = total,
                                labelResults = result?.let { uiState.value.labelResults + it } ?: uiState.value.labelResults,
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
            } catch (error: Throwable) {
                emitState(uiState.value.copy(error = error.message.orEmpty()))
            } finally {
                withContext(NonCancellable) {
                    if (state.mode == ImportMode.BACKUPS) repository.clearPreviewCache()
                    emitState(uiState.value.copy(stage = ImportStage.COMPLETE, cancelling = false))
                }
            }
        }
    }

    private suspend fun toggleBackup(state: TitaniumImportUiState, id: String) {
        val candidate = state.backupCandidates.firstOrNull { it.id == id } ?: return
        if (!candidate.isComplete) return
        emitState(state.copy(selectedBackupIds = state.selectedBackupIds.toggle(id)))
    }

    private suspend fun toggleBackupGroup(state: TitaniumImportUiState, packageName: String) {
        val ids = state.backupCandidates.filter { it.packageName == packageName && it.isComplete }.mapTo(mutableSetOf()) { it.id }
        val selected = if (ids.all(state.selectedBackupIds::contains)) state.selectedBackupIds - ids else state.selectedBackupIds + ids
        emitState(state.copy(selectedBackupIds = selected))
    }

    private suspend fun toggleLabel(state: TitaniumImportUiState, label: String) {
        emitState(state.copy(selectedLabels = state.selectedLabels.toggle(label)))
    }

    private suspend fun selectAll(state: TitaniumImportUiState, selected: Boolean) {
        emitState(
            when (state.mode) {
                ImportMode.BACKUPS -> state.copy(
                    selectedBackupIds = if (selected) state.backupCandidates.filter { it.isComplete }.mapTo(mutableSetOf()) { it.id } else emptySet()
                )
                ImportMode.LABELS -> state.copy(
                    selectedLabels = if (selected) state.labelCandidates.mapTo(mutableSetOf()) { it.name } else emptySet()
                )
            }
        )
    }

    private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value

    override fun onCleared() {
        repository.clearPreviewCache()
        super.onCleared()
    }

    private suspend fun selectPath(activity: Activity, pickerType: PickerType, onSelected: suspend (String) -> Unit) {
        withMainContext {
            PickYouLauncher(
                checkPermission = true,
                title = activity.getString(com.xayah.feature.main.settings.R.string.select_source),
                pickerType = pickerType,
                permissionType = PermissionType.ROOT,
            ).apply {
                launch(activity) { path -> launchOnIO { onSelected(path) } }
            }
        }
    }
}
