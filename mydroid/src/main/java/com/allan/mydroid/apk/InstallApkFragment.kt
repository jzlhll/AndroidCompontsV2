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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.allan.mydroid.bt.BleIpAdvertiser
import com.allan.mydroid.client.TransferTheme
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.au.module_android.log.logEx
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.utilsmedia.getUriFromFile
import com.au.module_androidui.toast.MyToastUtil
import com.au.module_androiduiex.landscape.SecondaryPageScaffold
import com.au.module_androiduiex.ui.ComposeViewFragment
import com.au.module_simplepermission.spGetBoolean
import com.au.module_simplepermission.spPutBoolean
import org.koin.android.ext.android.inject
import java.io.File

/** 显示 APK 上传地址和已接收的 APK，由用户选择需要安装的文件。 */
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

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationRequestInProgress = false
        if (isResumed && view != null) updateNotificationAndDiscovery()
    }

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
        notificationRequestInProgress = savedInstanceState?.getBoolean(KEY_NOTIFICATION_REQUEST_IN_PROGRESS) == true
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
    }

    override fun onResume() {
        super.onResume()
        receiver?.let { startReceiving(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_NOTIFICATION_REQUEST_IN_PROGRESS, notificationRequestInProgress)
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
                    if (address == null) {
                        item {
                            val message = when {
                                bindingFailed || state is StatusState.Error -> R.string.mydroid_apk_service_error
                                network is GlobalNetworkMonitorObj.NetworkStatus.Disconnected -> R.string.connect_wifi_or_hotspot
                                else -> R.string.mydroid_apk_preparing
                            }
                            Text(stringResource(message))
                        }
                    } else {
                        item { SelectionContainer { Text(address) } }
                        item { Text(stringResource(R.string.mydroid_apk_instructions)) }
                    }
                    if (notificationBlocked && !notificationRequestInProgress) {
                        item {
                            Column {
                                Text(stringResource(R.string.mydroid_apk_notification_disabled))
                                TextButton(onClick = ::openNotificationSettings) {
                                    Text(stringResource(R.string.mydroid_apk_notification_settings))
                                }
                            }
                        }
                    }
                    items(files, key = { it.path }) { file ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, modifier = Modifier.weight(1f))
                            TextButton(onClick = { openInstaller(file) }) {
                                Text(stringResource(R.string.mydroid_apk_install_action))
                            }
                        }
                    }
                }
            }
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

    private fun openInstaller(file: File) {
        val context = requireContext()
        val uri = if (file.isFile) getUriFromFile(context, file) else null
        if (uri == null) {
            MyToastUtil.toastOnBottom(getString(R.string.mydroid_apk_unavailable))
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            if (e !is ActivityNotFoundException && e !is SecurityException) throw e
            logEx(throwable = e) { "Open APK installer failed" }
            MyToastUtil.toastOnBottom(getString(R.string.mydroid_apk_unavailable))
        }
    }

    companion object {
        private const val KEY_NOTIFICATION_ASKED = "mydroid_apk_notification_asked"
        private const val KEY_NOTIFICATION_REQUEST_IN_PROGRESS = "apk_notification_request_in_progress"
    }
}
