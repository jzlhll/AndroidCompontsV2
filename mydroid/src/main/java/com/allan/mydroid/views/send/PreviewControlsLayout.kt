package com.allan.mydroid.views.send

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import eightbitlab.com.blurview.BlurView
import kotlin.math.abs

/** 统一接收整组按钮的长按拖动手势，拖动或滑动结束时取消子按钮的单击。 */
class PreviewControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BlurView(context, attrs) {
    var onTouchStarted: (() -> Unit)? = null
    var onDrag: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onTouchFinished: ((dragged: Boolean) -> Unit)? = null
    val isTouchActive: Boolean
        get() = touchActive

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchActive = false
    private var dragging = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private val beginDrag = Runnable {
        if (touchActive && isShown) {
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    init {
        isClickable = true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            finishTouch(false)
            touchActive = true
            downX = event.rawX
            downY = event.rawY
            moved = false
            onTouchStarted?.invoke()
            postDelayed(beginDrag, ViewConfiguration.getLongPressTimeout().toLong())
        } else if (!touchActive) {
            return true
        }

        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            finishTouch(false)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            val dx = event.rawX - downX
            val dy = event.rawY - downY
            if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
            if (dragging) onDrag?.invoke(dx, dy)
        }

        // 长按或滑动后的松手统一转为 CANCEL，确保子按钮不会收到触发单击的 UP。
        if (event.actionMasked == MotionEvent.ACTION_UP && (dragging || moved)) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
        } else {
            super.dispatchTouchEvent(event)
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            finishTouch(event.actionMasked == MotionEvent.ACTION_UP)
        }
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return dragging || super.onInterceptTouchEvent(event)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) finishTouch(false)
    }

    override fun onDetachedFromWindow() {
        finishTouch(false)
        super.onDetachedFromWindow()
    }

    private fun finishTouch(completed: Boolean) {
        if (!touchActive) return
        removeCallbacks(beginDrag)
        val completedDrag = completed && dragging
        touchActive = false
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
        onTouchFinished?.invoke(completedDrag)
    }
}
