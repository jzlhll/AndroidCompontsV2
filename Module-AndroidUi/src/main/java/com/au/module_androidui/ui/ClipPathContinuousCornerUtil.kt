package com.au.module_androidui.ui

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * 连续圆角配置。
 */
data class ClipPathContinuousCorners(
    val topLeft: Float = 0f,
    val topRight: Float = 0f,
    val bottomLeft: Float = 0f,
    val bottomRight: Float = 0f,
) {
    /**
     * 是否存在有效圆角。
     */
    fun hasCorner(): Boolean {
        return topLeft > 0f || topRight > 0f || bottomLeft > 0f || bottomRight > 0f
    }
}

/**
 * 使用四段三次贝塞尔曲线构建连续圆角 path。
 */
object ContinuousCornerPathUtil {
    private const val ROUND_RECT_KAPPA = 0.5522848f
    private const val CONTINUOUS_EXTRA_HANDLE = 0.16f
    private const val CONTINUOUS_EDGE_PULL = 0.46f

    /**
     * 生成连续圆角 Path。
     *
     * @param path 输出 Path，会先 reset
     * @param rect 目标矩形区域
     * @param corners 四角圆角半径
     * @param smoothness 顺滑程度，0 接近普通圆角，1 更接近 squircle
     */
    fun buildPath(
        path: Path,
        rect: RectF,
        corners: ClipPathContinuousCorners,
        smoothness: Float = 0.6f,
    ) {
        path.reset()
        val width = rect.width()
        val height = rect.height()
        if (width <= 0f || height <= 0f) {
            return
        }
        if (!corners.hasCorner()) {
            path.addRect(rect, Path.Direction.CW)
            return
        }

        val smoothValue = normalizeSmoothness(smoothness)
        val resolved = resolveCorners(width, height, corners)

        val left = rect.left
        val top = rect.top
        val right = rect.right
        val bottom = rect.bottom

        val tlPull = resolveEdgePull(resolved.topLeft, smoothValue)
        val trPull = resolveEdgePull(resolved.topRight, smoothValue)
        val brPull = resolveEdgePull(resolved.bottomRight, smoothValue)
        val blPull = resolveEdgePull(resolved.bottomLeft, smoothValue)

        val tlHandle = resolveHandle(resolved.topLeft, smoothValue)
        val trHandle = resolveHandle(resolved.topRight, smoothValue)
        val brHandle = resolveHandle(resolved.bottomRight, smoothValue)
        val blHandle = resolveHandle(resolved.bottomLeft, smoothValue)

        path.moveTo(left + resolved.topLeft + tlPull, top)
        path.lineTo(right - resolved.topRight - trPull, top)
        appendTopRight(path, right, top, resolved.topRight, trPull, trHandle)
        path.lineTo(right, bottom - resolved.bottomRight - brPull)
        appendBottomRight(path, right, bottom, resolved.bottomRight, brPull, brHandle)
        path.lineTo(left + resolved.bottomLeft + blPull, bottom)
        appendBottomLeft(path, left, bottom, resolved.bottomLeft, blPull, blHandle)
        path.lineTo(left, top + resolved.topLeft + tlPull)
        appendTopLeft(path, left, top, resolved.topLeft, tlPull, tlHandle)
        path.close()
    }

    private fun appendTopRight(
        path: Path,
        right: Float,
        top: Float,
        radius: Float,
        edgePull: Float,
        handle: Float,
    ) {
        if (radius <= 0f) {
            path.lineTo(right, top)
            return
        }
        path.cubicTo(
            right - radius + handle,
            top,
            right,
            top + radius - handle,
            right,
            top + radius + edgePull,
        )
    }

    private fun appendBottomRight(
        path: Path,
        right: Float,
        bottom: Float,
        radius: Float,
        edgePull: Float,
        handle: Float,
    ) {
        if (radius <= 0f) {
            path.lineTo(right, bottom)
            return
        }
        path.cubicTo(
            right,
            bottom - radius + handle,
            right - radius + handle,
            bottom,
            right - radius - edgePull,
            bottom,
        )
    }

    private fun appendBottomLeft(
        path: Path,
        left: Float,
        bottom: Float,
        radius: Float,
        edgePull: Float,
        handle: Float,
    ) {
        if (radius <= 0f) {
            path.lineTo(left, bottom)
            return
        }
        path.cubicTo(
            left + radius - handle,
            bottom,
            left,
            bottom - radius + handle,
            left,
            bottom - radius - edgePull,
        )
    }

