package com.allan.picnote.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.allan.picnote.PicNoteApplication
import com.allan.picnote.R
import com.allan.picnote.data.BluetoothArchiveDirectory
import com.allan.picnote.data.BluetoothArchiveException
import com.allan.picnote.data.bluetoothStorageReason
import com.allan.picnote.data.deleteBluetoothArchiveFiles
import com.allan.picnote.logd
import com.allan.picnote.logEx
import com.au.module_android.init.GlobalBackgroundCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

enum class BluetoothTransferPhase {
    Preparing, Ready, Pairing, Connecting, AwaitingApproval, Incoming, Transferring, AwaitingImport, Importing,
    Imported, Sent, Failed, Unconfirmed,
}

enum class BluetoothSystemAction { Permissions, Enable, Discoverable, Settings }

data class BluetoothTransferDevice(val address: String, val name: String?)

data class BluetoothDiscoverability(val deadlineMillis: Long, val durationSeconds: Int)

data class BluetoothTransferState(
    val phase: BluetoothTransferPhase,
    @StringRes val statusRes: Int = 0,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val bodyWritten: Boolean = false,
    val localName: String? = null,
    val remoteName: String? = null,
    val pairedDevices: List<BluetoothTransferDevice> = emptyList(),
    val nearbyDevices: List<BluetoothTransferDevice> = emptyList(),
    val discoverability: BluetoothDiscoverability? = null,
    val scanning: Boolean = false,
    val documentId: String? = null,
    val documentTitle: String? = null,
    val permissionRequired: Boolean = false,
    val enableRequired: Boolean = false,
    val canDiscover: Boolean = false,
    val canRetryListening: Boolean = false,
    val canScan: Boolean = false,
    val leaveBlocked: Boolean = false,
    val mirrorFailed: Boolean = false,
) {
    val awaitingReceipt: Boolean
        get() = phase == BluetoothTransferPhase.AwaitingImport || (phase == BluetoothTransferPhase.Transferring && bodyWritten)
    val confirmCancel: Boolean
        get() = phase == BluetoothTransferPhase.Transferring || phase == BluetoothTransferPhase.AwaitingImport
}

private const val ServiceName = "PicNote"
private const val ScanTimeoutMillis = 30_000L
private const val SystemActionTimeoutMillis = 120_000L
private const val ReturnToForegroundMillis = 1_500L
private const val PairingTimeoutMillis = 60_000L
private const val PairingReturnGraceMillis = 20_000L
private const val PairingBroadcastGraceMillis = 2_000L
private const val BondStatePollMillis = 500L

private data class BondAttempt(val address: String, val changes: Channel<Unit>)
private data class IncomingPairing(val address: String, val deadlineMillis: Long, val inProgress: Boolean)

