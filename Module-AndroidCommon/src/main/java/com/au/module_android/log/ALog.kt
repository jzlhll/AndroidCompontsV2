package com.au.module_android.log

import android.util.Log
import kotlin.math.min

/** 控制低于 WARN 级别的 Logcat 输出与落盘，可在 Application 初始化时修改。 */
@Volatile
var logDebugEnabled: Boolean = true

/**
 * 之所以定义这些，是综合考虑了反编译的字节码长度，避免inline过多膨胀
 */
inline fun <THIS : Any> THIS.loge(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    Log.e(tag, log)

    FileLog.write(log)
}

inline fun <THIS : Any> THIS.logeNoFile(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    Log.e(tag, ALogJ.log("E", block(this), tag, javaClass))
}

inline fun <THIS : Any> THIS.logw(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("W", str, tag, javaClass)
    Log.w(tag, log)

    FileLog.write(log)
}

inline fun <THIS : Any> THIS.logwNoFile(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    Log.w(tag, ALogJ.log("W", block(this), tag, javaClass))
}

inline fun <THIS : Any> THIS.logEx(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, throwable: Throwable, crossinline block: (THIS) -> String) {
    val str = block(this)
    val log = ALogJ.log("E", str, tag, javaClass)
    val ex = ALogJ.ex(throwable)

    Log.e(tag, log)
    Log.e(tag, ex)
    FileLog.write(log + "\n" + ex)
}

inline fun <THIS : Any> THIS.logExNoFile(tag:String = LogTag.TAG, javaClass: Class<*> = this.javaClass, throwable: Throwable, crossinline block: (THIS) -> String) {
    val log = ALogJ.log("E", block(this), tag, javaClass)
    val ex = ALogJ.ex(throwable)
    Log.e(tag, log)
    Log.e(tag, ex)
}

inline fun <THIS : Any> THIS.logd(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    logd(tag = LogTag.TAG, javaClass = javaClass, block = block)
}

inline fun <THIS : Any> THIS.logd(tag:String = LogTag.TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (!logDebugEnabled) return
    val log = ALogJ.log("D", block(this), tag, javaClass)
    Log.d(tag, log)
    FileLog.write(log)
}

inline fun <THIS : Any> THIS.logdNoFile(javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    logdNoFile(tag = LogTag.TAG, javaClass = javaClass, block = block)
}

inline fun <THIS : Any> THIS.logdNoFile(tag:String, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (logDebugEnabled) {
        Log.d(tag, ALogJ.log("D", block(this), tag, javaClass))
    }
}

inline fun <THIS : Any> THIS.logt(tag:String = LogTag.TAG, javaClass:Class<*> = this.javaClass, crossinline block: (THIS) -> String) {
    if (logDebugEnabled) {
        val str = block(this)
        val log = ALogJ.logThread(str, javaClass)
        Log.d(tag, log)
    }
}

fun logDebug(s:String) {
    if (logDebugEnabled) Log.d(LogTag.TAG, s)
}

fun logStace(tag:String = LogTag.TAG, s: String) {
    if (!logDebugEnabled) return
    Log.d(tag, "$s...start...")
    val ex = Exception()
    ex.printStackTrace()
    Log.d(tag, "$s...end!")
}

fun logLargeLine(tag:String, str:String) {
    if (!logDebugEnabled) return
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

fun logLargeSize(tag:String, str:String, length : Int = 300) {
    if (!logDebugEnabled) return
    val len = str.length
    var i = 0
    while (i < len) {
        val endIndex = min(i + length, len)
        val log = str.substring(i, endIndex)
        Log.d(tag, log)
        i = endIndex
    }
}
