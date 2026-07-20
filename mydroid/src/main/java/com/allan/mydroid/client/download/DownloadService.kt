package com.allan.mydroid.client.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_android.log.logEx
import com.au.module_android.service.AutoStopService
import com.au.module_android.utils.launchOnIOThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.creator.downloadFile
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * 下载 ForegroundService。
 *
 * - 每个 uriUuid 对应一次 onStartCommand，独立 startId 与协程；任务结束后 [stopWrap]。
 * - 每任务独立 [PowerManager.PARTIAL_WAKE_LOCK]，tag 加 uriUuid 后缀，try/finally release。
 * - 复用 [downloadFile] 扩展（useTempFile=true，原子重命名），进度回调更新 GlobalDownloadObj。
 * - 取消靠 [GlobalDownloadObj.cancel] 设置 Canceled 状态，progressListener 检测到后抛 [CancellationException]
 *   终止下载并清理临时文件（downloadFile 内部 invokeOnCancellation 会 call.cancel + 删 tmp）。
 */
class DownloadService : AutoStopService(), KoinComponent {
    private val supervisor = SupervisorJob()
    private val scope = CoroutineScope(supervisor)
    private val globalDownloadObj: GlobalDownloadObj by inject()

    override fun getNotifyName(): String = "MyDroid 文件下载"

    override fun foregroundType(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    override fun getPendingIntent(): PendingIntent = GlobalDownloadObj.buildHomePendingIntent()

    override fun onHandleWork(intent: Intent, startIdStr: String) {
        val ip = intent.getStringExtra(EXTRA_IP) ?: run {
            loge { "DownloadService missing EXTRA_IP, stop #$startIdStr" }
            stopWrap(startIdStr)
            return
        }
        val uriUuid = intent.getStringExtra(EXTRA_URI_UUID) ?: run {
            loge { "DownloadService missing EXTRA_URI_UUID, stop #$startIdStr" }
            stopWrap(startIdStr)
            return
        }

        val task = globalDownloadObj.findTask(ip, uriUuid)
        if (task == null) {
            logdNoFile { "DownloadService task not found ip=$ip uuid=$uriUuid, stop #$startIdStr" }
            stopWrap(startIdStr)
            return
        }

        scope.launchOnIOThread {
            val tag = "MyDroid::DownloadWakeLock::$uriUuid"
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag)
            wakeLock.setReferenceCounted(false)
            try {
                wakeLock.acquire()
                doDownload(task)
            } catch (e: CancellationException) {
                logdNoFile { "Download canceled ip=${task.ip} uuid=${task.uriUuid}" }
                globalDownloadObj.updateTask(task.ip, task.uriUuid) {
                    it.copy(state = DownloadState.Canceled)
                }
                deletePartialFile(task.destFile)
                throw e
            } catch (e: Exception) {
                logEx(throwable = e) { "Download failed ip=${task.ip} uuid=${task.uriUuid}" }
                globalDownloadObj.updateTask(task.ip, task.uriUuid) {
                    it.copy(state = DownloadState.Failed, error = e.message ?: "Download failed")
                }
                deletePartialFile(task.destFile)
            } finally {
                if (wakeLock.isHeld) {
                    wakeLock.release()
                }
                stopWrap(startIdStr)
            }
        }
    }

    private suspend fun doDownload(task: DownloadTask) {
        globalDownloadObj.updateTask(task.ip, task.uriUuid) {
            it.copy(state = DownloadState.Running, receivedBytes = 0, error = null)
        }

        val dirPath = File(task.destFilePath).parent ?: throw IllegalStateException("invalid dest path: ${task.destFilePath}")
        val fileName = File(task.destFilePath).name
        val request = Request.Builder().url(task.url).get().build()
        val client = OkhttpGlobal.okHttpClient()

        val resultFile = client.downloadFile(
            request = request,
            dirPath = dirPath,
            fileName = fileName,
            useTempFile = true,
            deleteFileIfNoSuccess = true,
            progressListener = { downloadLen, totalLen, _ ->
                // 取消检查：用户调用 globalDownloadObj.cancel 后状态变化，立即中断
                val cur = globalDownloadObj.findTask(task.ip, task.uriUuid)
                if (cur != null && cur.state == DownloadState.Canceled) {
                    throw CancellationException("user canceled")
                }
                globalDownloadObj.updateTask(task.ip, task.uriUuid) {
                    it.copy(receivedBytes = downloadLen)
                }
            }
        ) ?: throw RuntimeException("download returned null file")

        // 以实际落盘大小校验
        val received = resultFile.length()
        if (task.fileSize > 0 && received < task.fileSize) {
            throw RuntimeException("Incomplete download: $received / ${task.fileSize}")
        }
        globalDownloadObj.updateTask(task.ip, task.uriUuid) {
            it.copy(state = DownloadState.Completed, receivedBytes = received)
        }
        logdNoFile { "Download completed ip=${task.ip} uuid=${task.uriUuid} received=$received" }
    }

    private fun deletePartialFile(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            loge { "deletePartialFile failed: ${e.message}" }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        supervisor.cancel()
    }

    companion object {
        const val EXTRA_IP = GlobalDownloadObj.EXTRA_IP
        const val EXTRA_URI_UUID = GlobalDownloadObj.EXTRA_URI_UUID
    }
}
