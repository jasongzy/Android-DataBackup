package com.xayah.feature.main.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xayah.core.ui.component.InnerTopSpacer
import com.xayah.core.ui.util.LocalNavController

@Composable
fun ListRoute(
    viewModel: ListViewModel = hiltViewModel(),
    isDashboard: Boolean = false,
) {
    val navController = LocalNavController.current!!
    val uiState: ListUiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectionMode = (uiState as? ListUiState.Success)?.selectionMode == true
    BackHandler(
        enabled = selectionMode,
        onBack = viewModel::clearSelection,
    )
    DisposableEffect(viewModel) {
        onDispose { viewModel.clearSelection() }
    }
    LaunchedEffect(isDashboard) {
        if (isDashboard) viewModel.refresh(initial = true)
    }
    ListScreen(uiState, isDashboard, viewModel::refresh) {
        viewModel.toNextPage(navController)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListScreen(uiState: ListUiState, isDashboard: Boolean, onRefresh: () -> Unit, onFabClick: () -> Unit) {
    val scrollState = rememberLazyListState()
    val selectionMode = uiState is ListUiState.Success && uiState.selectionMode

    ListBottomSheet()

    Scaffold(
        topBar = { ListTopBar(isDashboard = isDashboard) },
        floatingActionButtonPosition = FabPosition.End,
        floatingActionButton = {
            AnimatedVisibility(visible = uiState is ListUiState.Success && uiState.selected != 0L, enter = scaleIn(), exit = scaleOut()) {
                ExtendedFloatingActionButton(
                    onClick = onFabClick,
                    icon = { Icon(Icons.Rounded.ChevronRight, null) },
                    text = { Text(text = stringResource(id = R.string.back_up_selected)) },
                )
            }
        }
    ) { innerPadding ->
        Column {
            InnerTopSpacer(innerPadding = innerPadding)

            PullToRefreshBox(
                modifier = Modifier.weight(1f),
                isRefreshing = (uiState as? ListUiState.Success)?.isUpdating == true,
                onRefresh = onRefresh,
            ) {
                ListItems(scrollState = scrollState, selectionMode = selectionMode)
            }
        }
    }
}
