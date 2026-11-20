package com.allan.picnote.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.allan.picnote.R
import com.allan.picnote.bluetooth.BluetoothDiscoverability
import com.allan.picnote.bluetooth.BluetoothSystemAction
import com.allan.picnote.bluetooth.BluetoothTransferDevice
import com.allan.picnote.bluetooth.BluetoothTransferPhase
import com.allan.picnote.bluetooth.BluetoothTransferState
import com.allan.picnote.bluetooth.BluetoothTransferViewModel
import com.allan.picnote.data.repositories.Saved
import com.allan.picnote.logEx
import com.allan.picnote.ui.theme.LocalThemePalette
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_androiduiex.styles.ComposeButton as Button
import com.au.module_androiduiex.styles.ComposeOutlinedButton as OutlinedButton
import com.au.module_androiduiex.styles.ComposeTextButton as TextButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.text.NumberFormat
import kotlin.time.Duration.Companion.milliseconds

private enum class BluetoothCancelRequest { Transfer, Leave }
private enum class BluetoothPermissionPrompt { Request, Rationale, ShowingRationale, RationaleAccepted, Settings }

@Composable
fun BluetoothTransferScreen(
    viewModel: BluetoothTransferViewModel,
    receiving: Boolean,
    sendTitle: String?,
    active: Boolean,
    onClose: (openDocumentId: String?, importedDocument: Saved<String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val systemAction by viewModel.actionFlow.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentOnClose by rememberUpdatedState(onClose)
    var cancelRequest by rememberSaveable { mutableStateOf<BluetoothCancelRequest?>(null) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    var openDocumentId by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsError by rememberSaveable { mutableStateOf(false) }
    var permissionPrompt by rememberSaveable { mutableStateOf(BluetoothPermissionPrompt.Request) }
    if (receiving) {
        val view = LocalView.current
        LifecycleResumeEffect(view, active, leaving) {
            val turnOn = active && !leaving && !view.keepScreenOn
            if (turnOn) view.keepScreenOn = true
            onPauseOrDispose { if (turnOn) view.keepScreenOn = false }
        }
    }
    fun permissionPromptFor(missing: List<String>, requestCompleted: Boolean = false): BluetoothPermissionPrompt = when {
        missing.isEmpty() -> BluetoothPermissionPrompt.Request
        activity != null && missing.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) } -> when {
            !requestCompleted && permissionPrompt == BluetoothPermissionPrompt.ShowingRationale -> permissionPrompt
            !requestCompleted && permissionPrompt == BluetoothPermissionPrompt.RationaleAccepted -> permissionPrompt
            else -> BluetoothPermissionPrompt.Rationale
        }
        requestCompleted || permissionPrompt == BluetoothPermissionPrompt.Settings -> BluetoothPermissionPrompt.Settings
        else -> BluetoothPermissionPrompt.Request
    }

    val permissionsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val prompt = permissionPromptFor(viewModel.missingPermissions(), requestCompleted = results.isNotEmpty())
        permissionPrompt = if (prompt == BluetoothPermissionPrompt.RationaleAccepted) BluetoothPermissionPrompt.Rationale else prompt
        viewModel.onPermissionsResult()
    }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onEnableResult()
    }
    val discoverableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onDiscoverabilityResult(result.resultCode)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.onSettingsResult()
    }
    LifecycleResumeEffect(active, leaving) {
        if (active && !leaving) viewModel.onResume()
        onPauseOrDispose { }
    }
    LifecycleResumeEffect(systemAction, active, permissionPrompt, leaving) {
        if (active && !leaving) {
            val action = systemAction
            val missing = if (action == null || action == BluetoothSystemAction.Permissions) viewModel.missingPermissions() else emptyList()
            if (action == null || action == BluetoothSystemAction.Permissions) permissionPrompt = permissionPromptFor(missing)
            val permissionPromptBlocked = action == BluetoothSystemAction.Permissions &&
                permissionPrompt in setOf(BluetoothPermissionPrompt.Settings, BluetoothPermissionPrompt.Rationale, BluetoothPermissionPrompt.ShowingRationale)
            if (action != null && !permissionPromptBlocked && viewModel.beginSystemAction(action)) {
                try {
                    when (action) {
                        BluetoothSystemAction.Permissions -> {
                            if (missing.isEmpty()) viewModel.onPermissionsResult()
                            else permissionsLauncher.launch(missing.toTypedArray())
                        }
                        BluetoothSystemAction.Enable -> enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        BluetoothSystemAction.Discoverable -> discoverableLauncher.launch(
                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, BluetoothTransferViewModel.DiscoverableSeconds),
                        )
                        BluetoothSystemAction.Settings -> Unit
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (permissionPrompt == BluetoothPermissionPrompt.RationaleAccepted) permissionPrompt = BluetoothPermissionPrompt.Rationale
                    logEx(throwable = e, message = "Launch Bluetooth system action failed action=$action")
                    viewModel.onSystemActionFailed(action)
                }
            }
        }
        onPauseOrDispose { }
    }
    fun openSettings(intent: Intent) {
        if (!viewModel.beginSettingsVisit()) return
        try {
            settingsLauncher.launch(intent)
            settingsError = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            viewModel.onSettingsResult()
            logEx(throwable = e, message = "Open Bluetooth settings failed action=${intent.action}")
            settingsError = true
        }
    }
    fun requestLeave() {
        val current = viewModel.stateFlow.value
        if (leaving || current.leaveBlocked) return
        if (current.confirmCancel) cancelRequest = BluetoothCancelRequest.Leave
        else leaving = true
    }
    LaunchedEffect(leaving, active) {
        if (leaving && active) {
            viewModel.leave()
            val current = viewModel.stateFlow.value
            val importedDocument = if (receiving && current.phase == BluetoothTransferPhase.Imported) {
                current.documentId?.let { Saved(it, current.mirrorFailed) }
            } else null
            currentOnClose(openDocumentId, importedDocument)
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(currentInfo = NavigationEventInfo.None),
        isBackEnabled = active,
        onBackCompleted = { requestLeave() },
    )

    val cancelAvailable = when (state.phase) {
        BluetoothTransferPhase.Pairing, BluetoothTransferPhase.Connecting, BluetoothTransferPhase.AwaitingApproval,
        BluetoothTransferPhase.Transferring, BluetoothTransferPhase.AwaitingImport -> true
        else -> false
    }
    val awaitingReceipt = !receiving && state.awaitingReceipt
    val inFlightSystemAction = if (receiving) viewModel.systemActionInFlightFlow.collectAsStateWithLifecycle().value else null
    LaunchedEffect(state.confirmCancel) {
        if (!state.confirmCancel) cancelRequest = null
    }
    val primaryAction = when {
        state.phase == BluetoothTransferPhase.Imported -> R.string.bluetooth_open_document
        !receiving && state.phase == BluetoothTransferPhase.Unconfirmed -> R.string.bluetooth_choose_device_again
        state.permissionRequired && permissionPrompt == BluetoothPermissionPrompt.Settings -> R.string.bluetooth_app_settings
        state.permissionRequired -> R.string.bluetooth_permission_allow
        state.enableRequired -> R.string.bluetooth_enable
        awaitingReceipt -> R.string.bluetooth_stop_waiting
        cancelAvailable -> R.string.cancel
        receiving && state.canRetryListening -> R.string.bluetooth_retry_listening
        !receiving && state.canScan && !state.scanning -> R.string.bluetooth_rescan
        else -> null
    }
    val performPrimaryAction: () -> Unit = {
        when (primaryAction) {
            R.string.bluetooth_open_document -> {
                openDocumentId = viewModel.stateFlow.value.documentId
                if (openDocumentId != null) leaving = true
            }
            R.string.bluetooth_choose_device_again -> viewModel.chooseDeviceAgain()
            R.string.bluetooth_app_settings -> openSettings(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
            R.string.bluetooth_permission_allow -> {
                if (permissionPrompt == BluetoothPermissionPrompt.Rationale) permissionPrompt = BluetoothPermissionPrompt.ShowingRationale
                else viewModel.requestPermissions()
            }
            R.string.bluetooth_enable -> viewModel.requestEnable()
            R.string.bluetooth_retry_listening -> viewModel.retryListening()
            R.string.bluetooth_rescan -> viewModel.scan()
            R.string.cancel, R.string.bluetooth_stop_waiting -> {
                if (viewModel.stateFlow.value.confirmCancel) cancelRequest = BluetoothCancelRequest.Transfer
                else viewModel.cancel()
            }
        }
    }
    val actionLabel = primaryAction?.let { stringResource(it) }
    val actionEnabled = active && !leaving && !state.leaveBlocked &&
        (systemAction == null || systemAction == BluetoothSystemAction.Permissions && state.permissionRequired)
    SecondaryPageScaffold(
        title = stringResource(if (receiving) R.string.bluetooth_receive else R.string.bluetooth_share),
        onBack = { requestLeave() },
        backContentDescription = stringResource(R.string.back),
        modifier = modifier,
        backEnabled = active && !leaving && !state.leaveBlocked,
        primaryActionLabel = if (landscape) actionLabel else null,
        onPrimaryAction = performPrimaryAction,
        primaryActionEnabled = actionEnabled,
    ) {
        Column(Modifier.fillMaxSize()) {
            if (receiving && (state.phase == BluetoothTransferPhase.Ready || state.phase == BluetoothTransferPhase.Incoming)) {
                BluetoothWaitingContent(
                    state = state,
                    timerActive = active && !leaving,
                    requestingDiscovery = systemAction == BluetoothSystemAction.Discoverable || inFlightSystemAction == BluetoothSystemAction.Discoverable,
                    retryEnabled = active && !leaving && systemAction == null && inFlightSystemAction == null,
                    settingsError = settingsError,
                    onRetry = viewModel::requestDiscoverability,
                    onOpenBluetoothSettings = { openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (receiving) {
                        receiveContent(state, viewModel.bytesTransferredFlow)
                    } else {
                        sendContent(state, sendTitle, active && !leaving, viewModel.bytesTransferredFlow, viewModel::connect)
                    }
                    if (state.permissionRequired) item("permission_explanation") {
                        BluetoothSupportingText(stringResource(R.string.bluetooth_permission_explanation))
                    }
                    if (settingsError) item("settings_error") {
                        BluetoothStatusText(stringResource(R.string.bluetooth_settings_error), error = true)
                    }
                }
            }
            if (!landscape && actionLabel != null) {
                val actionModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                if (cancelAvailable) {
                    OutlinedButton(onClick = performPrimaryAction, modifier = actionModifier, enabled = actionEnabled) {
                        Text(actionLabel)
                    }
                } else {
                    Button(onClick = performPrimaryAction, modifier = actionModifier, enabled = actionEnabled) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }

    if (active && !leaving && state.phase == BluetoothTransferPhase.Incoming) {
        IncomingBluetoothDocumentDialog(state, onAccept = viewModel::accept, onReject = viewModel::reject)
    }
    val cancellation = cancelRequest
    if (active && cancellation != null && state.confirmCancel) {
        AlertDialog(
            onDismissRequest = { cancelRequest = null },
            title = { Text(stringResource(if (awaitingReceipt) R.string.bluetooth_stop_waiting_title else R.string.bluetooth_cancel_title)) },
            text = { Text(stringResource(if (awaitingReceipt) R.string.bluetooth_stop_waiting_body else R.string.bluetooth_cancel_body)) },
            dismissButton = {
                TextButton(onClick = { cancelRequest = null }) {
                    Text(stringResource(if (awaitingReceipt) R.string.bluetooth_keep_waiting else R.string.bluetooth_keep_transferring))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cancelRequest = null
                    if (cancellation == BluetoothCancelRequest.Leave) leaving = true
                    else viewModel.cancel()
                }) { Text(stringResource(if (awaitingReceipt) R.string.bluetooth_stop_waiting else R.string.bluetooth_cancel_transfer)) }
            },
        )
    }
    if (active && !leaving && permissionPrompt == BluetoothPermissionPrompt.ShowingRationale && state.permissionRequired) {
        AlertDialog(
            onDismissRequest = { permissionPrompt = BluetoothPermissionPrompt.Rationale },
            title = { Text(stringResource(R.string.bluetooth_permission_allow)) },
            text = { Text(stringResource(R.string.bluetooth_permission_explanation)) },
            dismissButton = {
                TextButton(onClick = { permissionPrompt = BluetoothPermissionPrompt.Rationale }) { Text(stringResource(R.string.cancel)) }
            },
            confirmButton = {
                Button(onClick = {
                    permissionPrompt = BluetoothPermissionPrompt.RationaleAccepted
                    viewModel.requestPermissions()
                }) { Text(stringResource(R.string.bluetooth_permission_allow)) }
            },
        )
    }
}

private fun LazyListScope.sendContent(
    state: BluetoothTransferState,
    title: String?,
    active: Boolean,
    bytesTransferredFlow: StateFlow<Long>,
    onDevice: (String) -> Unit,
) {
    item("send_archive") {
        BluetoothInfoCard(
            icon = R.drawable.ic_bluetooth_archive,
            title = state.documentTitle ?: title ?: stringResource(R.string.bluetooth_document),
            detail = state.fileName?.let { stringResource(R.string.bluetooth_file_detail, it, bluetoothFileSize(state.fileSize)) },
        )
    }
    bluetoothStatus(state)
    if ((state.phase == BluetoothTransferPhase.Ready || state.phase == BluetoothTransferPhase.Failed && state.canScan) &&
        !state.permissionRequired && !state.enableRequired
    ) {
        val duplicateNames = (state.pairedDevices + state.nearbyDevices).groupingBy { it.name }.eachCount()
        bluetoothDeviceGroup(R.string.bluetooth_paired_devices, state.pairedDevices, duplicateNames, active, onDevice)
        bluetoothDeviceGroup(R.string.bluetooth_nearby_devices, state.nearbyDevices, duplicateNames, active, onDevice)
        if (state.scanning) item("scan_progress") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                BluetoothSupportingText(stringResource(R.string.bluetooth_scanning))
            }
        } else if (state.nearbyDevices.isEmpty()) item("scan_empty") {
            BluetoothSupportingText(stringResource(R.string.bluetooth_no_nearby_devices))
        }
        if (state.scanning || state.nearbyDevices.isNotEmpty()) item("device_help") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider()
                BluetoothSupportingText(stringResource(R.string.bluetooth_pick_device_help))
            }
        }
    }
    when (state.phase) {
        BluetoothTransferPhase.Preparing -> item("preparing") {
            BluetoothBusyStatus(stringResource(R.string.bluetooth_preparing))
        }
        BluetoothTransferPhase.Pairing, BluetoothTransferPhase.Connecting, BluetoothTransferPhase.AwaitingApproval,
        BluetoothTransferPhase.Transferring, BluetoothTransferPhase.AwaitingImport -> {
            item("remote_device") {
                BluetoothInfoCard(
                    icon = R.drawable.ic_bluetooth_device,
                    title = state.remoteName ?: stringResource(R.string.bluetooth_unknown_device),
                    detail = null,
                    label = stringResource(R.string.bluetooth_receiving_device),
                    emphasized = false,
                )
            }
            item("send_progress") {
                when (state.phase) {
                    BluetoothTransferPhase.Pairing -> BluetoothBusyStatus(
                        stringResource(R.string.bluetooth_pairing),
                        detail = stringResource(R.string.bluetooth_pairing_help),
                    )
                    BluetoothTransferPhase.Connecting -> BluetoothBusyStatus(stringResource(R.string.bluetooth_connecting))
                    BluetoothTransferPhase.AwaitingApproval -> BluetoothBusyStatus(
                        stringResource(R.string.bluetooth_waiting_for_acceptance),
                        detail = stringResource(R.string.bluetooth_waiting_for_acceptance_help),
                    )
                    BluetoothTransferPhase.Transferring -> BluetoothProgressCard(state.fileSize, bytesTransferredFlow, stringResource(R.string.bluetooth_sending))
                    else -> BluetoothBusyStatus(stringResource(R.string.bluetooth_waiting_for_import))
                }
            }
        }
        BluetoothTransferPhase.Sent -> item("sent") {
            BluetoothSuccessHeading(stringResource(R.string.bluetooth_peer_imported))
        }
        else -> Unit
    }
}

