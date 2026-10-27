package com.allan.mydroid.client.download

import android.app.PendingIntent
import android.content.Intent
import com.allan.mydroid.R
import com.allan.mydroid.SplashActivity
import com.allan.mydroid.repository.TransferFiles
import com.au.module_android.Globals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okhttp3.Call
import java.io.File

/** 应用级下载状态与请求所有者，跨页面持有活动请求，任务状态仅保留在内存。 */
class GlobalDownloadObj {
    private val lock = Any()
    private val _tasksFlow = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val tasksFlow = _tasksFlow.asStateFlow()
    private val calls = mutableMapOf<String, Call>()

    fun tasksFlow(ip: String, httpPort: Int) = tasksFlow.map { tasks ->
        tasks.filter { it.ip == ip && it.httpPort == httpPort }
    }.distinctUntilChanged()

    fun enqueue(task: DownloadTask) {
        synchronized(lock) {
            val existing = _tasksFlow.value.find { it.hostKey == task.hostKey && it.uriUuid == task.uriUuid }
            if (existing != null && (existing.active || existing.state == DownloadState.Completed)) return
            _tasksFlow.value = _tasksFlow.value.filterNot { it.hostKey == task.hostKey && it.uriUuid == task.uriUuid } + task
        }
        try {
            Globals.app.startForegroundService(Intent(Globals.app, DownloadService::class.java).apply {
                putExtra(EXTRA_ATTEMPT, task.attemptId)
            })
        } catch (e: Exception) {
            finish(task, e.message)
        }
    }

    fun findTask(attemptId: String): DownloadTask? {
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

    fun register(task: DownloadTask, call: Call): Boolean = synchronized(lock) {
        val current = _tasksFlow.value.find { it.attemptId == task.attemptId } ?: return@synchronized false
        if (current.state != DownloadState.Pending || calls.containsKey(task.attemptId)) return@synchronized false
        calls[task.attemptId] = call
        updateTask(task) { it.copy(state = DownloadState.Running) }
        true
    }

    fun updateTask(task: DownloadTask, updater: (DownloadTask) -> DownloadTask) {
        synchronized(lock) {
            _tasksFlow.value = _tasksFlow.value.map {
                if (it.attemptId == task.attemptId) updater(it)
                else it
            }
        }
    }

    fun complete(task: DownloadTask, temp: File) {
        synchronized(lock) {
            val current = _tasksFlow.value.find { it.attemptId == task.attemptId } ?: return
            if (current.state != DownloadState.Running) return
            val result = TransferFiles.publish(temp, task.name)
            updateTask(task) { it.copy(state = DownloadState.Completed,
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

    fun clearFinishedTasks(ip: String, httpPort: Int) {
        synchronized(lock) {
            _tasksFlow.value = _tasksFlow.value.filterNot { it.ip == ip && it.httpPort == httpPort && !it.active }
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
