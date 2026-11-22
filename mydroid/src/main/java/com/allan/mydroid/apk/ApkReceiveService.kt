package com.allan.mydroid.apk

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import com.allan.mydroid.MyDroidActivity
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.globals.GlobalDroidServerObj
import com.au.module_android.log.logEx
import com.au.module_android.simpleflow.StatusState
import com.au.module_android.simpleflow.createStatusStateFlow
import com.au.module_android.simpleflow.setError
import com.au.module_android.simpleflow.setLoading
import com.au.module_android.simpleflow.setSuccess
import com.au.module_android.simpleflow.setUninitialized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/** 页面存在期间维持 APK 接收，切到后台后仍可继续传输。 */
class ApkReceiveService : Service() {
    private val server: GlobalDroidServerObj by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val receiveStateFlow = createStatusStateFlow<Unit>()
    private val livePageToken = Any()
    private val receivePages = mutableSetOf<Any>()
    private var startRequested = false
    private var serviceStartId: Int? = null
    private var startJob: Job? = null
    private var foregroundNotification: Notification? = null

    inner class ReceiverBinder : Binder() {
        val stateFlow = receiveStateFlow.asStateFlow()

        fun startReceiving(page: Any) {
            receivePages.add(page)
            if (startRequested) return
            startRequested = true
            receiveStateFlow.setLoading()
            try {
                startForegroundService(Intent(this@ApkReceiveService, ApkReceiveService::class.java))
            } catch (e: Exception) {
                reportStartError(e)
            }
        }

        fun stopReceiving(page: Any) {
            if (receivePages.remove(page) && receivePages.isEmpty()) stopReceivingSession()
        }

        fun refreshNotification(page: Any) {
            if (page !in receivePages) return
            val notification = foregroundNotification ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return

            try {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                logEx(throwable = e) { "Refresh APK receive notification failed" }
            }
        }
    }

    private val binder = ReceiverBinder()

    override fun onBind(intent: Intent): Binder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceStartId = startId
        try {
            showForegroundNotification()
            // 请求提交后页面可能已退栈，先完成前台服务启动，再立即结束。
            if (receivePages.isEmpty()) {
                stopReceivingSession()
            } else if (startJob == null) {
                startJob = scope.launch {
                    try {
                        ApkReceiveSession.prepareCache()
                        ApkInstallSessions.prepare()
                        receiveStateFlow.setSuccess(Unit)
                        server.enterLivePage(livePageToken, MyDroidMode.InstallApk)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportStartError(e)
                    }
                }
            }
        } catch (e: Exception) {
            reportStartError(e)
        }
        return START_NOT_STICKY
    }

    override fun onUnbind(intent: Intent?): Boolean {
        receivePages.clear()
        stopReceivingSession()
        return false
    }

    override fun onDestroy() {
        stopReceivingSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopReceivingSession() {
        startRequested = false
        foregroundNotification = null
        startJob?.cancel()
        startJob = null
        if (receiveStateFlow.value is StatusState.Success) server.leaveLivePage(livePageToken)
        receiveStateFlow.setUninitialized()
        serviceStartId?.let {
            serviceStartId = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(it)
        }
    }

    private fun reportStartError(e: Exception) {
        logEx(throwable = e) { "Start APK receive service failed" }
        stopReceivingSession()
        receiveStateFlow.setError(e)
    }

    private fun showForegroundNotification() {
        val title = getString(R.string.mydroid_install_apk)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW),
        )
        val openPage = Intent(this, MyDroidActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, NOTIFICATION_ID, openPage, PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(getString(R.string.mydroid_apk_receiving_notification))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        foregroundNotification = notification
    }

    companion object {
        const val CHANNEL_ID = "apk_receive"
        private const val NOTIFICATION_ID = 0x113
    }
}
