package com.allan.mydroid.client.download

import android.app.PendingIntent
import android.content.Intent
import android.util.AtomicFile
import com.allan.mydroid.R
import com.allan.mydroid.SplashActivity
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.Globals
import com.au.module_android.log.logEx
import com.au.module_gson.fromGsonList
import com.au.module_gson.toGsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Call
import java.io.File
import java.util.UUID

/** 应用级下载状态与请求所有者，跨页面保留任务并持久化最终结果。 */
class GlobalDownloadObj {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasksFlow = _tasksFlow.asStateFlow()
    private val calls = mutableMapOf<String, Call>()
    private val snapshots = Channel<List<DownloadTask>>(Channel.CONFLATED)
    private val history = AtomicFile(File(Globals.app.filesDir, "client-downloads.json"))
    private val restored = scope.async {
        try {
            val tasks = if (history.baseFile.exists()) history.openRead().bufferedReader().use {
                it.readText().fromGsonList<DownloadTask>()
            } else emptyList()
            synchronized(lock) {
                _tasksFlow.value = tasks.map {
                    when {
                        it.active -> it.copy(state = DownloadState.Failed, error = Globals.getString(R.string.transfer_interrupted))
                        it.state == DownloadState.Completed && !it.destFile.isFile -> it.copy(state = DownloadState.Missing)
                        else -> it
                    }
                }
            }
        } catch (e: Exception) {
            logEx(throwable = e) { "Restore downloads failed" }
        }
    }

    init {
        scope.launch {
            restored.await()
            for (snapshot in snapshots) {
                var output: java.io.FileOutputStream? = null
                try {
                    output = history.startWrite()
                    output.write(snapshot.toGsonString().toByteArray(Charsets.UTF_8))
                    history.finishWrite(output)
                } catch (e: Exception) {
                    history.failWrite(output)
                    logEx(throwable = e) { "Persist downloads failed" }
                }
            }
        }
    }

    fun tasksFlow(ip: String, httpPort: Int) = tasksFlow.map { tasks ->
        tasks.filter { it.ip == ip && it.httpPort == httpPort }
    }.distinctUntilChanged()

    suspend fun enqueue(task: DownloadTask) {
        restored.await()
        synchronized(lock) {
            val existing = _tasksFlow.value.find { it.hostKey == task.hostKey && it.uriUuid == task.uriUuid }
            if (existing != null && (existing.active || existing.state == DownloadState.Completed)) return
            _tasksFlow.value = _tasksFlow.value.filterNot { it.hostKey == task.hostKey && it.uriUuid == task.uriUuid } + task
            snapshots.trySend(_tasksFlow.value)
        }
        try {
            Globals.app.startForegroundService(Intent(Globals.app, DownloadService::class.java).apply {
                putExtra(EXTRA_ATTEMPT, task.attemptId)
            })
        } catch (e: Exception) {
            finish(task, e.message)
        }
    }

    suspend fun findTask(attemptId: String): DownloadTask? {
        restored.await()
        return synchronized(lock) { _tasksFlow.value.find { it.attemptId == attemptId } }
    }

    fun cancel(task: DownloadTask) {
        synchronized(lock) {
            val current = _tasksFlow.value.find { it.attemptId == task.attemptId } ?: return
            if (!current.active || current.state == DownloadState.Canceling) return
            val call = calls[current.attemptId]
            updateTask(current) { it.copy(state = if (call == null) DownloadState.Canceled else DownloadState.Canceling) }
            call?.cancel()
        }
    }

    suspend fun retry(task: DownloadTask) {
        if (task.active || task.state == DownloadState.Completed) return
        enqueue(task.copy(state = DownloadState.Pending, receivedBytes = 0, bytesPerSecond = 0, error = null,
            destFilePath = "", attemptId = UUID.randomUUID().toString()))
    }

    fun register(task: DownloadTask, call: Call): Boolean = synchronized(lock) {
        val current = _tasksFlow.value.find { it.attemptId == task.attemptId } ?: return@synchronized false
        if (current.state != DownloadState.Pending || calls.containsKey(task.attemptId)) return@synchronized false
        calls[task.attemptId] = call
        updateTask(task) { it.copy(state = DownloadState.Running) }
        true
    }

    fun updateTask(task: DownloadTask, updater: (DownloadTask) -> DownloadTask) {
        synchronized(lock) {
            var changedState = false
            _tasksFlow.value = _tasksFlow.value.map {
                if (it.attemptId == task.attemptId) updater(it).also { next -> changedState = next.state != it.state }
                else it
            }
            if (changedState) snapshots.trySend(_tasksFlow.value)
        }
    }

    fun complete(task: DownloadTask, temp: File) {
        synchronized(lock) {
            val current = _tasksFlow.value.find { it.attemptId == task.attemptId } ?: return
            if (current.state != DownloadState.Running) return
            val result = TransferFiles.publish(temp, task.name)
            updateTask(task) { it.copy(state = DownloadState.Completed, destFilePath = result.absolutePath,
                receivedBytes = result.length(), error = null) }
        }
    }

    fun finish(task: DownloadTask, error: String?) {
        synchronized(lock) {
            calls.remove(task.attemptId)
            updateTask(task) {
                when (it.state) {
                    DownloadState.Canceling -> it.copy(state = DownloadState.Canceled)
                    DownloadState.Running, DownloadState.Pending -> it.copy(state = DownloadState.Failed,
                        error = error ?: Globals.getString(R.string.transfer_interrupted))
                    else -> it
                }
            }
        }
    }

    fun removeRecord(task: DownloadTask) {
        synchronized(lock) {
            if (_tasksFlow.value.any { it.attemptId == task.attemptId && it.active }) return
            _tasksFlow.value = _tasksFlow.value.filterNot { it.attemptId == task.attemptId }
            snapshots.trySend(_tasksFlow.value)
        }
    }

    companion object {
        const val EXTRA_ATTEMPT = "download_attempt"
        fun buildHomePendingIntent(): PendingIntent {
            val intent = Intent(Globals.app, SplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(Globals.app, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
