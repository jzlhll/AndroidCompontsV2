package com.au.module_android.log

import android.util.Log
import com.au.module_android.log.LogDebug.ALWAYS_FILE_LOG
import com.au.module_android.log.LogDebug.ALWAYS_LOG_DEBUG
import kotlin.math.min

/**
 * 之所以定义这些，是综合考虑了反编译的字节码长度，避免inline过多膨胀
 */
inline fun <THIS : Any> THIS.loge(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    Log.e(tag, log)

    if (ALWAYS_FILE_LOG) FileLog.write(log)
}

inline fun <THIS : Any> THIS.logw(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("W", str, tag, javaClass)
    Log.w(tag, log)

    if (ALWAYS_FILE_LOG) FileLog.write(log)
}

inline fun <THIS : Any> THIS.logEx(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, throwable: Throwable, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    val ex = ALogJ.ex(throwable)

    Log.e(tag, log)
    Log.e(tag, ex)
    if (ALWAYS_FILE_LOG) FileLog.write(log + "\n" + ex)
}

inline fun <THIS : Any> THIS.logd(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (ALWAYS_LOG_DEBUG || ALWAYS_FILE_LOG) {
        val str = block(this)
        val log = ALogJ.log("D", str, javaClass)
        if(ALWAYS_LOG_DEBUG) Log.d(LogTag.TAG, log)

        if (ALWAYS_FILE_LOG) FileLog.write(log)
    }
}

inline fun <THIS : Any> THIS.logd(tag:String = LogTag.TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (ALWAYS_LOG_DEBUG || ALWAYS_FILE_LOG) {
        val str = block(this)
        val log = ALogJ.log("D", str, tag, javaClass)
        if(ALWAYS_LOG_DEBUG) Log.d(tag, log)

        if (ALWAYS_FILE_LOG) FileLog.write(log)
    }
}

inline fun <THIS : Any> THIS.logdNoFile(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (ALWAYS_LOG_DEBUG) {
        val str = block(this)
        val log = ALogJ.log("D", str, javaClass)
        Log.d(LogTag.TAG, log)
    }
}

inline fun <THIS : Any> THIS.logdNoFile(tag:String = "", javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (ALWAYS_LOG_DEBUG) {
        val str = block(this)
        val log = ALogJ.log("D", str, tag, javaClass)
        Log.d(LogTag.TAG, log)
    }
}

inline fun <THIS : Any> THIS.logt(tag:String = LogTag.TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (ALWAYS_LOG_DEBUG) {
        val str = block(this)
        val log = ALogJ.logThread(str, javaClass)
        Log.d(tag, log)
    }
}

fun logDebug(s:String) {
    Log.d(LogTag.TAG, s)
}

fun logStace(tag:String = LogTag.TAG, s: String) {
    Log.d(tag, "$s...start...")
    val ex = Exception()
    ex.printStackTrace()
    Log.d(tag, "$s...end!")
}

fun logLargeLine(tag:String, str:String) {
    val len = str.length
    val maxLine = 300
    var i = 0
    while (i < len) {
        var lineIndex = str.indexOf("\n", i + maxLine)
        if (lineIndex == -1) {
            lineIndex = len
        }
        val log = str.substring(i, min(lineIndex, len))
        Log.d(tag, log)
        i = lineIndex + 1
    }
}