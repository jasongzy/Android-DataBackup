package com.xayah.feature.main.details

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.RemoveRedEye
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded._123
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import com.xayah.core.common.util.toLineString
import com.xayah.core.model.OpType
import com.xayah.core.model.DataType
import com.xayah.core.model.LabelPalette
import com.xayah.core.model.database.LabelAppCrossRefEntity
import com.xayah.core.model.ColoredLabel
import com.xayah.core.model.database.PackageDataStates
import com.xayah.core.model.database.PackageDataStates.Companion.setSelected
import com.xayah.core.model.database.PackageEntity
import com.xayah.core.model.database.PackagePermission
import com.xayah.core.ui.component.ActionSegmentedButton
import com.xayah.core.ui.component.AnimatedModalDropdownMenu
import com.xayah.core.ui.component.BodyMediumText
import com.xayah.core.ui.component.BodyLargeText
import com.xayah.core.ui.component.BottomButton
import com.xayah.core.ui.component.Clickable
import com.xayah.core.ui.component.DataChips
import com.xayah.core.ui.component.DropdownMenuItem
import com.xayah.core.ui.component.FilledTonalIconTextButton
import com.xayah.core.ui.component.LocalSlotScope
import com.xayah.core.ui.component.ModalBottomSheet
import com.xayah.core.ui.component.PackageIconImage
import com.xayah.core.ui.component.Title
import com.xayah.core.ui.component.TitleLargeText
import com.xayah.core.ui.component.TooltipIconButton
import com.xayah.core.ui.component.confirm
import com.xayah.core.ui.component.edit
import com.xayah.core.ui.component.paddingHorizontal
import com.xayah.core.ui.component.paddingVertical
import com.xayah.core.ui.theme.ThemedColorSchemeKeyTokens
import com.xayah.core.ui.theme.value
import com.xayah.core.ui.theme.withState
import com.xayah.core.ui.token.SizeTokens
import com.xayah.core.util.DateUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppDetails(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    uiState: DetailsUiState.Success.App,
    onSetDataStates: (Long, PackageDataStates) -> Unit,
    onAddLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onSetLabelColor: (String, Long) -> Unit,
    onSelectLabel: (Boolean, LabelAppCrossRefEntity?) -> Unit,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onCopyDataPath: (DataType) -> Unit,
    onResolveDataPath: (DataType, (String) -> Unit) -> Unit,
    onCopyPath: (String) -> Unit,
    onOpenPath: (String) -> Unit,
    onEditPermissions: () -> Unit,
    onSaveAppIcon: () -> Unit,
    onShareApk: () -> Unit,
    furtherOperations: FurtherOperationsUiState,
    onLoadFurtherOperations: () -> Unit,
    onOpenFurtherOperation: (FurtherOperation) -> Unit,
    onFreeze: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onProtect: () -> Unit,
    onDelete: () -> Unit
) {
    var isShow by remember { mutableStateOf(false) }
    var showFurtherOperations by remember { mutableStateOf(false) }
    var selectedDataPath by remember { mutableStateOf<String?>(null) }
    var colorCandidate by remember { mutableStateOf<ColoredLabel?>(null) }
    val sheetState = rememberModalBottomSheetState()
    val onDismissRequest: () -> Unit = {
        coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                isShow = false
            }
        }
    }
    val app = uiState.app
    val opType = app.indexInfo.opType
    val context = LocalContext.current
    val dialogState = LocalSlotScope.current!!.dialogSlot

    LabelsBottomSheet(isShow, sheetState, onDismissRequest, app, uiState.refs, uiState.labels, onAddLabel, onDeleteLabel, onSelectLabel)
    if (showFurtherOperations) {
        FurtherOperationsBottomSheet(
            onDismiss = { showFurtherOperations = false },
            onShareApk = {
                showFurtherOperations = false
                onShareApk()
            },
            onCopyApkPath = {
                showFurtherOperations = false
                onCopyDataPath(DataType.PACKAGE_APK)
            },
            onOpenFurtherOperation = { operation ->
                showFurtherOperations = false
                onOpenFurtherOperation(operation)
            },
            uiState = furtherOperations,
        )
    }
    selectedDataPath?.let { path ->
        DataPathBottomSheet(
            path = path,
            onDismiss = { selectedDataPath = null },
            onCopy = {
                onCopyPath(path)
                selectedDataPath = null
            },
            onOpen = {
                onOpenPath(path)
                selectedDataPath = null
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(SizeTokens.Level12))

        Box(
            modifier = Modifier.combinedClickable(
                onClick = {
                    if (opType == OpType.BACKUP) {
                        showFurtherOperations = true
                        onLoadFurtherOperations()
                    }
                },
                onLongClick = {
                    dialogState.confirm(
                        title = context.getString(R.string.save_app_icon),
                        text = context.getString(R.string.confirm_save_app_icon),
                        onConfirm = onSaveAppIcon,
                    )
                },
            )
        ) {
            PackageIconImage(packageName = app.packageName, size = SizeTokens.Level100)
        }

        Spacer(Modifier.height(SizeTokens.Level12))

        TitleLargeText(text = app.packageInfo.label, color = ThemedColorSchemeKeyTokens.OnSurface.value)
        BodyMediumText(text = app.packageName, color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value)
        BodyMediumText(text = app.packageInfo.versionName, color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value)
        LabelsFlow(
            opType = opType,
            app = app,
            refs = uiState.refs,
            labels = uiState.labels,
            onEditColor = { colorCandidate = it },
            onAdd = { isShow = true },
        )

        Spacer(Modifier.height(SizeTokens.Level12))

        ActionsRow(opType = opType, frozen = app.extraInfo.enabled.not(), protected = app.preserveId != 0L, onUninstall = onUninstall, onClearData = onClearData, onFreeze = onFreeze, onLaunch = onLaunch, onProtect = onProtect, onDelete = onDelete)

        Spacer(Modifier.height(SizeTokens.Level12))

        BackupParts(
            app = app,
            isCalculating = uiState.isRefreshing,
            onSetDataStates = onSetDataStates,
            onShowDataPath = { dataType ->
                onResolveDataPath(dataType) { selectedDataPath = it }
            },
        )

        Info(app = app, architecture = uiState.architecture, targetSdk = uiState.targetSdk)

        Permissions(permissions = app.extraInfo.permissions, onClick = onEditPermissions)
    }

    colorCandidate?.let { label ->
        LabelColorDialog(
            label = label,
            onDismiss = { colorCandidate = null },
            onSelect = { color ->
                onSetLabelColor(label.label, color)
                colorCandidate = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataPathBottomSheet(
    path: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Title(stringResource(R.string.data_path))
        SelectionContainer {
            BodyMediumText(
                modifier = Modifier
                    .fillMaxWidth()
                    .paddingHorizontal(SizeTokens.Level24)
                    .paddingVertical(SizeTokens.Level12),
                text = path,
                color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .paddingHorizontal(SizeTokens.Level24),
            horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
        ) {
            FilledTonalIconTextButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy,
                text = stringResource(R.string.copy),
                onClick = onCopy,
            )
            FilledTonalIconTextButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.FolderOpen,
                text = stringResource(R.string.open_path),
                onClick = onOpen,
            )
        }
        Spacer(Modifier.height(SizeTokens.Level24))
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun FurtherOperationsBottomSheet(
    onDismiss: () -> Unit,
    onShareApk: () -> Unit,
    onCopyApkPath: () -> Unit,
    onOpenFurtherOperation: (FurtherOperation) -> Unit,
    uiState: FurtherOperationsUiState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Title(stringResource(R.string.further_operations))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .paddingHorizontal(SizeTokens.Level24),
            horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
        ) {
            FilledTonalIconTextButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Share,
                text = stringResource(R.string.share),
                onClick = onShareApk,
            )
            FilledTonalIconTextButton(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.ContentCopy,
                text = stringResource(R.string.copy_apk_path),
                onClick = onCopyApkPath,
            )
        }
        Spacer(Modifier.height(SizeTokens.Level12))
        when (uiState) {
            FurtherOperationsUiState.Idle,
            FurtherOperationsUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SizeTokens.Level80),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is FurtherOperationsUiState.Content -> {
                if (uiState.operations.isEmpty()) {
                    BodyMediumText(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SizeTokens.Level24),
                        text = stringResource(R.string.no_external_actions),
                        color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value,
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .paddingHorizontal(SizeTokens.Level16),
                        maxItemsInEachRow = 4,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalArrangement = Arrangement.spacedBy(SizeTokens.Level12),
                    ) {
                        uiState.operations.forEach { operation ->
                            Column(
                                modifier = Modifier
                                    .width(SizeTokens.Level80)
                                    .clickable { onOpenFurtherOperation(operation) }
                                    .padding(vertical = SizeTokens.Level8),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                PackageIconImage(packageName = operation.packageName, size = SizeTokens.Level48)
                                Spacer(Modifier.height(SizeTokens.Level8))
                                Text(
                                    text = operation.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(SizeTokens.Level24))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsFlow(
    opType: OpType,
    app: PackageEntity,
    refs: List<LabelAppCrossRefEntity>,
    labels: List<ColoredLabel>,
    onEditColor: (ColoredLabel) -> Unit,
    onAdd: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .paddingHorizontal(SizeTokens.Level24),
        horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(-SizeTokens.Level8)
    ) {
        if (app.isSystemApp) {
            FilterChip(
                onClick = { },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.BluePrimaryContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.BlueOnPrimaryContainer.value),
                label = { Text(stringResource(R.string.system_app)) },
            )
        }
        if (app.isUpdatedSystemApp) {
            FilterChip(
                onClick = { },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.OnErrorContainer.value),
                label = { Text(stringResource(R.string.updated)) },
            )
        }
        when (opType) {
            OpType.BACKUP -> {
                if (app.extraInfo.enabled.not()) {
                    FilterChip(
                        onClick = { },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.OnErrorContainer.value),
                        label = { Text(stringResource(R.string.disabled)) },
                    )
                }
                if (app.extraInfo.blocked) {
                    FilterChip(
                        onClick = { },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.OnErrorContainer.value),
                        label = { Text(stringResource(R.string.blacklist)) },
                    )
                }
            }

            OpType.RESTORE -> {
                if (app.preserveId != 0L) {
                    FilterChip(
                        onClick = { },
                        selected = true,
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.YellowPrimaryContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.YellowOnPrimaryContainer.value),
                        label = { Text(stringResource(R.string._protected)) },
                    )
                }
            }
        }

        if (app.extraInfo.hasKeystore) {
            FilterChip(
                onClick = { },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.BluePrimaryContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.BlueOnPrimaryContainer.value),
                label = { Text(stringResource(R.string.keystore)) },
            )
        }
        if (app.extraInfo.ssaid.isNotEmpty()) {
            FilterChip(
                onClick = { },
                selected = true,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ThemedColorSchemeKeyTokens.BluePrimaryContainer.value, selectedLabelColor = ThemedColorSchemeKeyTokens.BlueOnPrimaryContainer.value),
                label = { Text(stringResource(R.string.ssaid)) },
            )
        }

        val labelsByName = labels.associateBy(ColoredLabel::label)
        refs.forEach { item ->
            labelsByName[item.label]?.let { label ->
                val color = Color(label.colorArgb)
                androidx.compose.material3.Surface(
                    onClick = { onEditColor(label) },
                    color = Color.Transparent,
                    contentColor = color,
                    shape = androidx.compose.material3.MaterialTheme.shapes.small,
                    border = BorderStroke(SizeTokens.Level1, color),
                ) {
                    Text(
                        modifier = Modifier.paddingHorizontal(SizeTokens.Level8).paddingVertical(SizeTokens.Level4),
                        text = label.label,
                    )
                }
            }
        }
        TooltipIconButton(tooltip = stringResource(R.string.add_label), onClick = onAdd) {
            Icon(Icons.Rounded.Add, contentDescription = null)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
private fun LabelsBottomSheet(
    isShow: Boolean,
    sheetState: SheetState,
    onDismissRequest: () -> Unit,
    app: PackageEntity,
    refs: List<LabelAppCrossRefEntity>,
    labels: List<ColoredLabel>,
    onAddLabel: (String) -> Unit,
    onDeleteLabel: (String) -> Unit,
    onSelectLabel: (Boolean, LabelAppCrossRefEntity?) -> Unit,
) {
    val context = LocalContext.current
    val dialogState = LocalSlotScope.current!!.dialogSlot
    if (isShow) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState) {
            val selectedLabels by remember(refs) { mutableStateOf(refs.map { it.label }) }

            Title(text = stringResource(id = R.string.labels))

            if (labels.isEmpty()) {
                BodyLargeText(
                    modifier = Modifier
                        .fillMaxWidth()
                        .paddingHorizontal(SizeTokens.Level24),
                    text = stringResource(R.string.no_labels_here),
                    color = ThemedColorSchemeKeyTokens.OnSurfaceVariant.value
                )
            } else {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .paddingHorizontal(SizeTokens.Level24),
                    horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level8),
                    verticalArrangement = Arrangement.spacedBy(-SizeTokens.Level8)
                ) {
                    labels.forEach { item ->
                        var expanded by remember { mutableStateOf(false) }
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
                            val interactionSource = remember { MutableInteractionSource() }
                            val selected by remember(item.label, selectedLabels) { mutableStateOf(item.label in selectedLabels) }
                            Box {
                                FilterChip(
                                    onClick = {},
                                    label = { Text(item.label) },
                                    selected = selected,
                                    leadingIcon = if (selected) {
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
                                    interactionSource = interactionSource,
                                )
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .combinedClickable(
                                            onLongClick = { expanded = true },
                                            onClick = { onSelectLabel(selected, LabelAppCrossRefEntity(item.label, app.packageName, app.userId, app.preserveId)) },
                                            interactionSource = interactionSource,
                                            indication = null,
                                        )
                                )
                            }

                            AnimatedModalDropdownMenu(
                                targetState = null,
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = stringResource(id = R.string.delete),
                                    leadingIcon = Icons.Rounded.DeleteForever,
                                    onClick = { onDeleteLabel(item.label) },
                                )
                            }
                        }
                    }
                }
            }

            BottomButton(text = stringResource(id = R.string.add_label)) {
                dialogState.edit(context.getString(R.string.add_label), label = context.getString(R.string.label), onConfirm = onAddLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.ActionItem(
    enabled: Boolean = true,
    index: Int,
    count: Int,
    title: String,
    icon: ImageVector,
    containerColor: Color = ThemedColorSchemeKeyTokens.SurfaceContainer.value,
    onClick: () -> Unit,
) {
    ActionSegmentedButton(
        enabled = enabled,
        onClick = onClick,
        containerColor = containerColor,
        index = index,
        count = count
    ) {
        CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.withState(enabled)) {
            Column(modifier = Modifier.paddingVertical(SizeTokens.Level8), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(imageVector = icon, contentDescription = null)
                Text(text = title)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActionsRow(
    opType: OpType,
    frozen: Boolean,
    protected: Boolean,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onFreeze: (Boolean) -> Unit,
    onLaunch: () -> Unit,
    onProtect: () -> Unit,
    onDelete: () -> Unit
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .paddingHorizontal(SizeTokens.Level24)
            .height(IntrinsicSize.Min),
        space = SizeTokens.Level0
    ) {
        when (opType) {
            OpType.BACKUP -> {
                BackupActions(frozen, onUninstall, onClearData, onFreeze, onLaunch)
            }

            OpType.RESTORE -> {
                RestoreActions(protected, onProtect, onDelete)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.BackupActions(
    frozen: Boolean,
    onUninstall: () -> Unit,
    onClearData: () -> Unit,
    onFreeze: (Boolean) -> Unit,
    onLaunch: () -> Unit,
) {
    val context = LocalContext.current
    val dialogState = LocalSlotScope.current!!.dialogSlot
    ActionItem(
        index = 0,
        count = 4,
        title = stringResource(R.string.uninstall),
        icon = Icons.Rounded.DeleteForever,
        containerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value,
    ) {
        dialogState.confirm(
            title = context.getString(R.string.prompt),
            text = context.getString(R.string.confirm_uninstall),
            onConfirm = onUninstall,
        )
    }
    ActionItem(
        index = 1,
        count = 4,
        title = stringResource(R.string.clear_data),
        icon = Icons.Rounded.CleaningServices,
        containerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value,
    ) {
        dialogState.confirm(
            title = context.getString(R.string.prompt),
            text = context.getString(R.string.confirm_clear_data),
            onConfirm = onClearData,
        )
    }
    ActionItem(
        index = 2,
        count = 4,
        title = stringResource(if (frozen) R.string.unfreeze else R.string.freeze),
        icon = Icons.Rounded.AcUnit
    ) {
        dialogState.confirm(
            title = context.getString(R.string.prompt),
            text = context.getString(if (frozen) R.string.confirm_unfreeze else R.string.confirm_freeze),
            onConfirm = {
                onFreeze(frozen)
            }
        )
    }
    ActionItem(
        enabled = frozen.not(),
        index = 3,
        count = 4,
        title = context.getString(R.string.launch),
        icon = Icons.Rounded.RocketLaunch
    ) {
        onLaunch()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleChoiceSegmentedButtonRowScope.RestoreActions(protected: Boolean, onProtect: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val dialogState = LocalSlotScope.current!!.dialogSlot
    ActionItem(
        enabled = protected.not(),
        index = 0,
        count = 2,
        title = stringResource(R.string._protected),
        icon = Icons.Outlined.Shield
    ) {
        dialogState.confirm(
            title = context.getString(R.string.protect),
            text = context.getString(R.string.protect_desc),
            onConfirm = {
                onProtect()
            }
        )
    }
    ActionItem(
        index = 1,
        count = 2,
        title = stringResource(R.string.delete),
        icon = Icons.Outlined.DeleteForever,
        containerColor = ThemedColorSchemeKeyTokens.ErrorContainer.value
    ) {
        dialogState.confirm(
            title = context.getString(R.string.delete),
            text = context.getString(R.string.delete_desc),
            onConfirm = {
                onDelete()
            }
        )
    }
}

@Composable
private fun BackupParts(
    app: PackageEntity,
    isCalculating: Boolean,
    onSetDataStates: (Long, PackageDataStates) -> Unit,
    onShowDataPath: (DataType) -> Unit,
) {
    Title(title = stringResource(id = R.string.backup_parts)) {
        DataChips(
            selections = app.dataStates,
            displayStats = app.displayStats,
            isCalculating = isCalculating,
            onItemLongClick = onShowDataPath,
        ) { type, selected -> onSetDataStates(app.id, type.setSelected(app.dataStates, selected.not())) }
        Spacer(Modifier.height(SizeTokens.Level12))
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun Info(app: PackageEntity, architecture: String, targetSdk: Int) {
    Title(title = stringResource(id = R.string.info)) {
        Clickable(
            icon = ImageVector.vectorResource(id = R.drawable.ic_rounded_person),
            title = stringResource(id = R.string.user),
            value = app.userId.toString()
        )
        Clickable(
            icon = Icons.Rounded._123,
            title = stringResource(id = R.string.uid),
            value = app.extraInfo.uid.toString()
        )
        Clickable(
            icon = Icons.Rounded.Apps,
            title = stringResource(id = R.string.version),
            value = "${app.packageInfo.versionName} (${app.packageInfo.versionCode})"
        )
        if (architecture.isNotEmpty()) {
            Clickable(
                icon = Icons.Rounded.Memory,
                title = stringResource(id = R.string.architecture),
                value = architecture,
            )
        }
        if (targetSdk != 0) {
            Clickable(
                icon = Icons.Rounded.Api,
                title = stringResource(id = R.string.target_sdk),
                value = targetSdk.toString(),
            )
        }
        if (app.packageInfo.firstInstallTime != 0L) {
            Clickable(
                icon = Icons.Rounded.Download,
                title = stringResource(id = R.string.first_install),
                value = DateUtil.formatTimestamp(app.packageInfo.firstInstallTime, DateUtil.PATTERN_YMD),
            )
        }
        if (app.packageInfo.lastUpdateTime != 0L) {
            Clickable(
                icon = Icons.Rounded.Update,
                title = stringResource(id = R.string.last_update),
                value = DateUtil.formatTimestamp(app.packageInfo.lastUpdateTime, DateUtil.PATTERN_YMD_HMS),
            )
        }
        if (app.extraInfo.lastBackupTime != 0L) {
            Clickable(
                icon = ImageVector.vectorResource(id = R.drawable.ic_rounded_acute),
                title = stringResource(id = R.string.last_backup),
                value = DateUtil.formatTimestamp(app.extraInfo.lastBackupTime, DateUtil.PATTERN_YMD_HMS),
            )
        }
        if (app.extraInfo.ssaid.isNotEmpty()) {
            Clickable(
                icon = Icons.Rounded.RemoveRedEye,
                title = stringResource(id = R.string.ssaid),
                value = app.extraInfo.ssaid,
            )
        }
        if (app.preserveId != 0L) {
            Clickable(
                icon = Icons.Outlined.Shield,
                title = stringResource(id = R.string._protected),
                value = DateUtil.formatTimestamp(app.preserveId, DateUtil.PATTERN_FINISH),
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun Permissions(permissions: List<PackagePermission>, onClick: () -> Unit) {
    val granted by remember(permissions) { mutableStateOf(permissions.filter { it.isGranted || it.isOpsAllowed }.map { it.name }) }
    val denied by remember(permissions) { mutableStateOf(permissions.filter { it.isGranted.not() && it.isOpsAllowed.not() }.map { it.name }) }
    if (granted.isNotEmpty() || denied.isNotEmpty()) {
        Title(title = stringResource(id = R.string.permissions)) {
            if (granted.isNotEmpty()) {
                Clickable(
                    title = stringResource(R.string.granted),
                    value = granted.toLineString(),
                    onClick = onClick,
                )
            }
            if (denied.isNotEmpty()) {
                Clickable(
                    title = stringResource(R.string.denied),
                    value = denied.toLineString(),
                    onClick = onClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelColorDialog(label: ColoredLabel, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label.label) },
        text = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SizeTokens.Level12),
                verticalArrangement = Arrangement.spacedBy(SizeTokens.Level12),
            ) {
                LabelPalette.colors.forEach { colorArgb ->
                    val color = Color(colorArgb)
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(40.dp),
                        onClick = { onSelect(colorArgb) },
                        color = color,
                        shape = androidx.compose.foundation.shape.CircleShape,
                        border = if (colorArgb == label.colorArgb) BorderStroke(SizeTokens.Level2, LocalContentColor.current) else null,
                    ) {}
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
