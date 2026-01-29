package com.au.module_android

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.EmptySuper
import androidx.lifecycle.ProcessLifecycleOwner
import com.au.module_android.crash.UncaughtExceptionHandlerObj
import com.au.module_android.init.GlobalActivityCallback
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.init.optimizeSpTask
import com.au.module_android.log.LogTag
import com.au.module_android.logdebug.LogDebugInit
import com.au.module_android.screenadapter.ToutiaoScreenAdapter

/**
 * @author allan
 * @date :2023/11/7 14:32
 * @description: 使用InitApplication做为基础的application父类或者直接使用
 */
open class CommonInitApplication : Application() {
    data class FirstInitialConfig(
        val isInitSharedPrefHook:Boolean = false,
        val isEnableToutiaoScreenAdapter:Boolean = false,
        /**
         * 是否是debug模式，会打印日志。让logdNoFile等能够打印。
         */
        val isDebug: Boolean = false,
        /**
         * 是否有文件日志打印
         */
        val hasFileDebug: Boolean = false,
        /**
         * 日志tag
         */
        val tag : String? = null
    )

    open fun config() : FirstInitialConfig? = null

    protected fun init(context: Application): Application {
        UncaughtExceptionHandlerObj.init()

//        DeviceIdentifier.register(context)

        val initConfig = config() ?: FirstInitialConfig()
        if(initConfig.isEnableToutiaoScreenAdapter) { ToutiaoScreenAdapter.init(context) }
        if(initConfig.isInitSharedPrefHook) { optimizeSpTask() }
        if(initConfig.tag != null) LogTag.TAG = initConfig.tag
        if(initConfig.isDebug) LogDebugInit().initAsDebug(true, initConfig.hasFileDebug)

        context.registerActivityLifecycleCallbacks(GlobalActivityCallback())
        ProcessLifecycleOwner.get().lifecycle.addObserver(GlobalBackgroundCallback)

        Globals.firstInitialOnCreateData.setValueSafe(Unit)
        return context
    }

    override fun onCreate() {
        Globals.internalApp = this
        if (BuildConfig.DEBUG) {
            System.setProperty("kotlinx.coroutines.debug", "on")
        }
        super.onCreate()
        init(this)
        DarkModeAndLocalesConst.appOnCreated(this)
    }

    final override fun attachBaseContext(base: Context?) {
        initBeforeAttachBaseContext()
        super.attachBaseContext(DarkModeAndLocalesConst.appAttachBaseContext(base))
    }

    @EmptySuper
    open fun initBeforeAttachBaseContext() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        DarkModeAndLocalesConst.appOnConfigurationChanged(this, newConfig)
    }

    override fun getResources() : Resources {
        if (BuildConfig.SUPPORT_LOCALES || BuildConfig.SUPPORT_DARKMODE) {
            //会影响Activity的获取。必须将处理后的resource替换掉。
            return DarkModeAndLocalesConst.themedContext?.resources ?: return super.getResources()
        }
        return super.getResources()
    }
}