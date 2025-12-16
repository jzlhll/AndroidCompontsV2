package com.au.module_android

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.au.module_android.crash.UncaughtExceptionHandlerObj
import com.au.module_android.init.GlobalActivityCallback
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_android.init.optimizeSpTask
import com.au.module_android.screenadapter.ToutiaoScreenAdapter
import com.google.gson.Gson
import kotlinx.serialization.json.Json

/**
 * 自动初始化
 */
class FirstInitial {
    data class FirstInitialConfig(
        val isInitSharedPrefHook:Boolean = false,
        val isInitDarkMode:Boolean = true,
        val isEnableToutiaoScreenAdapter:Boolean = false,
        )

    fun init(context: Application, initCfg:FirstInitialConfig? = null): Application {
        Globals.internalApp = context

        Globals.gson = Gson()
        Globals.kson = Json {
            ignoreUnknownKeys = true      // 后端多给字段也不报错
            encodeDefaults = true         // 输出默认值；关掉可减小体积
            prettyPrint = false           // 日常关；调试可开
            isLenient = true              // 宽松模式，允许非标准 JSON
            explicitNulls = false         // null 字段不主动输出
            //allowTrailingComma = true
            // classDiscriminator = "type" // 多态时的类型标记字段名（见下）
            // namingStrategy = JsonNamingStrategy.SnakeCase // 字段命名转换（版本要求较新）
        }

        UncaughtExceptionHandlerObj.init()

//        DeviceIdentifier.register(context)

        val initConfig = initCfg ?: FirstInitialConfig()
        if(initConfig.isEnableToutiaoScreenAdapter) { ToutiaoScreenAdapter.init(context) }
        if (initConfig.isInitSharedPrefHook) { optimizeSpTask() }

        context.registerActivityLifecycleCallbacks(GlobalActivityCallback())
        ProcessLifecycleOwner.get().lifecycle.addObserver(GlobalBackgroundCallback)

        Globals.firstInitialOnCreateData.setValueSafe(Unit)
        return context
    }
}