    private fun appendTopLeft(
        path: Path,
        left: Float,
        top: Float,
        radius: Float,
        edgePull: Float,
        handle: Float,
    ) {
        if (radius <= 0f) {
            path.lineTo(left, top)
            return
        }
        path.cubicTo(
            left,
            top + radius - handle,
            left + radius - handle,
            top,
            left + radius + edgePull,
            top,
        )
    }

    private fun resolveEdgePull(radius: Float, smoothness: Float): Float {
        return if (radius <= 0f) 0f else radius * CONTINUOUS_EDGE_PULL * smoothness
    }

    private fun resolveHandle(radius: Float, smoothness: Float): Float {
        return if (radius <= 0f) 0f else radius * (ROUND_RECT_KAPPA + CONTINUOUS_EXTRA_HANDLE * smoothness)
    }

    /**
     * 限制四角半径，避免相邻两角之和超过边长。
     */
    private fun resolveCorners(
        width: Float,
        height: Float,
        corners: ClipPathContinuousCorners,
    ): ClipPathContinuousCorners {
        var topLeft = max(0f, corners.topLeft)
        var topRight = max(0f, corners.topRight)
        var bottomLeft = max(0f, corners.bottomLeft)
        var bottomRight = max(0f, corners.bottomRight)

        val maxCorner = min(width, height) * 0.5f
        topLeft = min(topLeft, maxCorner)
        topRight = min(topRight, maxCorner)
        bottomLeft = min(bottomLeft, maxCorner)
        bottomRight = min(bottomRight, maxCorner)

        val topScale = resolveScale(width, topLeft + topRight)
        val bottomScale = resolveScale(width, bottomLeft + bottomRight)
        val leftScale = resolveScale(height, topLeft + bottomLeft)
        val rightScale = resolveScale(height, topRight + bottomRight)
        val scale = min(min(topScale, bottomScale), min(leftScale, rightScale))

        return ClipPathContinuousCorners(
            topLeft = topLeft * scale,
            topRight = topRight * scale,
            bottomLeft = bottomLeft * scale,
            bottomRight = bottomRight * scale,
        )
    }

    private fun resolveScale(limit: Float, sum: Float): Float {
        if (sum <= 0f || sum <= limit) {
            return 1f
        }
        return limit / sum
    }

    private fun normalizeSmoothness(value: Float): Float {
        return when {
            value < 0f -> 0f
            value > 1f -> 1f
            else -> value
        }
    }
}

/**
 * 给自定义 View/ViewGroup 复用的连续圆角 clipPath helper。
 *
 * 使用示例：
 * class DemoLayout @JvmOverloads constructor(
 *     context: Context,
 *     attrs: AttributeSet? = null,
 * ) : FrameLayout(context, attrs) {
 *
 *     private val continuousCornerHelper = ClipPathContinuousCornerHelper(
 *         corners = ClipPathContinuousCorners(
 *             topLeft = 24f,
 *             topRight = 24f,
 *             bottomLeft = 24f,
 *             bottomRight = 24f,
 *         ),
 *         smoothness = 0.6f,
 *     )
 *
 *     override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
 *         super.onSizeChanged(w, h, oldw, oldh)
 *         continuousCornerHelper.updateSize(w, h)
 *     }
 *
 *     override fun dispatchDraw(canvas: Canvas) {
 *         continuousCornerHelper.clipDraw(canvas) {
 *             super.dispatchDraw(canvas)
 *         }
 *     }
 * }
 */
class ClipPathContinuousCornerHelper(
    corners: ClipPathContinuousCorners = ClipPathContinuousCorners(),
    smoothness: Float = 0.6f,
) {
    private val path = Path()
    private val rect = RectF()

    var corners: ClipPathContinuousCorners = corners
        set(value) {
            field = value
            rebuildPath()
        }

    var smoothness: Float = normalizeSmoothness(smoothness)
        set(value) {
            field = normalizeSmoothness(value)
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
     * 按当前尺寸和连续圆角裁剪绘制内容。
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
        ContinuousCornerPathUtil.buildPath(
            path = path,
            rect = rect,
            corners = corners,
            smoothness = smoothness,
        )
    }

    private fun normalizeSmoothness(value: Float): Float {
        return when {
            value < 0f -> 0f
            value > 1f -> 1f
            else -> value
        }
    }
}
