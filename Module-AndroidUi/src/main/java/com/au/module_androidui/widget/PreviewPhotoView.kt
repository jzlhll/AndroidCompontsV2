package com.au.module_androidui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import com.google.android.material.imageview.ShapeableImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 支持预览图片缩放和平移的 ImageView。
 */
class PreviewPhotoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ShapeableImageView(context, attrs, defStyleAttr) {

    // 图片基础适配矩阵。
    private val baseMatrix = Matrix()

    // 用户缩放和平移矩阵。
    private val suppMatrix = Matrix()

    // 最终应用到 ImageView 的矩阵。
    private val drawMatrix = Matrix()

    // 临时计算图片显示区域。
    private val displayRect = RectF()

    // 临时计算 drawable 原始区域。
    private val drawableRect = RectF()

    // 当前矩阵数值缓存。
    private val matrixValues = FloatArray(9)

    // 触摸系统判定滑动的阈值。
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // 双指缩放手势识别器。
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

    // 回弹动画。
    private var snapBackAnimator: ValueAnimator? = null

    // 上一次触摸点横坐标。
    private var lastTouchX = 0f

    // 上一次触摸点纵坐标。
    private var lastTouchY = 0f

    // 按下时横坐标。
    private var downX = 0f

    // 按下时纵坐标。
    private var downY = 0f

    // 当前活跃手指 ID。
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // 是否已经进入拖拽。
    private var isDragging = false

    // 是否等待下一次布局后重置矩阵。
    private var pendingReset = false

    // 是否锁定为 Matrix 绘制模式。
    private var matrixScaleTypeLocked = false

    init {
        super.setScaleType(ScaleType.MATRIX)
        matrixScaleTypeLocked = true
    }

    /**
     * 重置缩放和平移状态。
     */
    fun resetZoom() {
        snapBackAnimator?.cancel()
        suppMatrix.reset()
        updateBaseMatrix()
        applyMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        scheduleReset()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        scheduleReset()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        scheduleReset()
    }

