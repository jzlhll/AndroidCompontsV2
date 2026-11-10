package com.au.agit.ui

import com.au.agit.ui.components.AGitButton as Button
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.au.agit.R
import com.au.agit.model.AppTheme
import com.au.agit.model.OperationStatus
import com.au.agit.ui.theme.AGitTheme
import com.au.module_androiduiex.styles.dismissKeyboardOnBlankTap
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AGitRoot(model: AppViewModel = koinViewModel(), onReady: () -> Unit = {}) {
    val catalog by model.catalogFlow.collectAsStateWithLifecycle()
    val loadError by model.loadErrorFlow.collectAsStateWithLifecycle()
    if (catalog == null && loadError == null) return
    AGitTheme(catalog?.theme ?: AppTheme.Black) {
        AGitApp(model)
        SideEffect { onReady() }
    }
}

@Composable
fun AGitApp(model: AppViewModel = koinViewModel()) {
    val catalog by model.catalogFlow.collectAsStateWithLifecycle()
    val loadError by model.loadErrorFlow.collectAsStateWithLifecycle()
    val operation by model.operationFlow.collectAsStateWithLifecycle()
    val notice by model.noticeFlow.collectAsStateWithLifecycle()
    val actionBusy by model.actionBusyFlow.collectAsStateWithLifecycle()
    val savedForm by model.savedFormFlow.collectAsStateWithLifecycle()
    val navigation = rememberAGitNavigationState(model, catalog, savedForm)
    val currentCatalog = catalog
    Surface(Modifier.fillMaxSize().dismissKeyboardOnBlankTap()) {
        if (currentCatalog == null) {
            Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
                if (loadError == null) CircularProgressIndicator()
                else Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(loadError!!)
                    Button(onClick = model::loadCatalog) { Text(stringResource(R.string.retry)) }
                }
            }
        } else {
            AGitNavHost(model, currentCatalog, navigation, actionBusy, savedForm)
        }
        if (operation == null) NoticeHost(notice, model::acknowledgeNotice)
        if (navigation.choosingTokenGroup && currentCatalog != null) {
            TokenGroupPickerDialog(currentCatalog.groups,
                onDismiss = { navigation.choosingTokenGroup = false },
                onGroup = { id -> navigation.choosingTokenGroup = false; navigation.go(CloneKey(id)) },
                onAddGroup = { navigation.choosingTokenGroup = false; navigation.go(TokenKey(cloneAfterSave = true)) })
        }
        operation?.let { state ->
            OperationDialog(state, notice, model::acknowledgeNotice, model::cancelOperation,
                onClose = {
                    model.closeOperation()
                    if (state.status == OperationStatus.Success && state.kind == "clone") {
                        catalog?.currentProject?.id?.let(navigation::openProject)
                    } else if (state.status == OperationStatus.Success && state.kind == "commit") navigation.back()
                }, onPush = model::continuePush)
        }
    }
}
