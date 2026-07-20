package com.allan.mydroid.client.download

import android.content.Intent
import com.allan.mydroid.SplashActivity
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent

/**
 * 下载任务全局状态容器。Koin single。
 *
 * - 内部按 ip 维护任务列表，进入不同 host 的 client 页面只看到对应 ip 的进度，跨 host 不串号。
 * - 不持有 Android Context（Koin single 跨 Fragment 生命周期），通过 [Globals.app] 启动 service。
 * - 初始化时把上一次 Running 项标记为 Failed（app 重启后无法继续）。
 */
class GlobalDownloadObj : KoinComponent {
    private val _hostsFlow = MutableStateFlow<Map<String, HostDownloadList>>(emptyMap())
    val hostsFlow: StateFlow<Map<String, HostDownloadList>> = _hostsFlow.asStateFlow()

    private val lock = Any()

    init {
        // 启动时把 Running 改 Failed
        synchronized(lock) {
            val cur = _hostsFlow.value.toMutableMap()
            cur.forEach { (ip, list) ->
                val newTasks = list.tasks.map { task ->
                    if (task.state == DownloadState.Running || task.state == DownloadState.Pending) {
                        task.copy(state = DownloadState.Failed, error = "App restart, download interrupted")
                    } else task
                }
                cur[ip] = list.copy(tasks = newTasks)
            }
            _hostsFlow.value = cur
        }
    }

    /** 当前 ip 的任务列表 flow。页面订阅它即可，自动随 host 切换隔离。 */
    fun tasksFlow(ip: String): Flow<List<DownloadTask>> {
        return hostsFlow.map { it[ip]?.tasks ?: emptyList() }.distinctUntilChanged()
    }

    /** 单任务进度 flow。 */
    fun taskFlow(ip: String, uriUuid: String): Flow<DownloadTask?> {
        return tasksFlow(ip).map { tasks -> tasks.find { it.uriUuid == uriUuid } }
    }

    /** 入队一个新任务，自动启动 DownloadService。 */
    fun enqueue(task: DownloadTask) {
        synchronized(lock) {
            val cur = _hostsFlow.value.toMutableMap()
            val hostList = cur[task.ip] ?: HostDownloadList(task.ip, emptyList())
            val newTasks = hostList.tasks.filter { it.uriUuid != task.uriUuid } + task
            cur[task.ip] = hostList.copy(tasks = newTasks)
            _hostsFlow.value = cur
        }
        startDownloadService(task.ip, task.uriUuid)
    }

    /** 取消任务。DownloadService 内部会停止 IO。 */
    fun cancel(ip: String, uriUuid: String) {
        updateTask(ip, uriUuid) { it.copy(state = DownloadState.Canceled) }
    }

    /** 重试：把 Failed/Canceled 任务重置为 Pending 再入队。 */
    fun retry(ip: String, uriUuid: String) {
        val task = synchronized(lock) {
            _hostsFlow.value[ip]?.tasks?.find { it.uriUuid == uriUuid }
        } ?: return
        enqueue(task.copy(state = DownloadState.Pending, receivedBytes = 0, error = null))
    }

    /** 清理已完成的任务。 */
    fun clearCompleted(ip: String) {
        synchronized(lock) {
            val cur = _hostsFlow.value.toMutableMap()
            val hostList = cur[ip] ?: return
            cur[ip] = hostList.copy(tasks = hostList.tasks.filter { it.state != DownloadState.Completed })
            _hostsFlow.value = cur
        }
    }

    /** 内部更新某 ip 下某任务，synchronized + emit。 */
    fun updateTask(ip: String, uriUuid: String, updater: (DownloadTask) -> DownloadTask) {
        synchronized(lock) {
            val cur = _hostsFlow.value.toMutableMap()
            val hostList = cur[ip] ?: return
            val newTasks = hostList.tasks.map { if (it.uriUuid == uriUuid) updater(it) else it }
            cur[ip] = hostList.copy(tasks = newTasks)
            _hostsFlow.value = cur
        }
    }

    /** 在 service 内查询任务详情。 */
    fun findTask(ip: String, uriUuid: String): DownloadTask? {
        return synchronized(lock) {
            _hostsFlow.value[ip]?.tasks?.find { it.uriUuid == uriUuid }
        }
    }

    private fun startDownloadService(ip: String, uriUuid: String) {
        try {
            val intent = Intent(Globals.app, DownloadService::class.java).apply {
                putExtra(EXTRA_IP, ip)
                putExtra(EXTRA_URI_UUID, uriUuid)
            }
            // 启动 foreground service 需要前台 Activity 或在后台白名单。client 页面正在前台时触发没问题。
            // 由于 Globals.app 是 Application Context，Android 8+ 限制下用 startForegroundService。
            Globals.app.startForegroundService(intent)
        } catch (e: Exception) {
            loge { "start DownloadService failed: ${e.message}" }
            updateTask(ip, uriUuid) {
                it.copy(state = DownloadState.Failed, error = "Cannot start download service: ${e.message}")
            }
        }
    }

    companion object {
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_URI_UUID = "extra_uri_uuid"

        /** 通知点击跳回 app 首页（SplashActivity → 雷达页），用户重新点 host 即可恢复下载页进度。 */
        fun buildHomePendingIntent(): android.app.PendingIntent {
            val intent = Intent(Globals.app, SplashActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return android.app.PendingIntent.getActivity(
                Globals.app,
                0,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
