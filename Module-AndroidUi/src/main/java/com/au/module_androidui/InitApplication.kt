package com.au.module_androidui

import android.app.Activity
import android.os.Bundle
import com.au.module_android.CommonInitApplication
import com.au.module_androidui.ui.monitorActivityLoadTime

open class InitApplication : CommonInitApplication() {
    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(MonitorActivityCallback())
    }

    class MonitorActivityCallback : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            monitorActivityLoadTime(activity)
        }

        override fun onActivityDestroyed(activity: Activity) {
        }

        override fun onActivityPaused(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityStopped(activity: Activity) {
        }
    }
}