package com.au.module_androidui

import android.app.Activity
import android.os.Bundle
import com.au.module_android.CommonInitApplication
import com.au.module_androidui.ui.ActivityFrameRateMonitor
import com.au.module_androidui.ui.monitorActivityFrameRate
import com.au.module_androidui.ui.monitorActivityLoadTime

open class InitApplication : CommonInitApplication() {
    override fun config(): FirstInitialConfig {
        return FirstInitialConfig(
            isDebug = BuildConfig.DEBUG,
            hasFileDebug = BuildConfig.DEBUG
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (ENABLE_MONITOR) {
            registerActivityLifecycleCallbacks(MonitorActivityCallback())
        }
    }

    class MonitorActivityCallback : ActivityLifecycleCallbacks {

        // Activity帧率监控器缓存。
        private val frameRateMonitors = hashMapOf<Activity, ActivityFrameRateMonitor>()

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            monitorActivityLoadTime(activity)
            if (ENABLE_ACTIVITY_FRAME_RATE_MONITOR) {
                frameRateMonitors[activity] = monitorActivityFrameRate(activity)
            }
        }

        override fun onActivityDestroyed(activity: Activity) {
            frameRateMonitors.remove(activity)?.stop()
        }

        override fun onActivityPaused(activity: Activity) {
            frameRateMonitors[activity]?.stop()
        }

        override fun onActivityResumed(activity: Activity) {
            frameRateMonitors[activity]?.start()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }
    }

    private companion object {
        // 默认关闭Activity帧率日志。
        private const val ENABLE_ACTIVITY_FRAME_RATE_MONITOR = false
        private const val ENABLE_MONITOR = false
    }
}