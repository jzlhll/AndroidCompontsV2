package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.withStyledAttributes
import com.au.module_androidui.R

/**
 * 支持裁剪子 View 的圆角 ConstraintLayout。
 */
class RoundCornerConstaintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val clipPath = Path()
    private val rect = RectF()
    private var cornerSize = 0f
    private var isTopNormal = false
    private var isBottomNormal = false

    init {
        context.withStyledAttributes(attrs, R.styleable.RoundCornerConstaintLayout, defStyleAttr, 0) {
            cornerSize = getDimension(R.styleable.RoundCornerConstaintLayout_cornerRadius, 0f)
            isTopNormal = getBoolean(R.styleable.RoundCornerConstaintLayout_roundTopNormal, false)
            isBottomNormal = getBoolean(R.styleable.RoundCornerConstaintLayout_roundBottomNormal, false)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clipPath.reset()
        rect.set(0f, 0f, w.toFloat(), h.toFloat())
        if (cornerSize <= 0f || w <= 0 || h <= 0) return
        val radii = when {
            isTopNormal && isBottomNormal -> FloatArray(8)
            isTopNormal -> floatArrayOf(0f, 0f, 0f, 0f, cornerSize, cornerSize, cornerSize, cornerSize)
            isBottomNormal -> floatArrayOf(cornerSize, cornerSize, cornerSize, cornerSize, 0f, 0f, 0f, 0f)
            else -> floatArrayOf(
                cornerSize,
                cornerSize,
                cornerSize,
                cornerSize,
                cornerSize,
                cornerSize,
                cornerSize,
                cornerSize,
            )
        }
        clipPath.addRoundRect(rect, radii, Path.Direction.CW)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (cornerSize <= 0f) {
            super.dispatchDraw(canvas)
            return
        }
        val saveCount = canvas.save()
        canvas.clipPath(clipPath)
        super.dispatchDraw(canvas)
        canvas.restoreToCount(saveCount)
    }
}