private fun LazyListScope.receiveContent(
    state: BluetoothTransferState,
    bytesTransferredFlow: StateFlow<Long>,
) {
    when (state.phase) {
        BluetoothTransferPhase.Transferring, BluetoothTransferPhase.Importing -> {
            item("receive_archive") {
                BluetoothInfoCard(
                    icon = R.drawable.ic_bluetooth_archive,
                    title = state.fileName.orEmpty(),
                    detail = stringResource(R.string.bluetooth_from_device, state.remoteName ?: stringResource(R.string.bluetooth_unknown_device)),
                )
            }
            item("receive_progress") {
                if (state.phase == BluetoothTransferPhase.Importing) {
                    BluetoothBusyStatus(stringResource(R.string.bluetooth_importing))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BluetoothProgressCard(state.fileSize, bytesTransferredFlow, stringResource(R.string.bluetooth_receiving))
                        BluetoothSupportingText(stringResource(R.string.bluetooth_auto_import))
                    }
                }
            }
        }
        BluetoothTransferPhase.Imported -> {
            item("imported") { BluetoothSuccessHeading(stringResource(R.string.bluetooth_import_success)) }
            if (state.mirrorFailed) item("mirror_failed") { BluetoothStatusText(stringResource(R.string.mirror_failed), error = true) }
            item("imported_document") {
                BluetoothInfoCard(
                    icon = R.drawable.ic_bluetooth_archive,
                    title = state.documentTitle.orEmpty(),
                    detail = stringResource(R.string.bluetooth_editable_document),
                )
            }
        }
        else -> item("receiver") { BluetoothReceiverCard(state) }
    }
    bluetoothStatus(state)
}

