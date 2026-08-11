package com.xayah.feature.main.dashboard

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xayah.core.model.BackupRevisionEntity
import com.xayah.core.model.BackupEngine
import com.xayah.core.model.DataState
import com.xayah.core.util.DateUtil
import com.xayah.core.model.database.PackageDataStates
import com.xayah.core.model.database.PackageDataStates.Companion.setSelected
import com.xayah.core.model.database.PackageDataStates.Companion.getSelected
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.ui.component.DataChips
import com.xayah.core.ui.component.TooltipIconButton
import com.xayah.core.ui.theme.ThemedColorSchemeKeyTokens
import com.xayah.core.ui.theme.value
import com.xayah.core.ui.util.LocalNavController
import com.xayah.feature.main.details.AppDetails
import com.xayah.feature.main.details.DetailsUiState
import com.xayah.feature.main.details.DetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRevisionsRoute(
    viewModel: AppRevisionsViewModel = hiltViewModel(),
    detailsViewModel: DetailsViewModel = hiltViewModel(),
) {
    val app by viewModel.app.collectAsStateWithLifecycle()
    val revisions by viewModel.revisions.collectAsStateWithLifecycle()
    val installedApp by viewModel.installedApp.collectAsStateWithLifecycle()
    val detailsState by detailsViewModel.uiState.collectAsStateWithLifecycle()
    val furtherOperations by detailsViewModel.furtherOperations.collectAsStateWithLifecycle()
    val navController = LocalNavController.current!!
    var selectedRevision by remember { mutableStateOf<BackupRevisionEntity?>(null) }
    var restoreCandidate by remember { mutableStateOf<BackupRevisionEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<BackupRevisionEntity?>(null) }
    var backupCandidate by remember { mutableStateOf<PackageEntity?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var blacklistConfirmation by remember { mutableStateOf<Boolean?>(null) }
    val appDetails = detailsState as? DetailsUiState.Success.App

    LaunchedEffect(Unit) {
        detailsViewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(app?.label ?: stringResource(R.string.backup_revisions)) },
                navigationIcon = {
                    TooltipIconButton(tooltip = stringResource(R.string.back), onClick = navController::navigateUp) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (appDetails != null && installedApp != null) {
                        TooltipIconButton(tooltip = stringResource(R.string.share), onClick = detailsViewModel::shareApk) {
                            Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.share))
                        }
                        Box {
                            TooltipIconButton(tooltip = stringResource(R.string.more_options), onClick = { menuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.clear_cache)) },
                                    leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        detailsViewModel.clearAppCache()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.open_settings)) },
                                    leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        detailsViewModel.openAppSettings()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (appDetails.app.extraInfo.blocked) R.string.remove_from_blacklist
                                                else R.string.add_to_blacklist
                                            )
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        blacklistConfirmation = appDetails.app.extraInfo.blocked
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(DashboardDimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(DashboardDimens.ItemSpacing),
        ) {
            item {
                Text(
                    text = stringResource(R.string.backup_revisions),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (installedApp != null) {
                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { backupCandidate = installedApp },
                    ) {
                        Text(stringResource(R.string.back_up))
                    }
                }
            }
            if (revisions.isEmpty()) {
                item {
                    Text(
                        modifier = Modifier.padding(vertical = DashboardDimens.EmptyPadding),
                        text = stringResource(R.string.no_backup_revisions),
                        color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value,
                    )
                }
            } else {
                items(revisions, key = BackupRevisionEntity::id) { revision ->
                    RevisionItem(revision, onClick = { selectedRevision = revision })
                }
            }

            if (detailsState is DetailsUiState.Success.App) {
                item {
                    AppDetails(
                        uiState = detailsState as DetailsUiState.Success.App,
                        onSetDataStates = detailsViewModel::setDataStates,
                        onAddLabel = detailsViewModel::addLabel,
                        onDeleteLabel = detailsViewModel::deleteLabel,
                        onSetLabelColor = detailsViewModel::setLabelColor,
                        onSelectLabel = detailsViewModel::selectAppLabel,
                        onUninstall = detailsViewModel::uninstallApp,
                        onClearData = detailsViewModel::clearAppData,
                        onCopyDataPath = detailsViewModel::copyDataPath,
                        onSaveAppIcon = detailsViewModel::saveAppIcon,
                        onShareApk = detailsViewModel::shareApk,
                        furtherOperations = furtherOperations,
                        onLoadFurtherOperations = detailsViewModel::loadFurtherOperations,
                        onOpenFurtherOperation = detailsViewModel::openFurtherOperation,
                        onFreeze = detailsViewModel::freezeApp,
                        onLaunch = detailsViewModel::launchApp,
                        onProtect = detailsViewModel::protect,
                        onDelete = detailsViewModel::delete,
                    )
                }
            }
        }
    }

    blacklistConfirmation?.let { blocked ->
        AlertDialog(
            onDismissRequest = { blacklistConfirmation = null },
            title = { Text(stringResource(R.string.blacklist)) },
            text = {
                Text(stringResource(if (blocked) R.string.confirm_remove_from_blacklist else R.string.confirm_add_to_blacklist))
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        blacklistConfirmation = null
                        detailsViewModel.block(blocked)
                    },
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { blacklistConfirmation = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    selectedRevision?.let { revision ->
        RevisionDetailsDialog(
            revision = revision,
            onDismiss = { selectedRevision = null },
            onRestore = {
                selectedRevision = null
                restoreCandidate = revision
            },
            onDelete = {
                selectedRevision = null
                deleteCandidate = revision
            },
        )
    }

    restoreCandidate?.let { revision ->
        RestoreScopeDialog(
            revision = revision,
            onDismiss = { restoreCandidate = null },
            onConfirm = { dataStates ->
                restoreCandidate = null
                viewModel.startRestore(revision, dataStates, navController)
            },
        )
    }

    deleteCandidate?.let { revision ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(stringResource(R.string.delete_revision)) },
            text = { Text(stringResource(R.string.confirm_delete_revision)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        deleteCandidate = null
                        viewModel.deleteRevision(revision)
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteCandidate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    backupCandidate?.let { candidate ->
        BackupScopeDialog(
            app = candidate,
            onDismiss = { backupCandidate = null },
            onConfirm = { dataStates ->
                backupCandidate = null
                viewModel.startBackup(dataStates, navController)
            },
        )
    }
}

@Composable
private fun BackupScopeDialog(
    app: PackageEntity,
    onDismiss: () -> Unit,
    onConfirm: (PackageDataStates) -> Unit,
) {
    var selections by remember(app.id) { mutableStateOf(app.dataStates.copy()) }
    val hasSelection = with(selections) {
        apkState == DataState.Selected || userState == DataState.Selected ||
            userDeState == DataState.Selected || dataState == DataState.Selected ||
            obbState == DataState.Selected || mediaState == DataState.Selected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_scope)) },
        text = {
            DataChips(selections = selections, displayStats = app.displayStats) { type, selected ->
                selections = type.setSelected(selections, selected.not())
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                enabled = hasSelection,
                onClick = { onConfirm(selections) },
            ) {
                Text(stringResource(R.string.back_up))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun RestoreScopeDialog(
    revision: BackupRevisionEntity,
    onDismiss: () -> Unit,
    onConfirm: (PackageDataStates) -> Unit,
) {
    val available = remember(revision.contentMask) { revision.contentMask.toDataStates() }
    var selections by remember(revision.id) { mutableStateOf(available) }
    val hasSelection = with(selections) {
        apkState == DataState.Selected || userState == DataState.Selected ||
            userDeState == DataState.Selected || dataState == DataState.Selected ||
            obbState == DataState.Selected || mediaState == DataState.Selected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_scope)) },
        text = {
            DataChips(
                selections = selections,
                isEnabled = { type -> type.getSelected(available) },
            ) { type, selected ->
                selections = type.setSelected(selections, !selected)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = hasSelection, onClick = { onConfirm(selections) }) {
                Text(stringResource(R.string.restore))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun Int.toDataStates() = PackageDataStates(
    apkState = if (this and 1 != 0) DataState.Selected else DataState.NotSelected,
    userState = if (this and 2 != 0) DataState.Selected else DataState.NotSelected,
    userDeState = if (this and 4 != 0) DataState.Selected else DataState.NotSelected,
    dataState = if (this and 8 != 0) DataState.Selected else DataState.NotSelected,
    obbState = if (this and 16 != 0) DataState.Selected else DataState.NotSelected,
    mediaState = if (this and 32 != 0) DataState.Selected else DataState.NotSelected,
)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RevisionItem(revision: BackupRevisionEntity, onClick: () -> Unit) {
    val contents = buildList {
        if (revision.contentMask and 1 != 0) add(stringResource(R.string.apk))
        if (revision.contentMask and 62 != 0) add(stringResource(R.string.app_data))
    }.joinToString()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(DashboardDimens.ItemPadding),
            verticalArrangement = Arrangement.spacedBy(DashboardDimens.ItemSpacing),
        ) {
            Text(
                text = revision.appVersionName.ifEmpty { revision.appVersionCode.toString() },
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = DateUtils.getRelativeTimeSpanString(revision.createdAt).toString(),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.revision_details,
                    revision.engine.name.lowercase(),
                    contents,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value,
            )
        }
    }
}

@Composable
private fun RevisionDetailsDialog(
    revision: BackupRevisionEntity,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val contents = buildList {
        if (revision.contentMask and 1 != 0) add(stringResource(R.string.apk))
        if (revision.contentMask and 62 != 0) add(stringResource(R.string.app_data))
    }.joinToString()
    val size = Formatter.formatFileSize(context, revision.sizeBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.revision_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(DashboardDimens.ItemSpacing)) {
                Text(revision.appVersionName.ifEmpty { revision.appVersionCode.toString() })
                Text(stringResource(R.string.created_at, DateUtil.formatTimestamp(revision.createdAt, DateUtil.PATTERN_YMD_HMS)))
                Text(stringResource(R.string.revision_details, revision.engine.name.lowercase(), contents))
                Text(size)
            }
        },
        confirmButton = {
            androidx.compose.foundation.layout.Row {
                androidx.compose.material3.TextButton(
                    enabled = revision.engine == BackupEngine.LEGACY,
                    onClick = onRestore,
                ) {
                    Text(stringResource(R.string.restore))
                }
                androidx.compose.material3.TextButton(
                    enabled = revision.engine == BackupEngine.LEGACY,
                    onClick = onDelete,
                ) {
                    Text(stringResource(R.string.delete_revision), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
