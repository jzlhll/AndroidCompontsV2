package com.au.module_android.utils

import android.content.Context
import android.os.Build
import android.os.Build.VERSION
import android.os.Looper
import android.view.WindowManager

val isMainThread: Boolean
    get() = Looper.getMainLooper() == Looper.myLooper()

/**
 * 类型转换
 */
inline fun <reified T> Any?.asOrNull(): T? = this as? T

fun displayRotation(context: Context) : Int {
    val rotation = if (VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display.rotation
    } else {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.rotation
    }
    return rotation
}