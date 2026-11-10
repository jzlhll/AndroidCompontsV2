package com.au.module_androidui.widget

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.*
import android.widget.ImageView
import com.au.module_android.utils.renderToBitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 全屏 Bitmap 预览，顶层挂载和系统栏切换由 AbsFullScreenPreviewView 统一管理。
 *
 * show 会将没有父容器的源 View 临时加入自身；调用方加载完图片并调用 ready 后，
 * 将源 View 离屏绘制为 Bitmap，再移除源 View，以 Bitmap 提供缩放、拖动和单击关闭。
 * 源 View 的图片加载请求仍由调用方负责清理。
 *
 * Bitmap 准备完成后才显示预览并隐藏系统栏，onHide 负责取消待完成的截图并回收 Bitmap。
 */
class FullScreenBitmapPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AbsFullScreenPreviewView(context, attrs, defStyleAttr) {
    private val previewImg = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = INVISIBLE
    }
    private val touchHelper = FullScreenPreviewTouchHelper(context)
    private var sourceView: View? = null
    private var previewBitmap: Bitmap? = null
    private var previewRequestId = 0L

    init {
        setBackgroundColor(PREVIEW_BG_COLOR)
        isClickable = true
        isFocusable = true
        clipChildren = false
        clipToPadding = false
        visibility = INVISIBLE
        setOnTouchListener(touchHelper)
        addView(
            previewImg,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }

    /**
     * 展示全屏预览。
     * @param view 用于离屏截图的源 View。
     * @param onImageReady 调用方在源 View 图片准备完成后调用 ready。
     */
    fun show(activity: Activity, view: View, onImageReady: (() -> Unit) -> Unit) {
        if (!attachToContent(activity)) return
        val requestId = ++previewRequestId
        clearPreviewBitmap()
        clearSourceView()
        previewImg.setImageDrawable(null)
        previewImg.visibility = INVISIBLE
        touchHelper.resetTarget(previewImg)
        touchHelper.onSingleClick = { hide(activity) }
        visibility = INVISIBLE

        sourceView = view
        view.visibility = INVISIBLE
        addView(
            view,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )

        onImageReady {
            post {
                showSourceBitmapIfCurrent(activity, requestId, view)
            }
        }
    }

    override fun onHide() {
        previewRequestId++
        touchHelper.resetTarget(null)
        touchHelper.onSingleClick = null
        previewImg.setImageDrawable(null)
        previewImg.visibility = INVISIBLE
        clearPreviewBitmap()
        clearSourceView()
    }

    private fun showSourceBitmapIfCurrent(activity: Activity, requestId: Long, view: View) {
        if (requestId != previewRequestId || sourceView !== view) {
            return
        }
        val bitmap = view.renderToBitmap() ?: return
        if (requestId != previewRequestId || sourceView !== view) {
            bitmap.recycle()
            return
        }
        clearSourceView()
        previewBitmap?.recycle()
        previewBitmap = bitmap
        previewImg.setImageBitmap(bitmap)
        previewImg.visibility = VISIBLE
        showPreview(activity)
        previewImg.post {
            if (parent != null) {
                touchHelper.resetTarget(previewImg)
            }
        }
    }

    private fun clearSourceView() {
        sourceView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        sourceView = null
    }

    private fun clearPreviewBitmap() {
        previewBitmap?.recycle()
        previewBitmap = null
    }

    private class FullScreenPreviewTouchHelper(context: Context) : OnTouchListener {
        var onSingleClick: (() -> Unit)? = null
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
        private var target: View? = null
        private var activePointerId = MotionEvent.INVALID_POINTER_ID
        private var downX = 0f
        private var downY = 0f
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var isDragging = false
        private var hasMultiPointer = false
        private var pendingScale = 1f
        private var pendingTranslationX = 0f
        private var pendingTranslationY = 0f
        private var applyTransformScheduled = false

        fun resetTarget(view: View?) {
            target?.resetTransform()
            target = view
            view?.resetTransform()
            pendingScale = 1f
            pendingTranslationX = 0f
            pendingTranslationY = 0f
            applyTransformScheduled = false
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            scaleGestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    downX = event.x
                    downY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
                    isDragging = false
                    hasMultiPointer = false
                    return true
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    hasMultiPointer = true
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) {
                        return true
                    }
                    val x = event.getX(pointerIndex)
                    val y = event.getY(pointerIndex)
                    if (scaleGestureDetector.isInProgress) {
                        lastTouchX = x
                        lastTouchY = y
                        return true
                    }
                    if (!isDragging) {
                        isDragging = abs(x - downX) > touchSlop || abs(y - downY) > touchSlop
                    }
                    if (isDragging && pendingScale > MIN_SCALE) {
                        pendingTranslationX += x - lastTouchX
                        pendingTranslationY += y - lastTouchY
                        fixPendingBounds()
                        scheduleApplyTransform()
                    }
                    lastTouchX = x
                    lastTouchY = y
                    return true
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    handlePointerUp(event)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging && !hasMultiPointer) {
                        onSingleClick?.invoke()
                    }
                    clearTouchState()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    clearTouchState()
                    return true
                }
            }
            return true
        }

        private fun handlePointerUp(event: MotionEvent) {
            val upIndex = event.actionIndex
            val upId = event.getPointerId(upIndex)
            if (upId != activePointerId) {
                return
            }
            val newIndex = if (upIndex == 0) 1 else 0
            if (newIndex >= event.pointerCount) {
                return
            }
            activePointerId = event.getPointerId(newIndex)
            lastTouchX = event.getX(newIndex)
            lastTouchY = event.getY(newIndex)
        }

        private fun clearTouchState() {
            activePointerId = MotionEvent.INVALID_POINTER_ID
            isDragging = false
            hasMultiPointer = false
        }

        private fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float) {
            val view = target ?: return
            val oldScale = pendingScale
            val newScale = min(max(oldScale * scaleFactor, MIN_SCALE), MAX_SCALE)
            if (oldScale <= 0f || oldScale == newScale) {
                return
            }
            val localFocusX = (focusX - view.left - pendingTranslationX) / oldScale
            val localFocusY = (focusY - view.top - pendingTranslationY) / oldScale
            pendingScale = newScale
            pendingTranslationX = focusX - view.left - localFocusX * newScale
            pendingTranslationY = focusY - view.top - localFocusY * newScale
            fixPendingBounds()
            scheduleApplyTransform()
        }

        private fun fixPendingBounds() {
            val view = target ?: return
            val parent = view.parent as? View ?: return
            val parentWidth = parent.width.toFloat()
            val parentHeight = parent.height.toFloat()
            val viewWidth = view.width * pendingScale
            val viewHeight = view.height * pendingScale
            if (parentWidth <= 0f || parentHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
                return
            }

            val leftAlignedTranslationX = -view.left.toFloat()
            val rightAlignedTranslationX = parentWidth - view.left - viewWidth
            pendingTranslationX = limitInRange(
                pendingTranslationX,
                min(leftAlignedTranslationX, rightAlignedTranslationX),
                max(leftAlignedTranslationX, rightAlignedTranslationX),
            )

            val topAlignedTranslationY = -view.top.toFloat()
            val bottomAlignedTranslationY = parentHeight - view.top - viewHeight
            pendingTranslationY = limitInRange(
                pendingTranslationY,
                min(topAlignedTranslationY, bottomAlignedTranslationY),
                max(topAlignedTranslationY, bottomAlignedTranslationY),
            )
        }

        private fun limitInRange(value: Float, minValue: Float, maxValue: Float): Float {
            return min(max(value, minValue), maxValue)
        }

        private fun scheduleApplyTransform() {
            val view = target ?: return
            if (applyTransformScheduled) {
                return
            }
            applyTransformScheduled = true
            view.postOnAnimation {
                val cur = target ?: return@postOnAnimation
                applyTransformScheduled = false
                cur.scaleX = pendingScale
                cur.scaleY = pendingScale
                cur.translationX = pendingTranslationX
                cur.translationY = pendingTranslationY
            }
        }

        private fun View.resetTransform() {
            pivotX = 0f
            pivotY = 0f
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }

        private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                applyScale(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }

        companion object {
            private const val MIN_SCALE = 1f
            private const val MAX_SCALE = 4f
        }
    }

    companion object {
        private val PREVIEW_BG_COLOR = Color.rgb(252, 250, 247)
    }
}
