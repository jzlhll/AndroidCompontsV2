package com.au.module_android.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.au.module_android.R
import com.au.module_android.crash.UncaughtExceptionHandlerObj.killAndRestart
import com.au.module_android.log.FileLog
import com.au.module_android.utilsandroid.StatusBarUtils

/**
 * @author allan
 * @date :2024/9/19 14:47
 * @description:
 */
class CrashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("", "Crash Activity create")
        setContentView(R.layout.activity_crash_layout)

        val topSpace = findViewById<View>(R.id.topSpace)
        val lp = topSpace.layoutParams
        lp.height = StatusBarUtils.getStatusBarHeight()
        topSpace.layoutParams = lp

        val errorInfo = Html.fromHtml(
            intent.getStringExtra(UncaughtExceptionHandlerObj.KEY_INFO)
        )
        val version = intent.getStringExtra(UncaughtExceptionHandlerObj.KEY_VERSION)
        val threadInfo = intent.getStringExtra(UncaughtExceptionHandlerObj.KEY_THREAD_INFO)
        val crashText = version + "\n" + threadInfo + "\n" + errorInfo
        findViewById<TextView>(R.id.versionName).text = "appVersion:" + version
        findViewById<TextView>(R.id.tvInfo).text = threadInfo + "\n" + errorInfo

        //格式化时间，作为Log文件名
        FileLog.write(version + "\n" + threadInfo + "\n" + errorInfo)

        findViewById<View>(R.id.restartBtn).setOnClickListener {
            killAndRestart(this)
        }
        findViewById<View>(R.id.copyBtn).setOnClickListener {
            val clipboardManager =
                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("crash-info", crashText)
            clipboardManager.setPrimaryClip(clipData)
            Toast.makeText(this, "Copied!", Toast.LENGTH_SHORT).show()
        }
//
//        findViewById<View>(R.id.clearupBtn).onClick {
//            Thread {
//                runBlocking {
//                    clearAppCache()
//                }
//                //todo clearAppFileDir()
//                killAndRestart(this)
//            }.start()
//        }
    }
}