@SuppressLint("MissingPermission")
/** 持有导航页面的一次蓝牙会话，在页面重建时保留任务和系统弹窗结果。 */
class BluetoothTransferViewModel(
    private val app: PicNoteApplication,
    private val sourceDocumentId: String?,
) : ViewModel() {
    private val requiredPermissions = if (sourceDocumentId == null) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE)
    } else {
        listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    }

    private val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val supported = adapter != null && app.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
    private val mutableStateFlow = MutableStateFlow(
        BluetoothTransferState(
            phase = if (sourceDocumentId == null) BluetoothTransferPhase.Ready else BluetoothTransferPhase.Preparing,
            documentId = sourceDocumentId,
        ),
    )
    val stateFlow = mutableStateFlow.asStateFlow()
    private val mutableBytesTransferredFlow = MutableStateFlow(0L)
    val bytesTransferredFlow = mutableBytesTransferredFlow.asStateFlow()
    private val mutableActionFlow = MutableStateFlow<BluetoothSystemAction?>(null)
    val actionFlow = mutableActionFlow.asStateFlow()
    private val mutableSystemActionInFlightFlow = MutableStateFlow<BluetoothSystemAction?>(null)
    val systemActionInFlightFlow = mutableSystemActionInFlightFlow.asStateFlow()
    private val foregroundFlow = MutableStateFlow(GlobalBackgroundCallback.isForeground)

    private val requestedActions = mutableSetOf<BluetoothSystemAction>()
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var bondAttempt: BondAttempt? = null
    private var incomingPairing: IncomingPairing? = null
    private var receivingPeerAddress: String? = null
    private var backgroundSinceMillis: Long? = null

    private var packageJob: Job? = null
    private var senderScope: CoroutineScope? = null
    private var archive: Pair<File, BluetoothArchiveOffer>? = null
    private var scanJob: Job? = null
    private var listenJob: Job? = null
    private var transferJob: Job? = null
    private var consent: CompletableDeferred<Boolean>? = null
    private var refreshJob: Job? = null
    private var retrySendJob: Job? = null
    private var stopJob: Job? = null
    private var backgroundJob: Job? = null
    private var autoScanPending = true
    private var leaving = false

    private val backgroundListener = object : GlobalBackgroundCallback.IBackgroundListener {
        override fun onBackground(isBackground: Boolean) {
            backgroundSinceMillis = if (isBackground) SystemClock.elapsedRealtime() else null
            foregroundFlow.value = !isBackground
            backgroundJob?.cancel()
            if (isBackground) {
                val remainingPairingGrace = remainingIncomingPairingGrace()
                val grace = when {
                    systemActionInFlightFlow.value != null -> SystemActionTimeoutMillis
                    stateFlow.value.phase == BluetoothTransferPhase.Pairing -> PairingTimeoutMillis + PairingReturnGraceMillis
                    remainingPairingGrace > 0 -> remainingPairingGrace
                    isWaitingForIncomingConnection() -> PairingBroadcastGraceMillis
                    else -> 0L
                }
                endInBackground(grace)
            } else refresh()
        }
    }

    init {
        GlobalBackgroundCallback.addListener(backgroundListener)
        refresh()
    }

    fun beginSystemAction(action: BluetoothSystemAction): Boolean {
        if (leaving || !foregroundFlow.value || mutableActionFlow.value != action || systemActionInFlightFlow.value != null) {
            return false
        }
        if (action == BluetoothSystemAction.Discoverable && !stateFlow.value.canDiscover) {
            mutableActionFlow.value = null
            return false
        }
        requestedActions += action
        mutableSystemActionInFlightFlow.value = action
        mutableActionFlow.value = null
        return true
    }

    fun requestPermissions() {
        if (stateFlow.value.permissionRequired) queueAction(BluetoothSystemAction.Permissions)
    }

    fun requestEnable() {
        if (stateFlow.value.enableRequired) queueAction(BluetoothSystemAction.Enable)
    }

    fun requestDiscoverability() {
        if (sourceDocumentId != null || !foregroundFlow.value || leaving || stateFlow.value.phase != BluetoothTransferPhase.Ready) return
        if (stateFlow.value.canDiscover) queueAction(BluetoothSystemAction.Discoverable)
    }

    fun beginSettingsVisit(): Boolean {
        if (leaving || !foregroundFlow.value || systemActionInFlightFlow.value != null) return false
        refreshJob?.cancel()
        mutableActionFlow.value = null
        mutableSystemActionInFlightFlow.value = BluetoothSystemAction.Settings
        return true
    }

    fun onSettingsResult() {
        if (completeAction(BluetoothSystemAction.Settings)) refresh()
    }

    fun onResume() {
        completeAction(BluetoothSystemAction.Settings)
        refresh()
    }

    fun retryListening() {
        if (!stateFlow.value.canRetryListening || !foregroundFlow.value || leaving) return
        val failedListener = listenJob
        mutableStateFlow.update { it.copy(canRetryListening = false, statusRes = 0) }
        viewModelScope.launch {
            failedListener?.cancelAndJoin()
            refresh()
        }
    }

    fun onPermissionsResult() {
        if (completeAction(BluetoothSystemAction.Permissions)) refresh()
    }

    fun onEnableResult() {
        if (completeAction(BluetoothSystemAction.Enable)) refresh()
    }

    fun onDiscoverabilityResult(resultCode: Int) {
        if (!completeAction(BluetoothSystemAction.Discoverable)) return
        val state = stateFlow.value
        if (state.isTerminal() || state.permissionRequired || state.enableRequired || !foregroundFlow.value && stopJob?.isActive == true) return
        if (resultCode <= 0) {
            mutableStateFlow.update {
                val status = if (it.canRetryListening) it.statusRes else R.string.bluetooth_transfer_discoverability_denied
                it.copy(discoverability = null, statusRes = status)
            }
            return
        }
        val deadline = SystemClock.elapsedRealtime() + resultCode.toLong() * 1000L
        mutableStateFlow.update {
            val status = when (it.statusRes) {
                R.string.bluetooth_transfer_discoverability_denied, R.string.bluetooth_transfer_system_action_failed -> 0
                else -> it.statusRes
            }
            it.copy(discoverability = BluetoothDiscoverability(deadline, resultCode), statusRes = status)
        }
    }

    fun onSystemActionFailed(action: BluetoothSystemAction) {
        if (completeAction(action)) showStatus(R.string.bluetooth_transfer_system_action_failed)
    }

    private fun completeAction(action: BluetoothSystemAction): Boolean {
        if (systemActionInFlightFlow.value != action) return false
        mutableSystemActionInFlightFlow.value = null
        if (!foregroundFlow.value) endInBackground(max(ReturnToForegroundMillis, remainingIncomingPairingGrace()))
        return !leaving
    }

    private fun queueAction(action: BluetoothSystemAction) {
        if (foregroundFlow.value && !leaving && systemActionInFlightFlow.value == null && mutableActionFlow.value == null) {
            mutableActionFlow.value = action
        }
    }

    private fun endInBackground(graceMillis: Long) {
        backgroundJob?.cancel()
        backgroundJob = viewModelScope.launch {
            if (graceMillis > 0L) delay(graceMillis.milliseconds)
            stopJob?.join()
            if (!foregroundFlow.value) {
                val action = systemActionInFlightFlow.value
                val state = stateFlow.value
                val hasActiveWork = scanJob?.isActive == true || transferJob?.isActive == true || listenJob?.isActive == true ||
                    retrySendJob?.isActive == true ||
                    (state.phase == BluetoothTransferPhase.Preparing && packageJob?.isActive == true)
                if (hasActiveWork || action != null) {
                    val reason = if (action == BluetoothSystemAction.Settings || !hasActiveWork) 0 else R.string.bluetooth_transfer_background
                    stopBluetooth(reason, discardArchive = true)
                }
            }
        }
    }

    private fun canRefreshBluetooth(): Boolean = !leaving && foregroundFlow.value && stopJob?.isActive != true &&
        !stateFlow.value.isTerminal() && systemActionInFlightFlow.value != BluetoothSystemAction.Settings

    private fun refresh() {
        if (!canRefreshBluetooth() || retrySendJob?.isActive == true) return
        refreshJob?.cancel()
        if (!supported) {
            setPhase(BluetoothTransferPhase.Failed, R.string.bluetooth_transfer_unsupported)
            return
        }
        if (sourceDocumentId != null && archive == null) {
            prepareArchive(sourceDocumentId)
            return
        }
        if (missingPermissions().isNotEmpty()) {
            if (bluetoothReceiver != null) stopBluetooth(R.string.bluetooth_transfer_permission_required, discardArchive = false)
            mutableStateFlow.update {
                it.copy(
                    statusRes = R.string.bluetooth_transfer_permission_required,
                    permissionRequired = true, enableRequired = false, canDiscover = false, canScan = false,
                    discoverability = null,
                )
            }
            requestAutomatically(BluetoothSystemAction.Permissions)
            return
        }
        val localAdapter = checkNotNull(adapter)
        refreshJob = viewModelScope.launch {
            try {
                registerBluetoothReceiver()
                val (enabled, name) = withContext(Dispatchers.IO) {
                    val enabled = localAdapter.isEnabled
                    enabled to if (enabled && sourceDocumentId == null) localAdapter.name?.ifBlank { null } else null
                }
                if (!canRefreshBluetooth()) return@launch
                if (!enabled) {
                    mutableStateFlow.update {
                        it.copy(
                            statusRes = R.string.bluetooth_transfer_enable_required,
                            permissionRequired = false, enableRequired = true, canDiscover = false, canScan = false,
                            discoverability = null,
                        )
                    }
                    requestAutomatically(BluetoothSystemAction.Enable)
                    return@launch
                }
                mutableStateFlow.update {
                    val status = when (it.statusRes) {
                        R.string.bluetooth_transfer_permission_required, R.string.bluetooth_transfer_enable_required -> 0
                        else -> it.statusRes
                    }
                    it.copy(
                        statusRes = status,
                        localName = name,
                        permissionRequired = false, enableRequired = false,
                        canScan = sourceDocumentId != null && it.phase == BluetoothTransferPhase.Ready,
                    )
                }
                if (sourceDocumentId == null) {
                    if (!stateFlow.value.canRetryListening) startListening(localAdapter)
                } else if (autoScanPending && transferJob?.isCompleted != false) {
                    autoScanPending = false
                    scan()
                }
            } catch (_: SecurityException) {
                handlePermissionLoss()
            }
        }
    }

    private fun requestAutomatically(action: BluetoothSystemAction) {
        if (action !in requestedActions) queueAction(action)
    }

    fun missingPermissions(): List<String> = requiredPermissions.filter {
        ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED
    }

    private fun prepareArchive(documentId: String) {
        if (packageJob?.isCompleted == false) return
        packageJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            var file: File? = null
            try {
                val title = app.database.dao().document(documentId)?.title
                mutableStateFlow.update {
                    it.copy(phase = BluetoothTransferPhase.Preparing, documentTitle = title)
                }
                app.awaitStartupCleanup()
                val digest = MessageDigest.getInstance("SHA-256")
                val prepared = app.documents.prepareBluetoothArchive(documentId, digest)
                file = prepared
                val offer = withContext(Dispatchers.IO) {
                    BluetoothArchiveOffer(prepared.name, prepared.length(), digest.digest())
                }
                coroutineScope {
                    senderScope = this
                    archive = prepared to offer
                    mutableStateFlow.update {
                        it.copy(phase = BluetoothTransferPhase.Ready, fileName = offer.name, fileSize = offer.size)
                    }
                    refresh()
                    awaitCancellation()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Prepare Bluetooth archive failed")
                val reason = (e as? BluetoothArchiveException)?.messageResId ?: bluetoothStorageReason(e)
                mutableStateFlow.update { it.copy(phase = BluetoothTransferPhase.Failed, statusRes = reason) }
            } finally {
                senderScope = null
                archive = null
                deleteDirectory(file?.parentFile)
            }
        }
        packageJob?.start()
    }

    fun scan() {
        val localAdapter = adapter ?: return
        if (!foregroundFlow.value || !stateFlow.value.canScan || leaving ||
            transferJob?.isCompleted == false || scanJob?.isCompleted == false
        ) return
        scanJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            doScan(localAdapter)
        }
        scanJob?.start()
    }

    fun chooseDeviceAgain() {
        val documentId = sourceDocumentId ?: return
        if (stateFlow.value.phase != BluetoothTransferPhase.Unconfirmed ||
            !foregroundFlow.value || leaving || stopJob?.isActive == true || retrySendJob?.isActive == true
        ) return
        mutableStateFlow.update {
            it.copy(phase = BluetoothTransferPhase.Preparing, statusRes = 0, bodyWritten = false,
                fileName = null, fileSize = 0, pairedDevices = emptyList(), nearbyDevices = emptyList())
        }
        mutableBytesTransferredFlow.value = 0L
        val previousScan = scanJob
        retrySendJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            previousScan?.cancelAndJoin()
            packageJob?.cancelAndJoin()
            retrySendJob = null
            if (!leaving && foregroundFlow.value && stopJob?.isActive != true) {
                autoScanPending = true
                prepareArchive(documentId)
            }
        }
        retrySendJob?.start()
    }

    private suspend fun doScan(localAdapter: BluetoothAdapter) {
        val scanContext = currentCoroutineContext()
        val callbackJob = Job(scanContext[Job])
        val callbacks = CoroutineScope(scanContext + callbackJob)
        var receiver: BroadcastReceiver? = null
        var started = false
        try {
            val devices = withContext(Dispatchers.IO) {
                localAdapter.bondedDevices.orEmpty().map { it.toTransferDevice() }.sortedBy { it.name.orEmpty() }
            }
            val pairedAddresses = devices.map { it.address }.toSet()
            mutableStateFlow.update {
                it.copy(pairedDevices = devices, nearbyDevices = emptyList(), scanning = true, statusRes = 0)
            }
            stopDiscovery(localAdapter)
            val finished = CompletableDeferred<Unit>()
            var discoveryStarted = false
            val callback = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (receiver !== this || leaving || !foregroundFlow.value) return
                    val callback = this
                    try {
                        when (intent.action) {
                            BluetoothAdapter.ACTION_DISCOVERY_STARTED -> discoveryStarted = true
                            BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (discoveryStarted) {
                                callbacks.launch {
                                    try {
                                        if (!withContext(Dispatchers.IO) { localAdapter.isDiscovering }) finished.complete(Unit)
                                    } catch (e: SecurityException) {
                                        finished.completeExceptionally(e)
                                    }
                                }
                            }
                            BluetoothDevice.ACTION_FOUND -> {
                                val device = intent.bluetoothDevice() ?: return
                                if (device.address in pairedAddresses) return
                                callbacks.launch {
                                    try {
                                        val item = withContext(Dispatchers.IO) {
                                            if (device.type == BluetoothDevice.DEVICE_TYPE_LE) null else device.toTransferDevice()
                                        } ?: return@launch
                                        if (receiver !== callback || leaving || !foregroundFlow.value || !stateFlow.value.canScan) return@launch
                                        mutableStateFlow.update { state ->
                                            val nearby = state.nearbyDevices
                                            val matching = nearby.indexOfFirst { it.address == item.address }
                                            val updated = when {
                                                matching < 0 -> nearby + item
                                                item.name == null -> nearby
                                                else -> nearby.toMutableList().apply { set(matching, item) }
                                            }
                                            state.copy(nearbyDevices = updated)
                                        }
                                    } catch (e: SecurityException) {
                                        finished.completeExceptionally(e)
                                    }
                                }
                            }
                        }
                    } catch (e: SecurityException) {
                        finished.completeExceptionally(e)
                    }
                }
            }
            ContextCompat.registerReceiver(
                app,
                callback,
                IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothDevice.ACTION_FOUND)
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
            receiver = callback
            withContext(Dispatchers.IO) { started = localAdapter.startDiscovery() }
            if (!started) throw BluetoothTransferException(R.string.bluetooth_transfer_scan_failed)
            discoveryStarted = discoveryStarted || withContext(Dispatchers.IO) { localAdapter.isDiscovering }
            if (withTimeoutOrNull(ScanTimeoutMillis.milliseconds) { finished.await() } == null) {
                showStatus(R.string.bluetooth_transfer_scan_timeout)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            handlePermissionLoss()
        } catch (e: Exception) {
            logEx(throwable = e, message = "Scan Bluetooth devices failed")
            showStatus(R.string.bluetooth_transfer_scan_failed)
        } finally {
            val registeredReceiver = receiver
            receiver = null
            try {
                registeredReceiver?.let(app::unregisterReceiver)
            } finally {
                withContext(NonCancellable) {
                    callbackJob.cancelAndJoin()
                    if (started) {
                        try {
                            withContext(Dispatchers.IO) { localAdapter.cancelDiscovery() }
                        } catch (_: SecurityException) {
                            // 权限已撤销时由系统结束扫描。
                        }
                    }
                }
                mutableStateFlow.update { it.copy(scanning = false) }
            }
        }
    }

    private suspend fun stopDiscovery(localAdapter: BluetoothAdapter) = withContext(Dispatchers.IO) {
        localAdapter.cancelDiscovery()
        val stopped = withTimeoutOrNull(2_000L.milliseconds) {
            while (localAdapter.isDiscovering) delay(50L.milliseconds)
            true
        }
        if (stopped != true) throw BluetoothTransferException(R.string.bluetooth_transfer_scan_failed)
    }

    fun connect(address: String) {
        val localAdapter = adapter ?: return
        val scope = senderScope ?: return
        val (file, offer) = archive ?: return
        val state = stateFlow.value
        val packageAtConnect = packageJob
        if (!state.canScan || !foregroundFlow.value || leaving || transferJob?.isActive == true) return
        val selected = (state.pairedDevices + state.nearbyDevices).firstOrNull { it.address == address } ?: return
        mutableBytesTransferredFlow.value = 0L
        mutableStateFlow.update {
            it.copy(
                phase = BluetoothTransferPhase.Connecting, remoteName = selected.name, canScan = false,
                bodyWritten = false, statusRes = 0,
            )
        }
        transferJob = scope.launch(start = CoroutineStart.LAZY) {
            var socket: BluetoothSocket? = null
            try {
                scanJob?.cancelAndJoin()
                stopDiscovery(localAdapter)
                val device = localAdapter.getRemoteDevice(address)
                val firstPairing = awaitPairing(device, address)
                if (!foregroundFlow.value || leaving) throw BluetoothTransferException(R.string.bluetooth_transfer_background)
                if (firstPairing) {
                    mutableStateFlow.update {
                        it.copy(
                            pairedDevices = (it.pairedDevices + selected).distinctBy { device -> device.address }
                                .sortedBy { device -> device.name.orEmpty() },
                            nearbyDevices = it.nearbyDevices.filter { device -> device.address != address },
                        )
                    }
                }
                setPhase(BluetoothTransferPhase.Connecting)
                val connected = withContext(Dispatchers.IO) {
                    if (device.bondState != BluetoothDevice.BOND_BONDED) {
                        throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_cancelled)
                    }
                    currentCoroutineContext().ensureActive()
                    if (!foregroundFlow.value) throw BluetoothTransferException(R.string.bluetooth_transfer_background)
                    device.createRfcommSocketToServiceRecord(PicNoteBluetoothServiceUuid).also { socket = it }
                }
                runBluetoothSocketIo(connected, BluetoothConnectTimeoutMillis, R.string.bluetooth_transfer_connect_timeout) {
                    connected.connect()
                }
                waitForForeground(if (firstPairing) PairingReturnGraceMillis else ReturnToForegroundMillis)
                val channel = withContext(Dispatchers.IO) { BluetoothTransferChannel(connected) }
                channel.negotiateSend(offer)
                setPhase(BluetoothTransferPhase.AwaitingApproval)
                channel.awaitApproval(if (firstPairing) PairingReturnGraceMillis else 0L)
                setPhase(BluetoothTransferPhase.Transferring)
                channel.sendFileAndAwaitImport(
                    file, offer.size,
                    onBodyWritten = {
                        withContext(NonCancellable + Dispatchers.Main.immediate) {
                            mutableStateFlow.update { it.copy(bodyWritten = true) }
                        }
                    },
                    onAwaitingImport = { setPhase(BluetoothTransferPhase.AwaitingImport) },
                    onProgress = ::setProgress,
                )
                setPhase(BluetoothTransferPhase.Sent)
                logd("Bluetooth archive import confirmed")
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                if (stateFlow.value.awaitingReceipt) {
                    setPhase(BluetoothTransferPhase.Unconfirmed, R.string.bluetooth_transfer_unconfirmed)
                } else handlePermissionLoss()
            } catch (e: BluetoothPeerException) {
                resetSender(e.failure.reason)
            } catch (e: Exception) {
                logEx(throwable = e, message = "Send Bluetooth archive failed")
                failSend(e)
            } finally {
                closeBluetoothSocket(socket)
            }
        }
        transferJob?.invokeOnCompletion {
            if (stateFlow.value.isTerminal()) packageAtConnect?.cancel()
        }
        transferJob?.start()
    }

    private suspend fun awaitPairing(device: BluetoothDevice, address: String): Boolean {
        val initial = withContext(Dispatchers.IO) { device.bondState }
        if (initial == BluetoothDevice.BOND_BONDED) return false
        if (!foregroundFlow.value || leaving) throw BluetoothTransferException(R.string.bluetooth_transfer_background)
        setPhase(BluetoothTransferPhase.Pairing)
        val changes = Channel<Unit>(Channel.CONFLATED)
        bondAttempt = BondAttempt(address, changes)
        try {
            val bonded = withTimeoutOrNull(PairingTimeoutMillis.milliseconds) {
                val firstState = withContext(Dispatchers.IO) {
                    when (val state = device.bondState) {
                        BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_BONDING -> state
                        BluetoothDevice.BOND_NONE -> {
                            if (initial == BluetoothDevice.BOND_BONDING) {
                                throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_cancelled)
                            }
                            currentCoroutineContext().ensureActive()
                            if (!foregroundFlow.value) throw BluetoothTransferException(R.string.bluetooth_transfer_background)
                            val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
                                device.createBond(BluetoothDevice.TRANSPORT_BREDR)
                            } else {
                                device.createBond()
                            }
                            device.bondState.also {
                                if (!started && it != BluetoothDevice.BOND_BONDED && it != BluetoothDevice.BOND_BONDING) {
                                    throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_start_failed)
                                }
                            }
                        }
                        else -> throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_start_failed)
                    }
                }
                var state = firstState
                var sawBonding = initial == BluetoothDevice.BOND_BONDING || state == BluetoothDevice.BOND_BONDING
                while (state != BluetoothDevice.BOND_BONDED) {
                    // 广播只用于提前唤醒检查，状态可能来自已经结束的配对。
                    withTimeoutOrNull(BondStatePollMillis.milliseconds) { changes.receive() }
                    state = withContext(Dispatchers.IO) { device.bondState }
                    if (state == BluetoothDevice.BOND_NONE && sawBonding) {
                        throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_cancelled)
                    }
                    sawBonding = sawBonding || state == BluetoothDevice.BOND_BONDING
                }
                true
            }
            if (bonded != true) throw BluetoothTransferException(R.string.bluetooth_transfer_pairing_timeout)
            waitForForeground(PairingReturnGraceMillis)
            return true
        } finally {
            bondAttempt = null
            changes.close()
            if (!foregroundFlow.value && !leaving) endInBackground(PairingReturnGraceMillis)
        }
    }

    private fun failSend(error: Exception) {
        val state = stateFlow.value
        if (state.awaitingReceipt) {
            setPhase(BluetoothTransferPhase.Unconfirmed, R.string.bluetooth_transfer_unconfirmed)
            return
        }
        val reason = when {
            error is BluetoothTransferException -> error.reason
            state.phase == BluetoothTransferPhase.Pairing ->
                R.string.bluetooth_transfer_pairing_cancelled
            state.phase == BluetoothTransferPhase.Connecting -> R.string.bluetooth_transfer_receiver_unavailable
            else -> R.string.bluetooth_transfer_disconnected
        }
        resetSender(reason)
    }

    private fun resetSender(@StringRes reason: Int) {
        mutableBytesTransferredFlow.value = 0L
        mutableStateFlow.update {
            it.copy(
                phase = BluetoothTransferPhase.Ready, bodyWritten = false, remoteName = null,
                canScan = missingPermissions().isEmpty(), statusRes = reason,
            )
        }
    }

    private suspend fun waitForForeground(timeoutMillis: Long = ReturnToForegroundMillis) {
        val resumed = withTimeoutOrNull(timeoutMillis.milliseconds) { foregroundFlow.first { it } }
        if (resumed == null || !foregroundFlow.value || leaving) {
            throw BluetoothTransferException(R.string.bluetooth_transfer_background)
        }
    }

    private fun startListening(localAdapter: BluetoothAdapter) {
        if (stateFlow.value.phase != BluetoothTransferPhase.Ready || listenJob?.isCompleted == false || transferJob?.isCompleted == false) return
        listenJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            while (!leaving && (foregroundFlow.value || remainingIncomingPairingGrace() > 0)) {
                if (listenOnce(localAdapter)) break
            }
        }
        listenJob?.start()
    }

    private suspend fun listenOnce(localAdapter: BluetoothAdapter): Boolean {
        var socket: BluetoothSocket? = null
        var server: BluetoothServerSocket? = null
        try {
            val listening = withContext(Dispatchers.IO) {
                localAdapter.listenUsingRfcommWithServiceRecord(ServiceName, PicNoteBluetoothServiceUuid)
                    .also { server = it }
            }
            mutableStateFlow.update { it.copy(canDiscover = true) }
            requestAutomatically(BluetoothSystemAction.Discoverable)
            val incoming = runBluetoothSocketIo(listening) { listening.accept().also { socket = it } }
            mutableStateFlow.update { it.copy(canDiscover = false) }
            closeBluetoothSocket(listening)
            server = null
            receivingPeerAddress = withContext(Dispatchers.IO) { incoming.remoteDevice.address }
            val imported = coroutineScope {
                val receiver = async(start = CoroutineStart.LAZY) { receive(incoming) }
                transferJob = receiver
                receiver.start()
                try {
                    receiver.await()
                } catch (e: CancellationException) {
                    // 手动取消只结束当前连接，页面仍可继续接收。
                    currentCoroutineContext().ensureActive()
                    resetReceiver(R.string.bluetooth_transfer_cancelled)
                    false
                } finally {
                    transferJob = null
                }
            }
            return imported
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEx(throwable = e, message = "Listen for Bluetooth connection failed")
            when (e) {
                is SecurityException -> handlePermissionLoss()
                else -> {
                    val reason = (e as? BluetoothTransferException)?.reason ?: R.string.bluetooth_transfer_listen_failed
                    mutableStateFlow.update { it.copy(statusRes = reason, canRetryListening = true) }
                }
            }
            return true
        } finally {
            closeBluetoothSocket(server)
            closeBluetoothSocket(socket)
            receivingPeerAddress = null
            mutableStateFlow.update { it.copy(canDiscover = false) }
        }
    }

    private suspend fun receive(socket: BluetoothSocket): Boolean {
        var directory: File? = null
        var channel: BluetoothTransferChannel? = null
        try {
            val (peer, peerName) = withContext(Dispatchers.IO) {
                BluetoothTransferChannel(socket) to socket.remoteDevice.name?.ifBlank { null }
            }
            channel = peer
            val offer = peer.negotiateReceive()
            // 先响应协议握手，再等待系统配对界面返回，文件确认只在应用前台显示。
            val pairingGrace = min(PairingReturnGraceMillis, remainingIncomingPairingGrace())
            waitForForeground(max(ReturnToForegroundMillis, pairingGrace))
            incomingPairing = null
            val answer = CompletableDeferred<Boolean>()
            consent = answer
            mutableBytesTransferredFlow.value = 0L
            mutableStateFlow.update {
                it.copy(
                    phase = BluetoothTransferPhase.Incoming, remoteName = peerName,
                    fileName = offer.name, fileSize = offer.size,
                    canDiscover = false, statusRes = 0,
                )
            }
            val zip = supervisorScope {
                // 等待本地确认时就开始读下一帧，发送端断开可立即撤销旧确认。
                val bodyStart = async { peer.awaitBodyStart() }
                try {
                    val accepted = withTimeoutOrNull(BluetoothApprovalTimeoutMillis.milliseconds) {
                        select<Boolean> {
                            bodyStart.onAwait { throw BluetoothTransferException(R.string.bluetooth_transfer_invalid_control) }
                            answer.onAwait { it }
                        }
                    }
                    consent = null
                    if (accepted != true) {
                        val failure = if (accepted == null) BluetoothReceiptFailure.Timeout else BluetoothReceiptFailure.Rejected
                        resetReceiver(if (accepted == null) failure.reason else 0)
                        replyFailure(peer, failure) {
                            withTimeoutOrNull(BluetoothFailureReceiptTimeoutMillis.milliseconds) { bodyStart.join() }
                        }
                        return@supervisorScope null
                    }
                    setPhase(BluetoothTransferPhase.Transferring)
                    val staged = try {
                        stageReceiveDirectory(offer.size) { directory = it }
                    } catch (e: BluetoothTransferException) {
                        val failure = e.receiptFailure(e.reason) ?: throw e
                        replyFailure(peer, failure) {
                            withTimeoutOrNull(BluetoothFailureReceiptTimeoutMillis.milliseconds) { bodyStart.join() }
                        }
                        resetReceiver(e.reason)
                        return@supervisorScope null
                    }
                    peer.sendApproval()
                    bodyStart.await()
                    File(staged, "archive.zip")
                } finally {
                    bodyStart.cancel()
                }
            } ?: return false
            peer.receiveFile(zip, offer, ::setProgress)
            setPhase(BluetoothTransferPhase.Importing)
            val imported = app.documents.importBluetoothArchive(zip) {
                withContext(Dispatchers.Main.immediate) {
                    mutableStateFlow.update { it.copy(leaveBlocked = true) }
                }
            }
            val title = importedTitle(imported.value)
            mutableStateFlow.update {
                it.copy(
                    phase = BluetoothTransferPhase.Imported, documentId = imported.value, documentTitle = title,
                    mirrorFailed = imported.mirrorFailed, canDiscover = false, discoverability = null, statusRes = 0,
                )
            }
            try {
                peer.sendImported()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logEx(throwable = e, message = "Send Bluetooth import receipt failed")
            }
            logd("Bluetooth archive import committed")
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            handlePermissionLoss()
            return false
        } catch (e: Exception) {
            logEx(throwable = e, message = "Receive Bluetooth archive failed")
            val reason = when (e) {
                is BluetoothTransferException -> e.reason
                is BluetoothArchiveException -> e.messageResId
                is IOException -> R.string.bluetooth_transfer_disconnected
                else -> R.string.bluetooth_transfer_receive_failed
            }
            val failure = e.receiptFailure(reason)
            if (channel != null && failure != null) {
                val unreadBodyBytes = (e as? BluetoothTransferException)?.unreadBodyBytes
                val wholeBodyReceived = stateFlow.value.fileSize > 0 && bytesTransferredFlow.value == stateFlow.value.fileSize
                replyFailure(channel, failure) {
                    if (unreadBodyBytes != null || wholeBodyReceived) channel.awaitFailureReceiptRead(unreadBodyBytes ?: 0L)
                }
            }
            resetReceiver(reason)
            return false
        } finally {
            consent = null
            closeBluetoothSocket(socket)
            deleteDirectory(directory)
            mutableStateFlow.update { it.copy(leaveBlocked = false) }
        }
    }

    private suspend fun stageReceiveDirectory(size: Long, onCreated: (File) -> Unit): File = try {
        app.awaitStartupCleanup()
        withContext(Dispatchers.IO) {
            val root = File(app.filesDir, BluetoothArchiveDirectory)
            Files.createDirectories(root.toPath())
            if (root.usableSpace < size) {
                throw BluetoothTransferException(R.string.error_bluetooth_archive_no_space)
            }
            Files.createTempDirectory(root.toPath(), "receive-").toFile().also(onCreated)
        }
    } catch (e: BluetoothTransferException) {
        throw e
    } catch (e: IOException) {
        throw BluetoothTransferException(bluetoothStorageReason(e))
    }

    private suspend fun importedTitle(documentId: String): String? = try {
        app.database.dao().document(documentId)?.title
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logEx(throwable = e, message = "Read imported Bluetooth document title failed")
        null
    }

    private suspend fun replyFailure(
        channel: BluetoothTransferChannel,
        failure: BluetoothReceiptFailure,
        awaitRead: suspend () -> Unit,
    ) {
        try {
            channel.sendFailure(failure)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logEx(throwable = e, message = "Send Bluetooth failure receipt failed")
            return
        }
        try {
            awaitRead()
        } catch (_: IOException) {
            // 对端读完回执后主动关断，Android 可能用 IOException 表示 EOF。
        }
    }

    fun accept() {
        if (foregroundFlow.value && !leaving) consent?.complete(true)
    }

    fun reject() {
        consent?.complete(false)
    }

    fun cancel() {
        val state = stateFlow.value
        if (state.leaveBlocked || state.isTerminal()) return
        val current = transferJob ?: return
        viewModelScope.launch {
            current.cancelAndJoin()
            if (leaving || stopJob?.isActive == true || stateFlow.value.isTerminal()) return@launch
            if (sourceDocumentId != null) {
                if (stateFlow.value.awaitingReceipt) {
                    setPhase(BluetoothTransferPhase.Unconfirmed, R.string.bluetooth_transfer_unconfirmed)
                    packageJob?.cancel()
                } else resetSender(R.string.bluetooth_transfer_cancelled)
            }
        }
    }

    suspend fun leave() {
        leaving = true
        mutableActionFlow.value = null
        backgroundJob?.cancel()
        GlobalBackgroundCallback.removeListener(backgroundListener)
        unregisterBluetoothReceiver()
        withContext(NonCancellable) {
            if (stateFlow.value.leaveBlocked) transferJob?.join()
            stopJob?.join()
            stopTasks(discardArchive = true)
        }
    }

    private fun handlePermissionLoss() {
        stopBluetooth(R.string.bluetooth_transfer_permission_required, discardArchive = false, resume = false)
        mutableStateFlow.update {
            it.copy(permissionRequired = true, enableRequired = false, canDiscover = false, canScan = false, discoverability = null)
        }
    }

    private fun stopBluetooth(@StringRes reason: Int, discardArchive: Boolean, resume: Boolean = true) {
        if (stopJob?.isActive == true || leaving) return
        val oldState = stateFlow.value
        unregisterBluetoothReceiver()
        mutableActionFlow.value = null
        if (sourceDocumentId != null) autoScanPending = true
        stopJob = viewModelScope.launch(start = CoroutineStart.LAZY) {
            stopTasks(discardArchive)
            if (!oldState.leaveBlocked && !stateFlow.value.isTerminal()) {
                if (stateFlow.value.awaitingReceipt) {
                    setPhase(BluetoothTransferPhase.Unconfirmed, R.string.bluetooth_transfer_unconfirmed)
                } else if (sourceDocumentId == null) resetReceiver(reason)
                else resetSender(reason)
            }
            mutableStateFlow.update {
                it.copy(
                    canScan = false, canDiscover = false, canRetryListening = false,
                    discoverability = null, scanning = false,
                )
            }
        }
        stopJob?.invokeOnCompletion {
            if (resume && foregroundFlow.value && !leaving) viewModelScope.launch { refresh() }
        }
        stopJob?.start()
    }

    private suspend fun stopTasks(discardArchive: Boolean) {
        val jobs = listOfNotNull(
            scanJob, refreshJob, retrySendJob,
            if (stateFlow.value.leaveBlocked) null else if (sourceDocumentId == null) listenJob else transferJob,
            if (discardArchive) packageJob else null,
        )
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
    }

    private fun setPhase(phase: BluetoothTransferPhase, @StringRes status: Int = 0) {
        mutableStateFlow.update { it.copy(phase = phase, statusRes = status) }
    }

    private fun setProgress(bytes: Long) {
        mutableBytesTransferredFlow.value = bytes
    }

    private fun showStatus(@StringRes message: Int) {
        mutableStateFlow.update { it.copy(statusRes = message) }
    }

    private fun resetReceiver(@StringRes reason: Int) {
        mutableBytesTransferredFlow.value = 0L
        mutableStateFlow.update {
            it.copy(
                phase = BluetoothTransferPhase.Ready, statusRes = reason, fileName = null, fileSize = 0,
                remoteName = null, canDiscover = false,
            )
        }
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (bluetoothReceiver !== this || leaving) return
                when (intent.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> when (
                        intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    ) {
                        BluetoothAdapter.STATE_ON -> refresh()
                        BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                            stopBluetooth(R.string.bluetooth_transfer_enable_required, discardArchive = false)
                        }
                    }
                    BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED -> refresh()
                    BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> if (
                        intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR) !=
                        BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE
                    ) {
                        mutableStateFlow.update { it.copy(discoverability = null) }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> handleBondState(intent)
                    BluetoothDevice.ACTION_PAIRING_REQUEST -> {
                        intent.bluetoothDevice()?.address?.let { trackIncomingPairing(it, inProgress = true) }
                    }
                }
            }
        }
        // 这些广播由系统保护，蓝牙服务可能以独立系统应用 UID 发送。
        ContextCompat.registerReceiver(
            app, receiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                if (sourceDocumentId == null) {
                    addAction(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED)
                    addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
                    addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
                }
            },
            ContextCompat.RECEIVER_EXPORTED,
        )
        bluetoothReceiver = receiver
    }

    private fun handleBondState(intent: Intent) {
        val address = intent.bluetoothDevice()?.address ?: return
        if (sourceDocumentId != null) {
            bondAttempt?.takeIf { it.address == address }?.changes?.trySend(Unit)
            return
        }
        when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
            BluetoothDevice.BOND_BONDING -> trackIncomingPairing(address, inProgress = true)
            BluetoothDevice.BOND_BONDED, BluetoothDevice.BOND_NONE -> {
                val previous = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                if (incomingPairing?.address == address || previous == BluetoothDevice.BOND_BONDING) {
                    trackIncomingPairing(address, inProgress = false)
                }
            }
        }
    }

    private fun isWaitingForIncomingConnection(): Boolean =
        sourceDocumentId == null && listenJob?.isActive == true && stateFlow.value.phase == BluetoothTransferPhase.Ready

    private fun remainingIncomingPairingGrace(): Long {
        val pairing = incomingPairing ?: return 0L
        if (!isWaitingForIncomingConnection() || receivingPeerAddress?.let { it != pairing.address } == true) return 0L
        val deadline = backgroundSinceMillis?.let {
            min(pairing.deadlineMillis, it + PairingTimeoutMillis + PairingReturnGraceMillis)
        } ?: pairing.deadlineMillis
        return max(0L, deadline - SystemClock.elapsedRealtime())
    }

    private fun trackIncomingPairing(address: String, inProgress: Boolean) {
        if (!isWaitingForIncomingConnection() || receivingPeerAddress?.let { it != address } == true) return
        val current = incomingPairing
        val now = SystemClock.elapsedRealtime()
        if (current != null && current.deadlineMillis > now) {
            if (current.address != address || current.inProgress == inProgress) return
        }
        val grace = if (inProgress) PairingTimeoutMillis else PairingReturnGraceMillis
        incomingPairing = IncomingPairing(address, now + grace, inProgress)
        if (!foregroundFlow.value && systemActionInFlightFlow.value == null) endInBackground(remainingIncomingPairingGrace())
    }

    private fun unregisterBluetoothReceiver() {
        val receiver = bluetoothReceiver
        bluetoothReceiver = null
        incomingPairing = null
        receiver?.let(app::unregisterReceiver)
    }

    override fun onCleared() {
        leaving = true
        GlobalBackgroundCallback.removeListener(backgroundListener)
        unregisterBluetoothReceiver()
        super.onCleared()
    }

    class Factory(
        private val app: PicNoteApplication,
        private val documentId: String?,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == BluetoothTransferViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return BluetoothTransferViewModel(app, documentId) as T
        }
    }

    companion object {
        const val DiscoverableSeconds = 120
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.toTransferDevice() = BluetoothTransferDevice(address, name?.ifBlank { null })

private fun Intent.bluetoothDevice(): BluetoothDevice? =
    IntentCompat.getParcelableExtra(this, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

private fun BluetoothTransferState.isTerminal(): Boolean = when (phase) {
    BluetoothTransferPhase.Imported, BluetoothTransferPhase.Sent,
    BluetoothTransferPhase.Unconfirmed, BluetoothTransferPhase.Failed -> true
    else -> false
}

private fun Exception.receiptFailure(@StringRes reason: Int): BluetoothReceiptFailure? =
    when (reason) {
        R.string.bluetooth_transfer_integrity -> BluetoothReceiptFailure.Integrity
        R.string.error_bluetooth_archive_legacy -> BluetoothReceiptFailure.Legacy
        R.string.error_bluetooth_archive_version -> BluetoothReceiptFailure.Version
        R.string.error_bluetooth_archive_limit -> BluetoothReceiptFailure.Limit
        R.string.error_bluetooth_archive_no_space -> BluetoothReceiptFailure.Space
        R.string.error_bluetooth_archive_io -> BluetoothReceiptFailure.Storage
        R.string.bluetooth_transfer_invalid_control -> BluetoothReceiptFailure.Protocol
        else -> if (this is BluetoothArchiveException) BluetoothReceiptFailure.Invalid else null
    }

private suspend fun deleteDirectory(directory: File?) {
    if (directory != null) withContext(NonCancellable + Dispatchers.IO) {
        deleteBluetoothArchiveFiles(directory)
    }
}
