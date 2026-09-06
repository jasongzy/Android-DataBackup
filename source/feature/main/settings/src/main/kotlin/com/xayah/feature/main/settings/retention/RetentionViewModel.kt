package com.xayah.feature.main.settings.retention

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xayah.core.data.repository.AppBackupRepository
import com.xayah.feature.main.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RetentionUiState(
    val retainCount: String = "1",
    val hasScanned: Boolean = false,
    val isScanning: Boolean = false,
    val isDeleting: Boolean = false,
    val completed: Int = 0,
    val deleteTotal: Int = 0,
    val candidates: List<AppBackupRepository.RetentionCandidate> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
)

@HiltViewModel
class RetentionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppBackupRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RetentionUiState())
    val uiState = _uiState.asStateFlow()

    fun setRetainCount(value: String) {
        if (value.isNotEmpty() && value.any { !it.isDigit() }) return
        _uiState.value = _uiState.value.copy(
            retainCount = value,
            hasScanned = false,
            candidates = emptyList(),
            selectedIds = emptySet(),
        )
    }

    fun toggle(id: String) {
        val state = _uiState.value
        if (state.candidates.none { it.revision.id == id && it.removable }) return
        _uiState.value = state.copy(
            selectedIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id,
        )
    }

    fun toggleGroup(packageName: String, userId: Int) {
        val state = _uiState.value
        val ids = state.candidates
            .filter { it.removable && it.revision.packageName == packageName && it.revision.userId == userId }
            .mapTo(mutableSetOf()) { it.revision.id }
        _uiState.value = state.copy(
            selectedIds = if (ids.all(state.selectedIds::contains)) state.selectedIds - ids else state.selectedIds + ids,
        )
    }

    fun selectAll(selected: Boolean) {
        val state = _uiState.value
        _uiState.value = state.copy(
            selectedIds = if (selected) state.candidates.filter { it.removable }.mapTo(mutableSetOf()) { it.revision.id }
            else emptySet(),
        )
    }

    fun scan() {
        val count = _uiState.value.retainCount.toIntOrNull()?.takeIf { it >= 1 } ?: return
        if (_uiState.value.isScanning || _uiState.value.isDeleting) return
        _uiState.value = _uiState.value.copy(isScanning = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                updateCandidates(repository.getLocalRetentionCandidates(count))
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    fun deleteSelected() {
        val state = _uiState.value
        val retainCount = state.retainCount.toIntOrNull()?.takeIf { it >= 1 } ?: return
        val candidateIds = state.selectedIds
        if (candidateIds.isEmpty() || state.isScanning || state.isDeleting) return
        _uiState.value = _uiState.value.copy(
            isDeleting = true,
            completed = 0,
            deleteTotal = candidateIds.size,
        )
        viewModelScope.launch(Dispatchers.IO) {
            val deletedIds = mutableSetOf<String>()
            try {
                val candidates = repository.getLocalRetentionCandidates(retainCount)
                    .filter { it.removable && it.revision.id in candidateIds }
                _uiState.value = _uiState.value.copy(deleteTotal = candidates.size)
                candidates.forEachIndexed { index, candidate ->
                    if (repository.deleteRevision(candidate.revision)) deletedIds += candidate.revision.id
                    _uiState.value = _uiState.value.copy(completed = index + 1)
                }
                updateCandidates(repository.getLocalRetentionCandidates(retainCount))
                withContext(Dispatchers.Main.immediate) {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.old_backup_cleanup_result,
                            deletedIds.size,
                            candidates.size - deletedIds.size,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } finally {
                _uiState.value = _uiState.value.copy(isDeleting = false)
            }
        }
    }

    private fun updateCandidates(candidates: List<AppBackupRepository.RetentionCandidate>) {
        _uiState.value = _uiState.value.copy(
            hasScanned = true,
            candidates = candidates,
            selectedIds = candidates.filter { it.removable }.mapTo(mutableSetOf()) { it.revision.id },
        )
    }
}