private fun LazyListScope.bluetoothStatus(state: BluetoothTransferState) {
    if (state.statusRes != 0) item("session_status") {
        val error = state.phase == BluetoothTransferPhase.Failed || state.phase == BluetoothTransferPhase.Unconfirmed
        BluetoothStatusText(stringResource(state.statusRes), error)
    }
}

private fun LazyListScope.bluetoothDeviceGroup(
    @StringRes heading: Int,
    devices: List<BluetoothTransferDevice>,
    duplicateNames: Map<String?, Int>,
    active: Boolean,
    onSelected: (String) -> Unit,
) {
    item(key = heading) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(heading), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (devices.isEmpty()) {
                if (heading == R.string.bluetooth_paired_devices) BluetoothSupportingText(stringResource(R.string.bluetooth_no_paired_devices))
            } else {
                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column {
                        devices.forEachIndexed { index, device ->
                            if (index > 0) HorizontalDivider(Modifier.padding(start = 50.dp, end = 14.dp))
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable(enabled = active, role = Role.Button) { onSelected(device.address) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(painterResource(R.drawable.ic_bluetooth_device), contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    val name = device.name?.takeIf { it.isNotBlank() }
                                    Text(name ?: stringResource(R.string.bluetooth_unknown_device), style = MaterialTheme.typography.bodyLarge)
                                    if (name == null || (duplicateNames[name] ?: 0) > 1) BluetoothSupportingText(device.address)
                                }
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BluetoothWaitingContent(
    state: BluetoothTransferState,
    timerActive: Boolean,
    requestingDiscovery: Boolean,
    retryEnabled: Boolean,
    settingsError: Boolean,
    onRetry: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listening = state.canDiscover && !state.canRetryListening && !state.permissionRequired && !state.enableRequired
    val localName = state.localName
    // 父列的 weight 给出最小内容高度，窄屏或大字体时可滚动到底部引导。
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BluetoothReceiverCard(state)
            if (listening) {
                BluetoothDiscoverabilityCountdown(
                    discoverability = state.discoverability,
                    active = timerActive,
                    requesting = requestingDiscovery,
                    retryEnabled = retryEnabled,
                    showPairedHelp = state.statusRes != R.string.bluetooth_transfer_discoverability_denied,
                    onRetry = onRetry,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (localName.isNullOrBlank()) {
                    Column {
                        BluetoothSupportingText(stringResource(R.string.bluetooth_missing_name_help))
                        TextButton(onClick = onOpenBluetoothSettings) { Text(stringResource(R.string.bluetooth_system_settings)) }
                    }
                }
            } else if (state.phase == BluetoothTransferPhase.Ready && state.statusRes == 0 &&
                !state.permissionRequired && !state.enableRequired && !state.canRetryListening
            ) {
                BluetoothBusyStatus(stringResource(R.string.bluetooth_preparing_receiver))
            }
            if (state.statusRes != 0) {
                BluetoothStatusText(stringResource(state.statusRes), error = state.canRetryListening)
            }
            if (state.permissionRequired) BluetoothSupportingText(stringResource(R.string.bluetooth_permission_explanation))
            if (settingsError) BluetoothStatusText(stringResource(R.string.bluetooth_settings_error), error = true)
        }
        if (listening && !localName.isNullOrBlank()) {
            Text(
                stringResource(R.string.bluetooth_local_name_help, localName),
                Modifier.fillMaxWidth().padding(top = 24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun BluetoothDiscoverabilityCountdown(
    discoverability: BluetoothDiscoverability?,
    active: Boolean,
    requesting: Boolean,
    retryEnabled: Boolean,
    showPairedHelp: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingSeconds = discoverabilitySeconds(discoverability, active)
    val progress = if (discoverability != null) remainingSeconds.toFloat() / discoverability.durationSeconds else 0f
    val progressColor = if (remainingSeconds in 1..15) {
        LocalThemePalette.current.chapter.foreground
    } else MaterialTheme.colorScheme.primary
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val captionExpansion = with(LocalDensity.current) { 14.sp.toDp() } - 14.dp
            val preferredDiameter = if (captionExpansion > 0.dp) 148.dp + captionExpansion * 4 else 148.dp
            val diameter = if (maxWidth < preferredDiameter) maxWidth else preferredDiameter
            Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                val ringModifier = Modifier.matchParentSize().clearAndSetSemantics { }
                val trackColor = MaterialTheme.colorScheme.outlineVariant
                if (requesting) {
                    CircularProgressIndicator(
                        modifier = ringModifier,
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 5.dp,
                        trackColor = trackColor,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                } else {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = ringModifier,
                        color = progressColor,
                        strokeWidth = 5.dp,
                        trackColor = trackColor,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                    )
                }
                Column(
                    Modifier.padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when {
                        requesting -> Text(
                            stringResource(R.string.bluetooth_wait_discovery_confirmation),
                            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                        remainingSeconds > 0 -> {
                            Text(
                                stringResource(R.string.bluetooth_listening),
                                style = MaterialTheme.typography.titleSmall,
                                textAlign = TextAlign.Center,
                            )
                            val time = DateUtils.formatElapsedTime(remainingSeconds.toLong())
                            val timeDescription = stringResource(R.string.bluetooth_discoverable_time, time)
                            Text(
                                time,
                                Modifier.clearAndSetSemantics { contentDescription = timeDescription },
                                style = MaterialTheme.typography.headlineSmall,
                                color = progressColor,
                            )
                        }
                        else -> {
                            val retryDescription = stringResource(R.string.bluetooth_retry_discovery)
                            Button(
                                onClick = onRetry,
                                modifier = Modifier.semantics { onClick(label = retryDescription, action = null) },
                                enabled = retryEnabled,
                            ) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                }
            }
        }
        if (!requesting && remainingSeconds == 0 && showPairedHelp) {
            BluetoothSupportingText(stringResource(R.string.bluetooth_paired_can_connect))
        }
    }
}

@Composable
private fun discoverabilitySeconds(discoverability: BluetoothDiscoverability?, active: Boolean): Int {
    if (discoverability == null) return 0
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val remaining by key(discoverability) {
        produceState(discoverability.remainingSeconds(), lifecycle, active) {
            if (!active) return@produceState
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    val remainingMillis = discoverability.deadlineMillis - SystemClock.elapsedRealtime()
                    value = secondsFromRemainingMillis(remainingMillis)
                    if (remainingMillis <= 0L) break
                    delay(((remainingMillis - 1L) % 1000L + 1L).milliseconds)
                }
            }
        }
    }
    return remaining
}

private fun BluetoothDiscoverability.remainingSeconds(): Int =
    secondsFromRemainingMillis(deadlineMillis - SystemClock.elapsedRealtime())

private fun secondsFromRemainingMillis(remainingMillis: Long): Int =
    if (remainingMillis > 0L) ((remainingMillis + 999L) / 1000L).toInt() else 0

@Composable
private fun BluetoothReceiverCard(
    state: BluetoothTransferState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BluetoothSupportingText(stringResource(R.string.bluetooth_local_name))
                Text(
                    state.localName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.bluetooth_name_unavailable),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            BluetoothIconTile(R.drawable.ic_bluetooth)
        }
    }
}

@Composable
private fun IncomingBluetoothDocumentDialog(
    state: BluetoothTransferState,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onReject,
        modifier = modifier,
        title = { Text(stringResource(R.string.bluetooth_receive_prompt)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.bluetooth_from_device, state.remoteName ?: stringResource(R.string.bluetooth_unknown_device)))
                BluetoothInfoCard(
                    icon = R.drawable.ic_bluetooth_archive,
                    title = state.fileName.orEmpty(),
                    detail = bluetoothFileSize(state.fileSize),
                )
            }
        },
        dismissButton = { TextButton(onClick = onReject) { Text(stringResource(R.string.bluetooth_decline)) } },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.bluetooth_receive_and_import)) } },
    )
}

