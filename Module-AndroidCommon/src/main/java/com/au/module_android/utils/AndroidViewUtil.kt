package com.au.module_android.utils

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Outline
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import androidx.annotation.Keep
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import com.au.module_android.Globals.app
import kotlin.math.roundToInt

val View.activity: Activity?
    get() {
        val ctx = context
        if (ctx is Activity) {
            return ctx
        }
        if (ctx is ContextWrapper) {
            return ctx.baseContext.asOrNull()
        }
        return null
    }

/**
 * 根据当前界面的状态拿dp，更加合适。因为一些缩放框架会改变density
 */
fun Activity.dp(value:Float):Float {
    return value * this.resources.displayMetrics.density
}

/**
 * 根据当前界面的状态拿dp，更加合适。因为一些缩放框架会改变density
 */
fun Fragment.dp(value:Float):Float {
    return value * requireActivity().resources.displayMetrics.density
}
/**
 * 根据当前界面的状态拿dp，更加合适。因为一些缩放框架会改变density
 */
fun Dialog.dp(value:Float):Float {
    return value * this.context.resources.displayMetrics.density
}

fun Context.dp(value:Float) : Float {
    if (this is Activity) {
        return this.dp(value)
    }

    return value.dp
}

/**
 * 如果能使用Activity.dp或者Fragment.dp则使用另外2个。
 */
val Float.dp:Float
    get() = (this * app.resources.displayMetrics.density)

val Float.dpInt:Int
    get() = (this * app.resources.displayMetrics.density).roundToInt()

/**
 * 如果能使用Activity.dp或者Fragment.dp则使用另外2个。
 */
val Int.dp:Int
    get() = (this.toFloat() * app.resources.displayMetrics.density).roundToInt()

/**
 * 如果能使用Activity.dp或者Fragment.dp则使用另外2个。
 */
val Int.dpFloat:Float
    get() = this.toFloat() * app.resources.displayMetrics.density


fun View.visible() {
    if(visibility != View.VISIBLE) visibility = View.VISIBLE
}

fun View.invisible() {
    if(visibility != View.INVISIBLE) visibility = View.INVISIBLE
}

fun View.gone() {
    if(visibility != View.GONE) visibility = View.GONE
}

fun View.visibleOrGone(visible:Boolean) {
    if (visible) {
        visible()
    } else {
        gone()
    }
}

fun View.visibleOrInvisible(visible:Boolean) {
    if (visible) {
        visible()
    } else {
        invisible()
    }
}

/**
 * 遍历所有子view
 */
fun View?.forEachChild(action: ((View) -> Unit)) {
    if (this == null) {
        return
    }
    action.invoke(this)
    if (this is ViewGroup) {
        this.forEach {
            it.forEachChild(action)
        }
    }
}

/**
 * 通过outlineProvider和setClipToOutline来给View设置圆角。
 */
fun View.setOutlineProviderRoundCorner(radius:Float) {
    val provider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val rect = Rect()
            view.getGlobalVisibleRect(rect)
            val leftMargin = 0
            val topMargin = 0
            val selfRect = Rect(
                leftMargin, topMargin,
                rect.right - rect.left - leftMargin, rect.bottom - rect.top - topMargin
            )
            outline.setRoundRect(selfRect, radius)
        }
    }

    this.outlineProvider = provider
    this.setClipToOutline(true)
}

fun View.findWindow() : Window?{
    var curContext:Context? = context
    var count = 0
    while (curContext is ContextWrapper && count <= 3) {
        if (curContext is Activity) {
            break
        }
        curContext = curContext.baseContext
        count++
    }
    return (curContext as? Activity)?.window
}

/**
 * block: 1表示单击。2表示按压中，会自动每隔20ms回调一次直到结束。
 */
fun View.setOnContinuousTouchEvent(block:(Int)->Unit) {
    this.setOnTouchListener(ContinuousTouchListener(this, block))
}

/**
 * block ， 1表示单击。2表示按压中，会自动每隔20ms回调一次直到结束。
 */
@Keep
class ContinuousTouchListener(private val view: View,
                              private val block:(Int)->Unit) : View.OnTouchListener{
    private var startTime = 0L
    private var onceStart = false
    private val triggerPressRun = Runnable {
        block(2)
        continuousRunDelay()
    }

    private fun continuousRunDelay() {
        view.postDelayed(triggerPressRun, 50)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        event?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startTime = System.currentTimeMillis()
                onceStart = true
                view.removeCallbacks(triggerPressRun)
                view.postDelayed(triggerPressRun, 330)
                return true
            }
            MotionEvent.ACTION_UP -> {
                onceStart = false
                view.removeCallbacks(triggerPressRun)
                // 当抬起时计算按压时间
                val duration = System.currentTimeMillis() - startTime
                if (duration < 300) {
                    block(1) //单击
                    v?.performClick()
                }
            }
        }
        return false
    }
}