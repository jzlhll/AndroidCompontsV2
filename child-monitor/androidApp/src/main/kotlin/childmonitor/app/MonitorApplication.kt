package childmonitor.app

import android.app.Application
import android.os.SystemClock
import childmonitor.data.repository.MonitorRepository
import childmonitor.domain.MonitorRuntime
import childmonitor.platform.AndroidCaptureAdapter
import childmonitor.platform.AndroidReminderPlayer
import childmonitor.platform.RuntimeClock
import childmonitor.storage.AndroidPersistence
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Semaphore

/** 持有唯一持久化容器和 Runtime，相机平台绑定在首页可撤销地注册。 */
class MonitorApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val playbackRegistry = childmonitor.platform.AndroidPlaybackRegistry()
    val probePermit = Semaphore(1)
    val persistence by lazy { AndroidPersistence(this, applicationScope) }
    val capture by lazy { AndroidCaptureAdapter(this, persistence.files, applicationScope, probePermit) }
    val runtime by lazy {
        MonitorRuntime(persistence.files, object : RuntimeClock {
            override fun monotonicUs() = SystemClock.elapsedRealtimeNanos() / 1_000
            override fun wallUs() = System.currentTimeMillis() * 1_000
            override fun timezoneId() = TimeZone.getDefault().id
            override fun newId() = UUID.randomUUID().toString()
        }, persistence.settings, MonitorRepository(persistence.dao), capture,
            AndroidReminderPlayer(this), playbackRegistry, Dispatchers.Default)
    }
    override fun onTerminate() {
        runtime.close()
        applicationScope.cancel()
        super.onTerminate()
    }
}
