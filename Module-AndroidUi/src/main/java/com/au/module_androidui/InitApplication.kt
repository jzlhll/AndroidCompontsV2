package com.au.module_androidui

import com.au.module_android.CommonInitApplication
import com.au.module_android.restoreAppNightMode

open class InitApplication : CommonInitApplication() {
    override fun onCreate() {
        restoreAppNightMode(this)
        super.onCreate()
    }
}
