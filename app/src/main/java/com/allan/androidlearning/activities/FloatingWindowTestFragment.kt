package com.allan.androidlearning.activities

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.bindings.BindingFragment
import com.allan.androidlearning.databinding.FragmentFloatingWindowTestBinding
import com.allan.androidlearning.R
import com.au.module_simplepermission.gotoFloatWindowPermission
import com.au.module_simplepermission.hasFloatWindowPermission
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * @author allan
 * @date :2024/7/29
 * @description: 悬浮窗测试页面
 */
@EntryFrgName(priority = 2)
class FloatingWindowTestFragment : BindingFragment<FragmentFloatingWindowTestBinding>() {

    companion object {
        private var floatingView: View? = null
        private var timeTv: TextView? = null
        private val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
        private val frameCallback = object : android.view.Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                timeTv?.text = sdf.format(Date(System.currentTimeMillis()))
                android.view.Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.showBtn.onClick {
            showFloatingWindow()
        }

        binding.hideBtn.onClick {
            removeFloatingWindow()
        }
    }

    private fun showFloatingWindow() {
        val context = requireContext()
        if (!context.hasFloatWindowPermission()) {
            gotoFloatWindowPermission()
            return
        }

        if (floatingView != null) {
            return
        }

        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(context.applicationContext).inflate(R.layout.layout_floating_view, null)
        timeTv = floatingView?.findViewById(R.id.timeTv)

        val layoutParams = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 100
        }

        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            private var isDrag = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDrag = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (!isDrag && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDrag = true
                        }

                        if (isDrag) {
                            layoutParams.x = initialX + dx
                            layoutParams.y = initialY + dy
                            windowManager.updateViewLayout(floatingView, layoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDrag) {
                            v.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })

        floatingView?.onClick {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }

        try {
            windowManager.addView(floatingView, layoutParams)
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeFloatingWindow() {
        if (floatingView != null) {
            try {
                android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
                val windowManager = requireContext().getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                floatingView = null
                timeTv = null
            }
        }
    }
}
