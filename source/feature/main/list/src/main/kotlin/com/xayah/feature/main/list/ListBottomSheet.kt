package com.xayah.feature.main.list

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xayah.core.data.repository.Filters
import com.xayah.core.data.repository.LabelFilterMode
import com.xayah.core.datastore.readLoadSystemApps
import com.xayah.core.hiddenapi.castTo
import com.xayah.core.model.OpType
import com.xayah.core.model.SortType
import com.xayah.core.model.Target
import com.xayah.core.model.database.CloudEntity
import com.xayah.core.model.ColoredLabel
import com.xayah.core.model.database.PackageDataStates
import com.xayah.core.model.database.PackageDataStates.Companion.setSelected
import com.xayah.core.ui.component.BottomButton
import com.xayah.core.ui.component.DataChips
import com.xayah.core.ui.component.ModalBottomSheet
import com.xayah.core.ui.component.Title
import com.xayah.core.ui.component.TitleSort
import com.xayah.core.ui.component.paddingHorizontal
import com.xayah.core.ui.token.SizeTokens
import com.xayah.core.util.localBackupSaveDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ListBottomSheet(
    viewModel: ListBottomSheetViewModel = hiltViewModel(),
) {
    val uiState: ListBottomSheetUiState by viewModel.uiState.collectAsStateWithLifecycle()

    if (uiState is ListBottomSheetUiState.Success) {
        ListBottomSheet(
            uiState = uiState.castTo(),
            viewModel = viewModel,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListBottomSheet(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    uiState: ListBottomSheetUiState.Success,
    viewModel: ListBottomSheetViewModel,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val onDismissRequest: () -> Unit = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                viewModel.setShowFilterSheet(false)
            }
        }
    }

    when (uiState) {
        is ListBottomSheetUiState.Success.Apps -> {
            AppsFilterSheet(
                isShow = uiState.showFilterSheet,
                sheetState = sheetState,
                opType = uiState.opType,
                clouds = uiState.clouds,
                filters = uiState.filters,
                labelEntities = uiState.labelEntities,
                labelFilters = uiState.labelFilters,
                onClickLabel = viewModel::cycleLabelFilter,
                onLongClickLabel = viewModel::resetLabelFilter,
                setFilters = viewModel::setFilters,
                onDismissRequest = onDismissRequest,
            )

            val dataItemsSheetState = rememberModalBottomSheetState()
            AppsDataItemsSheet(
                isShow = uiState.showDataItemsSheet,
                sheetState = dataItemsSheetState,
                onDismissRequest = {
                    coroutineScope.launch { dataItemsSheetState.hide() }.invokeOnCompletion {
                        if (!dataItemsSheetState.isVisible) {
                            viewModel.setShowDataItemsSheet(false)
                        }
                    }
                },
                onSetDataItems = {
                    viewModel.setDataItems(it)
                }
            )
        }

        is ListBottomSheetUiState.Success.Files -> {
            FilesFilterSheet(
                isShow = uiState.showFilterSheet,
                sheetState = sheetState,
                labelEntities = uiState.labelEntities,
                labelFilters = uiState.labelFilters,
                onClickLabel = viewModel::cycleLabelFilter,
                onLongClickLabel = viewModel::resetLabelFilter,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
private fun SourceChips(clouds: List<CloudEntity>, onChanged: (cloud: String, backupDir: String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .paddingHorizontal(SizeTokens.Level24)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
    ) {
        val context = LocalContext.current
        var index by remember { mutableIntStateOf(0) }
        FilterChip(
            onClick = {
                index = 0
                onChanged("", context.localBackupSaveDir())
            },
            label = { Text(stringResource(R.string.local)) },
            selected = index == 0,
            leadingIcon = if (index == 0) {
                {
                    Icon(
                        imageVector = Icons.Filled.Done,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            } else {
                null
            },
        )

        clouds.forEachIndexed { i, cloudEntity ->
            FilterChip(
                onClick = {
                    index = i + 1
                    onChanged(cloudEntity.name, cloudEntity.remote)
                },
                label = { Text(cloudEntity.name) },
                selected = index - 1 == i,
                leadingIcon = if (index - 1 == i) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else {
                    null
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsFlow(
    labelEntities: List<ColoredLabel>,
    labelFilters: Map<String, LabelFilterMode>,
    onClick: (String) -> Unit,
    onLongClick: (String) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .paddingHorizontal(SizeTokens.Level24),
        horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
        verticalArrangement = Arrangement.spacedBy(SizeTokens.Level4)
    ) {
        labelEntities.forEach { item ->
            val mode = labelFilters[item.label]
            FilterChip(
                modifier = Modifier.pointerInput(item.label) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        awaitLongPressOrCancellation(down.id)?.let { change ->
                            change.consume()
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick(item.label)
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                },
                onClick = { onClick(item.label) },
                label = {
                    Text(
                        when (mode) {
                            LabelFilterMode.INCLUDE -> "+ ${item.label}"
                            LabelFilterMode.EXCLUDE -> "− ${item.label}"
                            null -> item.label
                        }
                    )
                },
                selected = mode != null,
                leadingIcon = if (mode != null) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Done,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactOptions(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().paddingHorizontal(SizeTokens.Level24),
        horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
        verticalArrangement = Arrangement.spacedBy(SizeTokens.Level4),
    ) { content() }
}

@Composable
private fun CompactOption(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
}

@Composable
private fun SortOptions(selected: Int, items: List<String>, onSelect: (Int) -> Unit) {
    CompactOptions {
        items.forEachIndexed { index, item ->
            CompactOption(text = item, selected = selected == index, onClick = { onSelect(index) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsFilterSheet(
    isShow: Boolean,
    sheetState: SheetState,
    opType: OpType,
    clouds: List<CloudEntity>,
    filters: Filters,
    labelEntities: List<ColoredLabel>,
    labelFilters: Map<String, LabelFilterMode>,
    onClickLabel: (String) -> Unit,
    onLongClickLabel: (String) -> Unit,
    setFilters: (Filters) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val loadSystemApps by LocalContext.current.readLoadSystemApps().collectAsStateWithLifecycle(initialValue = filters.systemApps)
    if (isShow) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
            Title(text = stringResource(id = R.string.filters))
            if (opType == OpType.BACKUP) {
                SourceChips(clouds) { cloud, backupDir ->
                    setFilters(filters.copy(cloud = cloud, backupDir = backupDir))
                }
            }
            if (loadSystemApps) {
                CompactOptions {
                    CompactOption(stringResource(R.string.system_apps), filters.systemApps) {
                        setFilters(filters.copy(systemApps = filters.systemApps.not()))
                    }
                    CompactOption(stringResource(R.string.non_system_apps), filters.nonSystemApps) {
                        setFilters(filters.copy(nonSystemApps = filters.nonSystemApps.not()))
                    }
                }
            }
            CompactOptions {
                CompactOption(stringResource(R.string.installed), filters.installedApps) {
                    setFilters(filters.copy(installedApps = filters.installedApps.not()))
                }
                CompactOption(stringResource(R.string.not_installed), filters.notInstalledApps) {
                    setFilters(filters.copy(notInstalledApps = filters.notInstalledApps.not()))
                }
            }
            CompactOptions {
                CompactOption(stringResource(R.string.frozen_apps), filters.frozenApps) {
                    setFilters(filters.copy(frozenApps = filters.frozenApps.not()))
                }
                CompactOption(stringResource(R.string.unfrozen_apps), filters.unfrozenApps) {
                    setFilters(filters.copy(unfrozenApps = filters.unfrozenApps.not()))
                }
            }
            if (opType == OpType.BACKUP) {
                CompactOptions {
                    CompactOption(stringResource(R.string.apps_which_have_backups), filters.hasBackups) {
                        setFilters(filters.copy(hasBackups = filters.hasBackups.not()))
                    }
                    CompactOption(stringResource(R.string.apps_which_have_no_backups), filters.hasNoBackups) {
                        setFilters(filters.copy(hasNoBackups = filters.hasNoBackups.not()))
                    }
                }
                CompactOptions {
                    CompactOption(stringResource(R.string.has_apk_backup), filters.hasApkBackup) {
                        setFilters(filters.copy(hasApkBackup = filters.hasApkBackup.not()))
                    }
                    CompactOption(stringResource(R.string.has_no_apk_backup), filters.hasNoApkBackup) {
                        setFilters(filters.copy(hasNoApkBackup = filters.hasNoApkBackup.not()))
                    }
                    CompactOption(stringResource(R.string.has_outdated_apk_backup), filters.hasOutdatedApkBackup) {
                        setFilters(filters.copy(hasOutdatedApkBackup = filters.hasOutdatedApkBackup.not()))
                    }
                }
                CompactOptions {
                    CompactOption(stringResource(R.string.has_data_backup), filters.hasDataBackup) {
                        setFilters(filters.copy(hasDataBackup = filters.hasDataBackup.not()))
                    }
                    CompactOption(stringResource(R.string.has_no_data_backup), filters.hasNoDataBackup) {
                        setFilters(filters.copy(hasNoDataBackup = filters.hasNoDataBackup.not()))
                    }
                }
            }

            if (labelEntities.isNotEmpty()) {
                Title(text = stringResource(id = R.string.labels))
                LabelsFlow(
                    labelEntities = labelEntities,
                    labelFilters = labelFilters,
                    onClick = onClickLabel,
                    onLongClick = onLongClickLabel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilesFilterSheet(
    isShow: Boolean,
    sheetState: SheetState,
    labelEntities: List<ColoredLabel>,
    labelFilters: Map<String, LabelFilterMode>,
    onClickLabel: (String) -> Unit,
    onLongClickLabel: (String) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (isShow) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
            if (labelEntities.isNotEmpty()) {
                Title(text = stringResource(id = R.string.labels))
                LabelsFlow(
                    labelEntities = labelEntities,
                    labelFilters = labelFilters,
                    onClick = onClickLabel,
                    onLongClick = onLongClickLabel,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SortSheet(
    isShow: Boolean,
    target: Target,
    selected: Int,
    sortType: SortType,
    onSortByType: () -> Unit,
    onSortByIndex: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (isShow) {
        ModalBottomSheet(onDismissRequest = onDismissRequest) {
            TitleSort(text = stringResource(R.string.sort), sortType = sortType, onSort = onSortByType)
            SortOptions(
                selected = selected,
                items = stringArrayResource(
                    if (target == Target.Apps) R.array.backup_sort_type_items_apps else R.array.backup_sort_type_items_files
                ).toList(),
                onSelect = onSortByIndex,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppsDataItemsSheet(
    isShow: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    onSetDataItems: (PackageDataStates) -> Unit,
) {
    if (isShow) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
            Title(text = stringResource(id = R.string.data_items))

            var selections by remember { mutableStateOf(PackageDataStates()) }
            DataChips(selections) { type, selected ->
                selections = type.setSelected(selections, selected.not())
            }

            BottomButton(text = stringResource(id = R.string.confirm)) {
                onDismissRequest()
                onSetDataItems(selections)
            }
        }
    }
}
