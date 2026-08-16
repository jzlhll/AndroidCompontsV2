package com.au.module_android.crash

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.text.Html
import android.text.TextUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import com.au.module_android.BuildConfig
import com.au.module_android.Globals
import com.au.module_android.log.FileLog
import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile
import com.au.module_android.log.loge
import com.au.module_android.utils.getAppIntent
import com.au.module_android.utils.ignoreError
import com.au.module_android.utils.startActivityFix
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Locale

object UncaughtExceptionHandlerObj : Thread.UncaughtExceptionHandler {
    const val TAG = "UncaughtExpHandObj"
    private var isInit = false
    private var downstreamHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * 设置恢复异常的上报接口。
     */
    var recoveredExceptionReporter:((source:String, exception:Throwable)->Unit)? = null

    override fun uncaughtException(t: Thread, e: Throwable) {
        if (t != Looper.getMainLooper().thread && e is Exception) {
            recoveredExceptionReporter?.invoke("background_thread", e)
            crashAction(t, e)
            return
        }

        ignoreError { FileLog.write(crashLogText(Globals.app, t, e)) }
        val handler = downstreamHandler
        if (handler != null) {
            handler.uncaughtException(t, e)
        } else {
            Process.killProcess(Process.myPid())
            Runtime.getRuntime().exit(-1)
        }
    }

    fun init() {
        if (isInit) return
        isInit = true

        Thread.getDefaultUncaughtExceptionHandler()
            ?.let { if (it !== this) downstreamHandler = it }
        Thread.setDefaultUncaughtExceptionHandler(UncaughtExceptionHandlerObj)

        Handler(Looper.getMainLooper()).post {
            while (true) {
                //主线程异常拦截
                try {
                    Looper.loop()
                } catch (e: Throwable) {
                    if (e !is Exception) throw e

                    logdNoFile(TAG) { "Crashed=======>>>" }
                    logd(TAG) { "uncaughtException2 loop crash: " + e.message }
                    e.printStackTrace()
                    recoveredExceptionReporter?.invoke("main_loop", e)

                    if (!shouldIgnore(e)) {
                        ignoreError {
                            crashAction(Thread.currentThread(), e)
                        }
                        logdNoFile(TAG) { "<<<=======" }
                    }
                }
            }
        }
    }

    private fun shouldIgnore(e: Throwable) : Boolean{
        val msg = e.message ?: return false
        //
        if (msg.contains("android.content.ClipDescription.hasMimeType(java.lang.String)' on a null object reference")) {
            return true
        }

        //小米手机上出现
        if (msg.contains("NullPointerException:Attempt to invoke virtual method 'int android.text.Layout.getLineForOffset(int)' on a null object reference")) {
            return true
        }

        if (msg.contains("android.view.WindowManager\$BadTokenException:Unable to add window")) {
            return true
        }

        if (msg.contains("Activity client record must not be null to execute transaction item: android.app.servertransaction.TransferSplashScreenViewStateItem\n")) {
            return true
        }

        return false
    }

    private fun crashAction(t: Thread, e: Throwable) {
        logd { "crash action $e" }
        Globals.activityList.forEach {
            if(it is FragmentActivity && it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                it.finish()
            }
        }

        if (BuildConfig.DEBUG) {
            loge(TAG) { "startCrashActivity crash activity..." }
            startCrashActivity(Globals.app, t, e)
        } else {
            // release 不把崩溃堆栈摆到用户面前。但落盘不能一起省掉：debug 下写文件
            // 是 CrashActivity 顺带做的，这里不弹页面就得自己写，否则 Android 侧
            // 一点崩溃线索都不剩（尚未接入崩溃上报）。
            // 也不在这里重启进程，启动阶段崩溃会变成无限重启，比停在桌面更糟。
            loge (TAG) { "release build: log crash to file without showing the stack" }
            ignoreError { FileLog.write(crashLogText(Globals.app, t, e)) }
        }
    }

    const val KEY_INFO = "errorInfo"
    const val KEY_VERSION = "version"
    const val KEY_THREAD_INFO = "threadInfo"

    private fun threadInfo(t: Thread) =
        "threadId=${t.id}" + ", name=${t.name}" + ", isMainThread:" + (t.id == Looper.getMainLooper().thread.id)

    /** 与 CrashActivity 落盘的文本保持同一格式，两种构建下的崩溃日志才可比。 */
    private fun crashLogText(context: Context, t: Thread, e: Throwable): String {
        val version = Array(1) { "" }
        val errorInfo = getErrorInfo(context, e, version)
        return version[0] + "\n" + threadInfo(t) + "\n" + Html.fromHtml(errorInfo)
    }

    private fun startCrashActivity(context: Context, t: Thread, e: Throwable) {
        context.startActivityFix(Intent(context, CrashActivity::class.java).also {
            val version = Array(1) {""}
            it.putExtra(KEY_INFO, getErrorInfo(context, e, version))
            it.putExtra(KEY_VERSION, version[0])
            it.putExtra(KEY_THREAD_INFO, threadInfo(t))
        })
    }

    private fun getErrorInfo(context: Context, e: Throwable, version:Array<String>): String {
        //用于存储设备信息
        val mInfo: MutableMap<String, String> = HashMap()
        val pm: PackageManager = context.packageManager
        val info: PackageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
        // 获取版本信息
        val versionName =
            if (TextUtils.isEmpty(info.versionName)) "未设置版本名称" else info.versionName
        version[0] = versionName ?: ""
        val versionCode =
            info.longVersionCode.toString() + ""
        mInfo["versionName"] = versionName ?: ""
        mInfo["versionCode"] = versionCode
        mInfo["brand"] = Build.BRAND
        mInfo["product"] = Build.PRODUCT
        return getErrorStackTrace(mInfo, e)
    }

    /**时间戳转日期*/
    private fun longTimeToStr(time: Long?, pattern: String = "yyyy-MM-dd HH:mm"): String {
        if (time == null) {
            return ""
        }
        val format = SimpleDateFormat(pattern, Locale.getDefault())
        return format.format(time).toString()
    }

    private fun getErrorStackTrace(mInfo: MutableMap<String, String>, e: Throwable): String {
        val stringBuffer = StringBuffer()
        stringBuffer.append(
            "${
                longTimeToStr(
                    System.currentTimeMillis(),
                    "yyyy-MM-dd HH:mm:ss"
                )
            }<br><br>"
        )
        stringBuffer.append("------------Stack---------<br>")
        val stringWriter = StringWriter()
        val writer = PrintWriter(stringWriter)
        e.printStackTrace(writer)
        var cause = e.cause
        while (cause != null) {
            cause.printStackTrace(writer)
            val nextCause = e.cause
            cause = if (nextCause != cause) {
                nextCause
            } else {
                null
            }
        }
        writer.close()
        val string: String = stringWriter.toString()
        stringBuffer.append(string)
        stringBuffer.append("<br><br>------------DeviceInfo---------<br>")
        for ((keyName, value) in mInfo) {
            stringBuffer.append("<b>$keyName：</b>$value<br>")
        }
        return stringBuffer.toString()
    }

    fun killAndRestart(activity: Activity?) {
        val ctx = activity ?: Globals.app
        getAppIntent(ctx, ctx.packageName)?.component?.className?.let {
            activity?.finish()
            ctx.startActivityFix(Intent(ctx.applicationContext, Class.forName(it)))
            Process.killProcess(Process.myPid())
            Runtime.getRuntime().exit(-1) //不能只依赖killProcess
        }
    }
}