@Composable
private fun BluetoothInfoCard(
    @DrawableRes icon: Int,
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
    label: String? = null,
    emphasized: Boolean = true,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (emphasized) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BluetoothIconTile(icon)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                label?.let { BluetoothSupportingText(it) }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                detail?.let { BluetoothSupportingText(it) }
            }
        }
    }
}

@Composable
private fun BluetoothIconTile(@DrawableRes icon: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun BluetoothBusyStatus(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 20.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        detail?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BluetoothProgressCard(
    fileSize: Long,
    bytesTransferredFlow: StateFlow<Long>,
    title: String,
    modifier: Modifier = Modifier,
) {
    val bytesTransferred by bytesTransferredFlow.collectAsStateWithLifecycle()
    val progress = if (fileSize > 0) (bytesTransferred.toDouble() / fileSize).toFloat() else 0f
    val locale = LocalConfiguration.current.locales[0]
    val percentFormat = remember(locale) { NumberFormat.getPercentInstance(locale) }
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(percentFormat.format(progress), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            BluetoothSupportingText(
                stringResource(R.string.bluetooth_byte_progress, bluetoothFileSize(bytesTransferred), bluetoothFileSize(fileSize)),
            )
        }
    }
}

@Composable
private fun BluetoothSuccessHeading(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Check, contentDescription = null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BluetoothStatusText(text: String, error: Boolean, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BluetoothSupportingText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun bluetoothFileSize(bytes: Long): String = Formatter.formatFileSize(LocalContext.current, bytes)