    override fun setScaleType(scaleType: ScaleType?) {
        if (matrixScaleTypeLocked) {
            super.setScaleType(ScaleType.MATRIX)
            applyMatrix()
        } else {
            super.setScaleType(scaleType)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (drawable == null) {
            return super.onTouchEvent(event)
        }

        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                snapBackAnimator?.cancel()
                activePointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = false
                if (isZoomed()) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleGestureDetector.isInProgress) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true

                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                val dx = x - lastTouchX
                val dy = y - lastTouchY

                if (!isDragging) {
                    isDragging = abs(x - downX) > touchSlop || abs(y - downY) > touchSlop
                }

                if (isDragging && isZoomed()) {
                    val shouldBlockPager = shouldBlockViewPager(dx, dy)
                    parent?.requestDisallowInterceptTouchEvent(shouldBlockPager)
                    if (shouldBlockPager) {
                        suppMatrix.postTranslate(dx, dy)
                        fixTranslation()
                        applyMatrix()
                    }
                }

                lastTouchX = x
                lastTouchY = y
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                handlePointerUp(event)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                animateSnapBack()
                return true
            }
        }

        return true
    }

    override fun onDetachedFromWindow() {
        snapBackAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    // 等待图片加载或布局完成后重置，避免 Glide 异步设置 drawable 时矩阵失效。
    private fun scheduleReset() {
        pendingReset = true
        post {
            if (!pendingReset) return@post
            pendingReset = false
            resetZoom()
        }
    }

    // 根据当前 drawable 和控件尺寸计算 fitCenter 基础矩阵。
    private fun updateBaseMatrix() {
        val cur = drawable ?: return
        val viewWidth = width - paddingLeft - paddingRight
        val viewHeight = height - paddingTop - paddingBottom
        if (viewWidth <= 0 || viewHeight <= 0 || cur.intrinsicWidth <= 0 || cur.intrinsicHeight <= 0) {
            return
        }

        drawableRect.set(0f, 0f, cur.intrinsicWidth.toFloat(), cur.intrinsicHeight.toFloat())
        displayRect.set(
            paddingLeft.toFloat(),
            paddingTop.toFloat(),
            (width - paddingRight).toFloat(),
            (height - paddingBottom).toFloat(),
        )
        baseMatrix.reset()
        baseMatrix.setRectToRect(drawableRect, displayRect, Matrix.ScaleToFit.CENTER)
    }

    // 应用基础矩阵与用户矩阵。
    private fun applyMatrix() {
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        imageMatrix = drawMatrix
    }

    // 修正位移，保证图片不会脱离预览区域。
    private fun fixTranslation() {
        val rect = getDisplayRect() ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        var deltaX = 0f
        var deltaY = 0f

        deltaX = if (rect.width() <= viewWidth) {
            viewWidth / 2f - rect.centerX()
        } else if (rect.left > 0f) {
            -rect.left
        } else if (rect.right < viewWidth) {
            viewWidth - rect.right
        } else {
            0f
        }

        deltaY = if (rect.height() <= viewHeight) {
            viewHeight / 2f - rect.centerY()
        } else if (rect.top > 0f) {
            -rect.top
        } else if (rect.bottom < viewHeight) {
            viewHeight - rect.bottom
        } else {
            0f
        }

        if (deltaX != 0f || deltaY != 0f) {
            suppMatrix.postTranslate(deltaX, deltaY)
        }
    }

    // 松手后回弹到合法显示区域。
    private fun animateSnapBack() {
        val startValues = FloatArray(9)
        suppMatrix.getValues(startValues)
        val startTranslateX = startValues[Matrix.MTRANS_X]
        val startTranslateY = startValues[Matrix.MTRANS_Y]

        fixTranslation()
        val endValues = FloatArray(9)
        suppMatrix.getValues(endValues)
        val endTranslateX = endValues[Matrix.MTRANS_X]
        val endTranslateY = endValues[Matrix.MTRANS_Y]

        if (startTranslateX == endTranslateX && startTranslateY == endTranslateY) {
            applyMatrix()
            return
        }

        val startScaleX = startValues[Matrix.MSCALE_X]
        val startScaleY = startValues[Matrix.MSCALE_Y]
        suppMatrix.setValues(startValues)

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = SNAP_BACK_DURATION
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { animation ->
            val fraction = animation.animatedValue as Float
            matrixValues[Matrix.MSCALE_X] = startScaleX
            matrixValues[Matrix.MSCALE_Y] = startScaleY
            matrixValues[Matrix.MTRANS_X] = startTranslateX + (endTranslateX - startTranslateX) * fraction
            matrixValues[Matrix.MTRANS_Y] = startTranslateY + (endTranslateY - startTranslateY) * fraction
            matrixValues[Matrix.MSKEW_X] = 0f
            matrixValues[Matrix.MSKEW_Y] = 0f
            matrixValues[Matrix.MPERSP_0] = 0f
            matrixValues[Matrix.MPERSP_1] = 0f
            matrixValues[Matrix.MPERSP_2] = 1f
            suppMatrix.setValues(matrixValues)
            applyMatrix()
        }
        snapBackAnimator = animator
        animator.start()
    }

    // 根据当前拖动方向判断是否需要阻止 ViewPager 联动。
    private fun shouldBlockViewPager(dx: Float, dy: Float): Boolean {
        if (abs(dx) <= abs(dy)) return true
        val rect = getDisplayRect() ?: return false
        val threshold = rect.width() * HIDDEN_WIDTH_BLOCK_RATIO
        val remainHiddenWidth = if (dx > 0f) {
            max(-rect.left, 0f)
        } else {
            max(rect.right - width, 0f)
        }
        return remainHiddenWidth > threshold
    }

    // 处理多指切换时的活跃手指。
    private fun handlePointerUp(event: MotionEvent) {
        val upIndex = event.actionIndex
        val upId = event.getPointerId(upIndex)
        if (upId != activePointerId) return

        val newIndex = if (upIndex == 0) 1 else 0
        if (newIndex >= event.pointerCount) return

        activePointerId = event.getPointerId(newIndex)
        lastTouchX = event.getX(newIndex)
        lastTouchY = event.getY(newIndex)
    }

    // 当前是否处于放大状态。
    private fun isZoomed(): Boolean {
        return getSuppScale() > MIN_SUPP_SCALE + SCALE_EPSILON
    }

    // 获取用户缩放倍数。
    private fun getSuppScale(): Float {
        suppMatrix.getValues(matrixValues)
        return matrixValues[Matrix.MSCALE_X]
    }

    // 获取当前图片显示区域。
    private fun getDisplayRect(): RectF? {
        val cur = drawable ?: return null
        if (cur.intrinsicWidth <= 0 || cur.intrinsicHeight <= 0) return null
        drawableRect.set(0f, 0f, cur.intrinsicWidth.toFloat(), cur.intrinsicHeight.toFloat())
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(suppMatrix)
        drawMatrix.mapRect(displayRect, drawableRect)
        return displayRect
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentScale = getSuppScale()
            val targetScale = min(max(currentScale * detector.scaleFactor, MIN_SUPP_SCALE), MAX_SUPP_SCALE)
            val factor = targetScale / currentScale
            suppMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            fixTranslation()
            applyMatrix()
            return true
        }
    }

    companion object {
        private const val MIN_SUPP_SCALE = 1f
        private const val MAX_SUPP_SCALE = 4f
        private const val SCALE_EPSILON = 0.01f
        private const val HIDDEN_WIDTH_BLOCK_RATIO = 0.1f
        private const val SNAP_BACK_DURATION = 180L
    }
}
