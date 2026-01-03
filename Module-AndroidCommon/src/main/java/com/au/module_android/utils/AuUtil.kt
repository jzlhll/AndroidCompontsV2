package com.au.module_android.utils

import android.content.Context
import android.content.Intent
import com.au.module_android.Globals

/**
 * 找到是否已经存在的某个activity
 */
fun findActivity(activityCls: Class<*>): Boolean {
    val found = Globals.activityList.find { it.javaClass == activityCls}
    return found != null
}

/**
 * 找到我自己的启动的activity
 * first是启动的activity的Intent。
 * second如果是true就找到了。false就是没找到。
 */
fun findMyLaunchActivity(context: Context): Pair<Intent, Boolean> {
    val l = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
    val className = l.component?.className
    val found = Globals.activityList.find { className?.contains(it.javaClass.simpleName) == true}
    return l to (found != null)
}