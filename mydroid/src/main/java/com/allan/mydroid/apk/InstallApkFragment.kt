package com.allan.mydroid.apk

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.allan.mydroid.api.WSApisConst2.PROCESS_ABORTED
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK
import com.allan.mydroid.api.WSApisConst2.PROCESS_CHUNK_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGE_ERROR
import com.allan.mydroid.api.WSApisConst2.PROCESS_MERGING
import com.allan.mydroid.beansinner.ReceivingFileInfo
import com.allan.mydroid.bt.BleIpAdvertiser
import com.allan.mydroid.client.TransferTheme
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_android.simpleflow.StatusState
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_androiduiex.ui.ComposeViewFragment
import com.au.module_simplepermission.spGetBoolean
import com.au.module_simplepermission.spPutBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.io.File
import java.util.UUID
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/** 显示 APK 接收进度，并在页面可见期间按顺序发起系统安装。 */
class InstallApkFragment : ComposeViewFragment() {
    override var customBackActionEnable = false

    private val networkMonitor: GlobalNetworkMonitorObj by inject()
    private val bleIpAdvertiser = BleIpAdvertiser(this)
    private val receivePage = Any()
    private var serviceBound = false
    private var receiver by mutableStateOf<ApkReceiveService.ReceiverBinder?>(null)
    private var bindingFailed by mutableStateOf(false)
    private var notificationBlocked by mutableStateOf(false)
    private var notificationRequestInProgress by mutableStateOf(false)
    private var advertiserStarted = false
    private val installVerifier by lazy { ApkInstallVerifier(Globals.app) }
    private var submittingApk by mutableStateOf<ReceivedApk?>(null)
    private var installSourceAllowed by mutableStateOf(false)
    private val manualInstallFlow = MutableStateFlow<ReceivedApk?>(null)
    private var confirmationToken by mutableStateOf<String?>(null)
    private lateinit var notificationPermission: ActivityResultLauncher<String>
    private lateinit var installConfirmation: ActivityResultLauncher<Intent>
    private lateinit var activityResultId: String
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val owner = viewLifecycleOwnerLiveData.value ?: return
            if (!serviceBound || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return
            bindingFailed = false
            val binder = service as ApkReceiveService.ReceiverBinder
            receiver = binder
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) startReceiving(binder)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            receiver = null
            bindingFailed = true
        }

        override fun onBindingDied(name: ComponentName) {
            onServiceDisconnected(name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val process = ApkInstallSessions.activityResultPrefix
        val restored = savedInstanceState?.takeIf { it.getString(KEY_ACTIVITY_RESULT_PROCESS) == process }
        notificationRequestInProgress = restored?.getBoolean(KEY_NOTIFICATION_REQUEST_IN_PROGRESS) == true
        confirmationToken = restored?.getString(KEY_INSTALL_CONFIRMATION_TOKEN)
            ?.takeIf { it == ApkInstallSessions.attemptFlow.value?.token }
        activityResultId = restored?.getString(KEY_ACTIVITY_RESULT_ID) ?: UUID.randomUUID().toString()
        val resultKey = "$process.$activityResultId"
        val registry = requireActivity().activityResultRegistry
        // 同进程重建沿用注册键，新进程忽略旧权限页和安装页的返回结果。
        notificationPermission = registry.register(
            "$resultKey.notifications",
            this,
            ActivityResultContracts.RequestPermission(),
        ) {
            notificationRequestInProgress = false
            if (isResumed && view != null) updateNotificationAndDiscovery()
        }
        installConfirmation = registry.register(
            "$resultKey.install",
            this,
            ActivityResultContracts.StartActivityForResult(),
        ) {
            val token = confirmationToken
            confirmationToken = null
            if (token != null) ApkInstallSessions.finishConfirmation(token)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        bindingFailed = try {
            serviceBound = context.bindService(
                Intent(context, ApkReceiveService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
            !serviceBound
        } catch (e: SecurityException) {
            logEx(throwable = e) { "Bind APK receive service failed" }
            true
        }
        val owner = viewLifecycleOwner
        owner.lifecycleScope.launch {
            ApkInstallSessions.attemptFlow.collectLatest { attempt ->
                if (attempt != null) observeInstallation(attempt)
            }
        }
        owner.lifecycleScope.launch {
            combine(ApkInstallSessions.attemptFlow, ApkReceiveSession.filesFlow) { attempt, files ->
                attempt?.takeIf { it.phase == ApkInstallSessions.Phase.AwaitingConfirmation && isSuperseded(it, files) }
            }.distinctUntilChangedBy { it?.token }.collect { attempt ->
                if (attempt != null) ApkInstallSessions.abandonSuperseded(attempt)
            }
        }
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    combine(ApkInstallSessions.attemptFlow, snapshotFlow { notificationRequestInProgress || confirmationToken != null }) { attempt, requesting ->
                        if (requesting) null else attempt
                    }.collect { attempt ->
                        if (attempt == null || isSuperseded(attempt, ApkReceiveSession.filesFlow.value)) return@collect
                        val confirmation = ApkInstallSessions.takeConfirmation(attempt) ?: return@collect
                        confirmationToken = attempt.token
                        if (!openSystemActivity(confirmation, installConfirmation)) {
                            confirmationToken = null
                            val current = ApkInstallSessions.attemptFlow.value
                            if (current?.token == attempt.token && current.phase == ApkInstallSessions.Phase.AwaitingConfirmation) {
                                ApkInstallSessions.updatePhase(current, ApkInstallSessions.Phase.Failed)
                            }
                        }
                    }
                }
                launch {
                    combine(
                        ApkReceiveSession.filesFlow,
                        ApkInstallSessions.attemptFlow,
                        ApkReceiveSession.handledFilesFlow,
                        manualInstallFlow,
                        snapshotFlow { notificationRequestInProgress || confirmationToken != null },
                    ) { files, attempt, handled, manual, requestingNotifications ->
                        if (requestingNotifications || attempt?.phase?.blocksQueue == true) null
                        else files.asReversed().firstOrNull { it.file !in handled } ?: manual?.takeIf { it in files }
                    }.collect { apk ->
                        if (apk != null) submitInstallation(apk)
                    }
                }
            }
        }
    }

    private fun isSuperseded(attempt: ApkInstallSessions.Attempt, files: List<ReceivedApk>): Boolean {
        return files.any { it.buildInfo.applicationId == attempt.target.applicationId && it.file.absolutePath != attempt.apkPath }
    }

    override fun onResume() {
        super.onResume()
        installSourceAllowed = requireContext().packageManager.canRequestPackageInstalls()
        receiver?.let { startReceiving(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_NOTIFICATION_REQUEST_IN_PROGRESS, notificationRequestInProgress)
        outState.putString(KEY_INSTALL_CONFIRMATION_TOKEN, confirmationToken)
        outState.putString(KEY_ACTIVITY_RESULT_PROCESS, ApkInstallSessions.activityResultPrefix)
        outState.putString(KEY_ACTIVITY_RESULT_ID, activityResultId)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        bleIpAdvertiser.stop()
        advertiserStarted = false
        receiver?.stopReceiving(receivePage)
        if (serviceBound) requireContext().unbindService(serviceConnection)
        serviceBound = false
        receiver = null
        super.onDestroyView()
    }

    private fun startReceiving(binder: ApkReceiveService.ReceiverBinder) {
        binder.startReceiving(receivePage)
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationRequestInProgress &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
            !spGetBoolean(context, KEY_NOTIFICATION_ASKED, false)
        ) {
            spPutBoolean(context, KEY_NOTIFICATION_ASKED, true)
            notificationRequestInProgress = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        updateNotificationAndDiscovery()
    }

    private fun updateNotificationAndDiscovery() {
        // 等通知权限回调后再读取系统状态并启动 BLE 权限申请。
        if (notificationRequestInProgress) {
            notificationBlocked = true
            return
        }
        val context = context ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        val wasBlocked = notificationBlocked
        notificationBlocked = !manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(ApkReceiveService.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE

        val binder = receiver ?: return
        if (wasBlocked && !notificationBlocked) binder.refreshNotification(receivePage)
        if (!advertiserStarted) {
            advertiserStarted = true
            bleIpAdvertiser.start()
        }
    }

    @Composable
    override fun ScreenContent() {
        val state = receiver?.stateFlow?.collectAsStateWithLifecycle()?.value
        val network by networkMonitor.networkFlow.collectAsStateWithLifecycle()
        val networkInfo by networkMonitor.networkInfoFlow.collectAsStateWithLifecycle()
        val files by ApkReceiveSession.filesFlow.collectAsStateWithLifecycle()
        val handled by ApkReceiveSession.handledFilesFlow.collectAsStateWithLifecycle()
        val incoming by ApkReceiveSession.progressFlow.collectAsStateWithLifecycle()
        val activeChunkRequests by ApkReceiveSession.activeChunkRequestCountFlow.collectAsStateWithLifecycle()
        val attempt by ApkInstallSessions.attemptFlow.collectAsStateWithLifecycle()
        val host = networkInfo?.urlHost
        val port = networkInfo?.httpPort
        val address = if (state is StatusState.Success && host != null && port != null) "http://$host:$port" else null

        TransferTheme {
            SecondaryPageScaffold(
                title = stringResource(R.string.mydroid_install_apk),
                onBack = { findNavController().navigateUp() },
                backContentDescription = stringResource(R.string.transfer_back),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        ApkStatusCard {
                            if (address != null) {
                                SelectionContainer {
                                    Text(address, fontSize = 18.sp)
                                }
                                ApkStatusText(stringResource(R.string.mydroid_apk_instructions))
                            } else {
                                val message = when {
                                    bindingFailed || state is StatusState.Error -> R.string.mydroid_apk_service_error
                                    network is GlobalNetworkMonitorObj.NetworkStatus.Disconnected -> R.string.connect_wifi_or_hotspot
                                    else -> R.string.mydroid_apk_preparing
                                }
                                ApkStatusText(stringResource(message))
                            }
                        }
                    }
                    if (notificationBlocked && !notificationRequestInProgress) {
                        item {
                            SettingsRow(
                                message = R.string.mydroid_apk_notification_disabled,
                                action = R.string.mydroid_apk_notification_settings,
                                onClick = ::openNotificationSettings,
                            )
                        }
                    }
                    if (!installSourceAllowed && files.isNotEmpty()) {
                        item {
                            SettingsRow(
                                message = R.string.mydroid_apk_allow_install_source,
                                action = R.string.mydroid_apk_install_source_action,
                                onClick = { openInstallSourceSettings(explicit = true) },
                            )
                        }
                    }
                    if (activeChunkRequests > 0 && incoming.values.none { it.process == PROCESS_CHUNK }) {
                        item {
                            ApkStatusCard {
                                ApkStatusText(stringResource(R.string.mydroid_apk_receiving))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(3.dp))
                            }
                        }
                    }
                    items(incoming.entries.toList(), key = { "upload:${it.key}" }) { (_, progress) ->
                        IncomingApkRow(progress)
                    }
                    val currentAttempt = attempt
                    items(files.asReversed(), key = { it.file.path }) { apk ->
                        val phase = currentAttempt?.takeIf { it.apkPath == apk.file.absolutePath }?.phase
                        val status = when {
                            phase != null -> installStatus(phase)
                            submittingApk == apk -> R.string.mydroid_apk_install_preparing
                            apk.file in handled -> R.string.mydroid_apk_install_interrupted
                            else -> R.string.mydroid_apk_install_queued
                        }
                        val canRetry = installSourceAllowed && submittingApk == null &&
                            currentAttempt?.phase?.blocksQueue != true && apk.file in handled
                        InstallStatusRow(
                            fileName = apk.file.name,
                            status = status,
                            onRetry = if (canRetry) ({ openInstaller(apk) }) else null,
                        )
                    }
                    if (currentAttempt != null && files.none { it.file.absolutePath == currentAttempt.apkPath } &&
                        !isSuperseded(currentAttempt, files)
                    ) {
                        item {
                            InstallStatusRow(
                                fileName = File(currentAttempt.apkPath).name,
                                status = installStatus(currentAttempt.phase),
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun ApkStatusCard(content: @Composable ColumnScope.() -> Unit) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                content = content,
            )
        }
    }

    @Composable
    private fun ApkStatusText(text: String, modifier: Modifier = Modifier) {
        Text(
            text,
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    @Composable
    private fun SettingsRow(message: Int, action: Int, onClick: () -> Unit) {
        ApkStatusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApkStatusText(stringResource(message), modifier = Modifier.weight(1f))
                TextButton(onClick = onClick) { Text(stringResource(action)) }
            }
        }
    }

    @Composable
    private fun IncomingApkRow(info: ReceivingFileInfo) {
        val status = when (info.process) {
            PROCESS_MERGING -> R.string.mydroid_apk_receive_merging
            PROCESS_ABORTED -> R.string.mydroid_apk_receive_canceled
            PROCESS_CHUNK_ERROR, PROCESS_MERGE_ERROR -> R.string.mydroid_apk_receive_failed
            else -> R.string.mydroid_apk_receiving
        }
        ApkStatusCard {
            Text(info.fileName, style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ApkStatusText(stringResource(status), modifier = Modifier.weight(1f))
                if (info.totalChunks > 0) {
                    val count = min(info.currentChunkIndex, info.totalChunks)
                    val percent = count * 100L / info.totalChunks
                    ApkStatusText(stringResource(R.string.mydroid_apk_receive_chunks, count, info.totalChunks, percent))
                }
            }
            if (info.totalChunks > 0 && (info.process == PROCESS_CHUNK || info.process == PROCESS_MERGING)) {
                val progress = min(info.currentChunkIndex.toFloat() / info.totalChunks, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
    }

    @Composable
    private fun InstallStatusRow(fileName: String, status: Int, onRetry: (() -> Unit)? = null) {
        ApkStatusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(fileName, style = MaterialTheme.typography.titleSmall)
                    ApkStatusText(stringResource(status))
                }
                if (onRetry != null) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.mydroid_apk_install_action)) }
                }
            }
        }
    }

    private fun installStatus(phase: ApkInstallSessions.Phase): Int {
        return when (phase) {
            ApkInstallSessions.Phase.AwaitingResult -> R.string.mydroid_apk_install_waiting
            ApkInstallSessions.Phase.AwaitingConfirmation -> R.string.mydroid_apk_install_confirm
            ApkInstallSessions.Phase.Succeeded -> R.string.mydroid_apk_install_checking
            ApkInstallSessions.Phase.Verified -> R.string.mydroid_apk_install_verified
            ApkInstallSessions.Phase.Unverified -> R.string.mydroid_apk_install_unverified
            ApkInstallSessions.Phase.Unconfirmed -> R.string.mydroid_apk_install_interrupted
            ApkInstallSessions.Phase.Aborted -> R.string.mydroid_apk_install_aborted
            ApkInstallSessions.Phase.Failed -> R.string.mydroid_apk_install_failed
        }
    }

    private fun openNotificationSettings() {
        val context = requireContext()
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelBlocked = manager.areNotificationsEnabled() &&
            manager.getNotificationChannel(ApkReceiveService.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        val settingsIntent = if (channelBlocked) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_CHANNEL_ID, ApkReceiveService.CHANNEL_ID)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        }.putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            startActivity(settingsIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }
    }

    private fun openInstaller(apk: ReceivedApk) {
        if (submittingApk != null || ApkInstallSessions.attemptFlow.value?.phase?.blocksQueue == true) return
        if (!requireContext().packageManager.canRequestPackageInstalls()) openInstallSourceSettings(explicit = true)
        manualInstallFlow.value = apk
    }

    private fun openInstallSourceSettings(explicit: Boolean) {
        if (!ApkInstallSessions.claimInstallSourceSettings(explicit)) return
        val context = requireContext()
        val settings = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.fromParts("package", context.packageName, null))
        if (openSystemActivity(settings)) MyToastUtil.toastOnBottom(Globals.app.getString(R.string.mydroid_apk_allow_install_source))
    }

    private suspend fun submitInstallation(apk: ReceivedApk) {
        val context = context ?: return
        if (!context.packageManager.canRequestPackageInstalls()) {
            openInstallSourceSettings(explicit = false)
            return
        }
        val manual = manualInstallFlow.value == apk
        submittingApk = apk
        try {
            if (ApkInstallSessions.submit(apk, automatic = !manual) && manual) {
                manualInstallFlow.compareAndSet(apk, null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApkReceiveSession.markHandled(apk)
            if (manual) manualInstallFlow.compareAndSet(apk, null)
            logEx(throwable = e) { "Submit APK install session failed" }
            MyToastUtil.toastOnBottom(Globals.app.getString(R.string.mydroid_apk_unavailable))
        } finally {
            submittingApk = null
        }
    }

    private fun openSystemActivity(intent: Intent, launcher: ActivityResultLauncher<Intent>? = null): Boolean {
        return try {
            if (launcher == null) startActivity(intent) else launcher.launch(intent)
            true
        } catch (e: Exception) {
            if (e !is ActivityNotFoundException && e !is SecurityException) throw e
            logEx(throwable = e) { "Open system activity for APK install failed" }
            MyToastUtil.toastOnBottom(Globals.app.getString(R.string.mydroid_apk_unavailable))
            false
        }
    }

    private suspend fun observeInstallation(attempt: ApkInstallSessions.Attempt) {
        when (attempt.phase) {
            ApkInstallSessions.Phase.AwaitingResult, ApkInstallSessions.Phase.AwaitingConfirmation -> {
                var missingChecks = 0
                do {
                    delay(3000.milliseconds)
                    missingChecks = if (ApkInstallSessions.exists(attempt)) 0 else missingChecks + 1
                } while (missingChecks < 3)
                ApkInstallSessions.updatePhase(attempt, ApkInstallSessions.Phase.Unconfirmed)
            }
            ApkInstallSessions.Phase.Succeeded -> verifyInstallation(attempt)
            else -> Unit
        }
    }

    private suspend fun verifyInstallation(attempt: ApkInstallSessions.Attempt) {
        repeat(6) { index ->
            if (installVerifier.check(attempt.target, refreshDigest = index == 0) == ApkInstallVerifier.Status.Verified) {
                ApkInstallSessions.updatePhase(attempt, ApkInstallSessions.Phase.Verified)
                return
            }
            if (index < 5) delay(3000.milliseconds)
        }
        ApkInstallSessions.updatePhase(attempt, ApkInstallSessions.Phase.Unverified)
    }

    companion object {
        private const val KEY_NOTIFICATION_ASKED = "mydroid_apk_notification_asked"
        private const val KEY_NOTIFICATION_REQUEST_IN_PROGRESS = "apk_notification_request_in_progress"
        private const val KEY_INSTALL_CONFIRMATION_TOKEN = "apk_install_confirmation_token"
        private const val KEY_ACTIVITY_RESULT_PROCESS = "apk_activity_result_process"
        private const val KEY_ACTIVITY_RESULT_ID = "apk_activity_result_id"
    }
}
