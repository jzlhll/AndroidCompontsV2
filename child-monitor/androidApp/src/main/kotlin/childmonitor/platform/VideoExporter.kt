package childmonitor.platform

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import childmonitor.android.R
import childmonitor.storage.AndroidFileStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoExportState(val busy: Boolean = false, val message: Int? = null)

/** 应用持有已确认的录像复制任务；页面返回或家长锁定不会中断导出。 */
class VideoExporter(private val context: Context, private val files: AndroidFileStorage, private val scope: CoroutineScope) {
    private val mutableStateFlow = MutableStateFlow(VideoExportState())
    val stateFlow = mutableStateFlow.asStateFlow()

    fun export(path: String, destination: Uri) {
        val requestId = java.util.UUID.randomUUID().toString()
        // 媒体目录的末级名称即会话 ID，不输出源路径或用户选择的目标 URI。
        val sessionId = path.substringBeforeLast('/').substringAfterLast('/')
        scope.launch(Dispatchers.Main.immediate) {
            var copiedBytes = 0L
            logdNoFile(tag = TAG) { "export accepted sessionId=$sessionId requestId=$requestId" }
            mutableStateFlow.value = VideoExportState(busy = true)
            var message = R.string.export_success
            try {
                withContext(Dispatchers.IO) {
                    files.resolve(path).inputStream().use { input ->
                        checkNotNull(context.contentResolver.openOutputStream(destination, "w")).use { output ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                ensureActive()
                                val size = input.read(buffer)
                                if (size < 0) break
                                output.write(buffer, 0, size)
                                copiedBytes += size
                            }
                        }
                    }
                }
                logdNoFile(tag = TAG) { "export completed sessionId=$sessionId requestId=$requestId bytes=$copiedBytes" }
            } catch (error: Exception) {
                val deleted = withContext(NonCancellable + Dispatchers.IO) {
                    try { DocumentsContract.deleteDocument(context.contentResolver, destination) }
                    catch (_: Exception) { false }
                }
                loge(tag = TAG) { "export failed sessionId=$sessionId requestId=$requestId bytes=$copiedBytes canceled=${error is CancellationException} targetDeleted=$deleted errorType=${error.javaClass.simpleName}" }
                message = if (deleted) R.string.export_failed else R.string.export_cleanup_failed
                if (error is CancellationException) throw error
            } finally {
                mutableStateFlow.value = VideoExportState(message = message)
            }
        }
    }
}
