package com.au.agit

import com.au.module_android.CommonInitApplication
import com.au.module_android.log.logd
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/** 初始化模块基础能力，并提供应用级数据依赖。 */
class AGitApplication : CommonInitApplication() {
    override fun onCreate() {
        super.onCreate()
        startKoin { androidContext(this@AGitApplication); modules(agitModule) }
        logd { "AGit application started" }
    }
}
