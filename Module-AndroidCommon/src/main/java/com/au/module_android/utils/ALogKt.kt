package com.au.module_android.utils

import android.util.Log
import com.au.module_android.BuildConfig

const val TAG:String = "au"
var ALWAYS_FILE_LOG = BuildConfig.ENABLE_FILE_LOG_DEFAULT

/**
 * 之所以定义这些，是综合考虑了反编译的字节码长度，避免inline过多膨胀
 */

inline fun <THIS : Any> THIS.loge(tag:String = TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    Log.e(tag, log)

    if (ALWAYS_FILE_LOG) FileLog.write(log)
}

inline fun <THIS : Any> THIS.logw(tag:String = TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("W", str, tag, javaClass)
    Log.w(tag, log)

    if (ALWAYS_FILE_LOG) FileLog.write(log)
}

inline fun <THIS : Any> THIS.logEx(tag:String = TAG, javaClass: Class<*> = this.javaClass, throwable: Throwable, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    val ex = ALogJ.ex(throwable)

    Log.e(tag, log)
    Log.e(tag, ex)
    if (ALWAYS_FILE_LOG) FileLog.write(log + "\n" + ex)
}

inline fun <THIS : Any> THIS.logd(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (BuildConfig.ENABLE_LOGCAT || ALWAYS_FILE_LOG) {
        val str = block(this)
        val log = ALogJ.log("D", str, javaClass)
        if(BuildConfig.ENABLE_LOGCAT) Log.d(TAG, log)

        if (ALWAYS_FILE_LOG) FileLog.write(log)
    }
}

inline fun <THIS : Any> THIS.logd(tag:String = TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (BuildConfig.ENABLE_LOGCAT || ALWAYS_FILE_LOG) {
        val str = block(this)
        val log = ALogJ.log("D", str, tag, javaClass)
        if(BuildConfig.ENABLE_LOGCAT) Log.d(tag, log)

        if (ALWAYS_FILE_LOG) FileLog.write(log)
    }
}

inline fun <THIS : Any> THIS.logdNoFile(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (BuildConfig.ENABLE_LOGCAT) {
        val str = block(this)
        val log = ALogJ.log("D", str, javaClass)
        Log.d(TAG, log)
    }
}

inline fun <THIS : Any> THIS.logdNoFile(tag:String = "", javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (BuildConfig.ENABLE_LOGCAT) {
        val str = block(this)
        val log = ALogJ.log("D", str, tag, javaClass)
        Log.d(TAG, log)
    }
}

inline fun <THIS : Any> THIS.logt(tag:String = TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (BuildConfig.ENABLE_LOGCAT) {
        val str = block(this)
        val log = ALogJ.logThread(str, javaClass)
        Log.d(tag, log)
    }
}

fun logDebug(s:String) {
    if (BuildConfig.DEBUG) {
        Log.d(TAG, s)
    }
}

fun logStace(tag:String = TAG, s: String) {
    Log.d(tag, "$s...start...")
    val ex = Exception()
    ex.printStackTrace()
    Log.d(tag, "$s...end!")
}