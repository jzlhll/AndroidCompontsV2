package com.allan.mydroid.views.send

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import eightbitlab.com.blurview.BlurView
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/** 只从左侧手柄启动长按拖动，按钮区域保持自身点击行为，拖动或滑动结束时取消单击。 */
class PreviewControlsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : BlurView(context, attrs) {
    var dragHandle: View? = null
        set(value) {
            field = value
            requestLayout()
        }
    var onTouchStarted: (() -> Unit)? = null
    var onDrag: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onTouchFinished: ((dragged: Boolean) -> Unit)? = null
    val isTouchActive: Boolean
        get() = touchActive

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val dragHoldDuration = 200.milliseconds
    private val dragBounds = Rect()
    private val longPressBounds = Rect()
    private var touchActive = false
    private var dragAllowed = false
    private var dragging = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val beginDrag = Runnable {
        if (touchActive && dragAllowed && !dragging && isShown) {
            downX = lastRawX
            downY = lastRawY
            dragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    init {
        isClickable = true
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateDragBounds()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            finishTouch(false)
            touchActive = true
            downX = event.rawX
            downY = event.rawY
            lastRawX = downX
            lastRawY = downY
            moved = false
            dragAllowed = updateDragBounds() && dragHandle?.isShown == true &&
                dragBounds.contains(event.x.toInt(), event.y.toInt())
            onTouchStarted?.invoke()
            if (dragAllowed) {
                longPressBounds.set(dragBounds)
                longPressBounds.inset(-touchSlop, -touchSlop)
                parent?.requestDisallowInterceptTouchEvent(true)
                postDelayed(beginDrag, dragHoldDuration.inWholeMilliseconds)
            }
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
            lastRawX = event.rawX
            lastRawY = event.rawY
            // 手柄内的轻微晃动不取消长按，离开容错区域后才放弃本次拖动。
            if (dragAllowed && !dragging) {
                if (event.eventTime - event.downTime >= dragHoldDuration.inWholeMilliseconds) {
                    removeCallbacks(beginDrag)
                    beginDrag.run()
                } else if (!longPressBounds.contains(event.x.toInt(), event.y.toInt())) {
                    dragAllowed = false
                    removeCallbacks(beginDrag)
                }
            }
            val dx = lastRawX - downX
            val dy = lastRawY - downY
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
        return dragAllowed || super.onInterceptTouchEvent(event)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != View.VISIBLE) finishTouch(false)
    }

    override fun onDetachedFromWindow() {
        finishTouch(false)
        super.onDetachedFromWindow()
    }

    private fun updateDragBounds(): Boolean {
        val handle = dragHandle
        if (handle == null || handle.width == 0 || height == 0) {
            dragBounds.setEmpty()
            systemGestureExclusionRects = emptyList()
            return false
        }
        handle.getDrawingRect(dragBounds)
        offsetDescendantRectToMyCoords(handle, dragBounds)
        dragBounds.left = 0
        dragBounds.top = 0
        dragBounds.bottom = height
        // 手柄靠近屏幕边缘时，避免系统返回手势截走拖动。
        systemGestureExclusionRects = listOf(Rect(dragBounds))
        return true
    }

    private fun finishTouch(completed: Boolean) {
        if (!touchActive) return
        removeCallbacks(beginDrag)
        val completedDrag = completed && dragging
        touchActive = false
        dragAllowed = false
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
        onTouchFinished?.invoke(completedDrag)
    }
}
