package com.allan.audroid

import com.au.logsystem.DefaultActivitiesFollowCallback
import com.au.module_android.Globals
import com.au.module_android.utils.launchOnIOThread
import com.au.module_androidui.InitApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

class App : InitApplication() {

    override fun onCreate() {
        super.onCreate()
        val globalModule = module {
            //app架构单例
            single { Globals.mainScope }        //两个单例的scope
            single { Globals.backgroundScope }  //两个单例的scope
        }
        startKoin {
            modules(globalModule)
            androidContext(this@App)
        }

        //日志按钮显示监听
        registerActivityLifecycleCallbacks(DefaultActivitiesFollowCallback())

        //一上来直接强制移除所有临时文件夹。
        Globals.mainScope.launchOnIOThread {
        }
    }
}