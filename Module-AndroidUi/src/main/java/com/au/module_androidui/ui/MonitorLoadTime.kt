package com.au.module_androidui.ui

import android.R
import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import com.au.module_android.init.GlobalActivityCallback
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.asOrNull

// 监控Activity显示耗时
fun monitorActivityLoadTime(activity: Activity) {
    val name = activity.asOrNull<FragmentShellActivity>()?.fragmentClass?.simpleName ?: activity.javaClass.simpleName
    val startTime = System.currentTimeMillis()
    val contentView = activity.findViewById<View>(R.id.content)

    contentView.viewTreeObserver.addOnGlobalLayoutListener(
        object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                contentView.viewTreeObserver.removeOnGlobalLayoutListener(this)

                contentView.post {
                    val loadTime = System.currentTimeMillis() - startTime
                    logdNoFile(GlobalActivityCallback::class.java) {
                        "$name loadTime: $loadTime ms"
                    }
                }
            }
        }
    )
}

// 监控Fragment显示耗时
fun monitorFragmentLoadTime(fragment: Fragment) {
    val name = fragment.javaClass.simpleName
    fragment.view?.let { view ->
        val startTime = System.currentTimeMillis()

        view.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)

                    view.post {
                        val loadTime = System.currentTimeMillis() - startTime
                        logdNoFile(GlobalActivityCallback::class.java) {
                            "$name loadTime: $loadTime ms"
                        }
                    }
                }
            }
        )
    }
}
    