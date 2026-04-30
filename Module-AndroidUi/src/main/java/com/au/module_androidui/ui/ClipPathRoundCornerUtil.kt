package com.au.module_androidui.ui

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF

/**
 * clipPath 圆角配置。
 */
data class ClipPathRoundCorners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomLeft: Float = 0f,
    val bottomRight: Float = 0f,
) {
    /**
     * 转成 Path.addRoundRect 所需数组顺序。
     */
    fun toPathRadii(): FloatArray {
        return floatArrayOf(
            topLeft, topLeft,
            topRight, topRight,
            bottomRight, bottomRight,
            bottomLeft, bottomLeft,
        )
    }

    /**
     * 是否存在有效圆角。
     */
    fun hasCorner(): Boolean {
        return topLeft > 0f || topRight > 0f || bottomLeft > 0f || bottomRight > 0f
    }
}

/**
 * 给自定义 View/ViewGroup 复用的 clipPath 圆角 helper。
 *
 * 使用示例：
 * class DemoLayout @JvmOverloads constructor(
 *     context: Context,
 *     attrs: AttributeSet? = null,
 * ) : FrameLayout(context, attrs) {
 *
 *     private val roundCornerHelper = ClipPathRoundCornerHelper(
 *         ClipPathRoundCorners(
 *             topLeft = 24f,
 *             topRight = 24f,
 *             bottomLeft = 0f,
 *             bottomRight = 0f,
 *         )
 *     )
 *
 *     override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
 *         super.onSizeChanged(w, h, oldw, oldh)
 *         roundCornerHelper.updateSize(w, h)
 *     }
 *
 *     override fun dispatchDraw(canvas: Canvas) {
 *         roundCornerHelper.clipDraw(canvas) {
 *             super.dispatchDraw(canvas)
 *         }
 *     }
 * }
 *
 * 如果是普通 View，改成在 draw(canvas) 里包一层 clipDraw 即可。
 */
class ClipPathRoundCornerHelper(
    corners: ClipPathRoundCorners = ClipPathRoundCorners(),
) {
    private val path = Path()
    private val rect = RectF()

    var corners: ClipPathRoundCorners = corners
        set(value) {
            field = value
            rebuildPath()
        }

    /**
     * 更新绘制区域尺寸。
     */
    fun updateSize(width: Int, height: Int) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        rebuildPath()
    }

    /**
     * 按当前尺寸和圆角裁剪绘制内容。
     */
    fun clipDraw(canvas: Canvas, drawAction: () -> Unit) {
        if (!corners.hasCorner() || rect.width() <= 0f || rect.height() <= 0f) {
            drawAction()
            return
        }
        canvas.save()
        canvas.clipPath(path)
        try {
            drawAction()
        } finally {
            canvas.restore()
        }
    }

    private fun rebuildPath() {
        path.reset()
        if (!corners.hasCorner() || rect.width() <= 0f || rect.height() <= 0f) {
            return
        }
        path.addRoundRect(rect, corners.toPathRadii(), Path.Direction.CW)
    }
}
