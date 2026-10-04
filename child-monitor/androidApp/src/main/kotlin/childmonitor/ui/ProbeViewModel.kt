package childmonitor.ui

import androidx.lifecycle.ViewModel
import childmonitor.app.MonitorApplication
import childmonitor.platform.CameraCapabilityProbe

/** 探针仅在当前页面由用户主动启动，重建页面和恢复导航均不自动采集。 */
class ProbeViewModel(app: MonitorApplication) : ViewModel() {
    val probe = CameraCapabilityProbe(app, app.probePermit)
    override fun onCleared() { probe.close() }
}
