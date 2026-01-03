package com.au.module_android.utilsandroid

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.KeyguardManager
import android.content.Context
import android.os.Process

/**
 * App是否运行在前台
 * @param context Context
 */
fun isAppRunningForeground(context: Context): Boolean {
    var isForeground = false
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runningProcesses = am.runningAppProcesses
    for (processInfo in runningProcesses) {
        if (processInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            for (activeProcess in processInfo.pkgList) {
                if (activeProcess == context.packageName) {
                    isForeground = true
                }
            }
        }
    }
    return isForeground
}

/**
 * App是否运行在前台 方法2
 * @param context Context
 */
fun isAppRunningForeground2(context: Context) : Boolean {
    val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (keyguardManager.isKeyguardLocked) {
        return false // Screen is off or lock screen is showing
    }
    val pid = Process.myPid()
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val appProcesses = am.runningAppProcesses
    if (appProcesses != null) {
        for (process in appProcesses) {
            if (process.pid == pid) {
                return process.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            }
        }
    }
    return false
}