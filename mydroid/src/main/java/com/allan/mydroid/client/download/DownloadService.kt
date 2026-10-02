package com.allan.mydroid.client.download

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.PowerManager
import com.allan.mydroid.R
import com.allan.mydroid.repository.GlobalFileListRepoObj
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.log.logEx
import com.au.module_android.service.AutoStopService
import com.allan.mydroid.client.api.ClientHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** 前台下载服务持有请求至落盘结束，取消直接中断网络读取，每次尝试仅清理自己的临时文件。 */
class DownloadService : AutoStopService(), KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val globalDownloadObj: GlobalDownloadObj by inject()
    private val fileListRepo: GlobalFileListRepoObj by inject()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    override fun getNotifyName(): String = getString(R.string.connect_to_host_receive_title)
    override fun foregroundType(): Int = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    override fun getPendingIntent(): PendingIntent = GlobalDownloadObj.buildHomePendingIntent()

    override fun onHandleWork(intent: Intent, startIdStr: String) {
        val attempt = intent.getStringExtra(GlobalDownloadObj.EXTRA_ATTEMPT)
        if (attempt == null) {
            stopWrap(startIdStr)
            return
        }
        scope.launch {
            var task: DownloadTask? = null
            var temp: File? = null
            var wakeLock: PowerManager.WakeLock? = null
            var registered = false
            var error: String? = null
            try {
                val current = globalDownloadObj.findTask(attempt) ?: return@launch
                task = current
                val call = ClientHttp.client.newCall(Request.Builder().url(current.url).build())
                if (!globalDownloadObj.register(current, call)) return@launch
                registered = true
                activeCalls[attempt] = call
                ensureActive()
                val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyDroid:Download:$attempt").apply {
                    setReferenceCounted(false)
                    acquire()
                }
                val staging = TransferFiles.temporaryFile()
                temp = staging
                val startedAt = android.os.SystemClock.elapsedRealtime()
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("Empty response")
                    var received = 0L
                    var lastReported = 0L
                    body.byteStream().use { input ->
                        staging.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                received += count
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now - lastReported >= 100) {
                                    val elapsed = now - startedAt
                                    val speed = if (elapsed > 0) (received * 1000.0 / elapsed).toLong() else 0L
                                    globalDownloadObj.updateTask(current) { it.copy(receivedBytes = received, bytesPerSecond = speed) }
                                    lastReported = now
                                }
                            }
                        }
                    }
                    if ((current.fileSize > 0 && received != current.fileSize) ||
                        (body.contentLength() >= 0 && received != body.contentLength())) {
                        throw IOException("Download size mismatch: $received")
                    }
                }
                ensureActive()
                globalDownloadObj.complete(current, staging)
                fileListRepo.reloadFileList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message
                logEx(throwable = e) { "Download failed: $attempt" }
            } finally {
                if (registered) activeCalls.remove(attempt)?.cancel()
                temp?.delete()
                if (registered) task?.let { globalDownloadObj.finish(it, error) }
                wakeLock?.let { if (it.isHeld) it.release() }
                stopWrap(startIdStr)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        activeCalls.values.forEach { it.cancel() }
        super.onDestroy()
    }
}
