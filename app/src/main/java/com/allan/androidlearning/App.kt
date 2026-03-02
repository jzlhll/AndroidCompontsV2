package com.allan.androidlearning

import android.util.Log
import com.allan.androidlearning.crashtest.debugApplicationCreateCrash
import com.allan.androidlearning.crashtest.debugApplicationPostMainThreadCrash
import com.allan.androidlearning.crashtest.debugSubThreadCrash
import com.au.module_android.DarkModeAndLocalesConst
import com.au.module_android.Globals
import com.au.module_android.init.GlobalBackgroundCallback
import com.au.module_androidui.InitApplication
import java.util.Locale

/**
 * @author allan
 * @date :2024/9/29 16:42
 * @description:
 */
class App : InitApplication() {
    override fun initBeforeAttachBaseContext() {
        DarkModeAndLocalesConst.supportLocales = mapOf(
            Locales.LOCALE_JIANTI_CN_KEY to Locale.SIMPLIFIED_CHINESE,
            Locales.LOCALE_FANTI_CN_KEY to Locale.TRADITIONAL_CHINESE,
            Locales.LOCALE_US_KEY to Locale.ENGLISH,
        )
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("alland", "App onCreate")
        GlobalBackgroundCallback.addListener(object : GlobalBackgroundCallback.IBackgroundListener {
            override fun onBackground(isBackground: Boolean) {
                Log.d("alland", "is background $isBackground")
            }
        })
        debugApplicationCreateCrash()
        debugApplicationPostMainThreadCrash()
        debugSubThreadCrash()
    }
}