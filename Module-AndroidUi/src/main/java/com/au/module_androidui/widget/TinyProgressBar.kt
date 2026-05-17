package com.au.module_androidui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.au.module_androidui.R
import kotlin.math.max
import kotlin.math.min

/**
 * 自定义进度条，支持背景色、进度色、圆角、进度值设置。
 * 高度和宽度支持 wrap_content（默认高度 2dp，默认宽度 100dp）。
 * 圆角默认取测量高度的一半。
 */
class TinyProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressColor: Int = ContextCompat.getColor(context, com.au.module_androidcolor.R.color.i8o_color_text_normal)
    private var progressBgColor: Int = ContextCompat.getColor(context, com.au.module_androidcolor.R.color.i8o_color_text_desc)
    private var cornerRadius: Float = -1f // -1 表示使用高度的一半
    
    var progress: Int = 0
        set(value) {
            field = max(0, min(value, maxProgress))
            invalidate()
        }
        
    var maxProgress: Int = 100
        set(value) {
            field = max(1, value)
            if (progress > field) {
                progress = field
            } else {
                invalidate()
            }
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rectF = RectF()

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(it, R.styleable.TinyProgressBar)
            progressColor = typedArray.getColor(R.styleable.TinyProgressBar_pbProgressColor, progressColor)
            progressBgColor = typedArray.getColor(R.styleable.TinyProgressBar_pbProgressBgColor, progressBgColor)
            cornerRadius = typedArray.getDimension(R.styleable.TinyProgressBar_pbCornerRadius, -1f)
            maxProgress = typedArray.getInt(R.styleable.TinyProgressBar_pbMax, 100)
            progress = typedArray.getInt(R.styleable.TinyProgressBar_pbProgress, 0)
            typedArray.recycle()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val defaultWidth = (100 * resources.displayMetrics.density).toInt()
        val defaultHeight = (2 * resources.displayMetrics.density).toInt()

        val width = when (widthMode) {
            MeasureSpec.EXACTLY -> widthSize
            MeasureSpec.AT_MOST -> min(defaultWidth, widthSize)
            else -> defaultWidth
        }

        val height = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> min(defaultHeight, heightSize)
            else -> defaultHeight
        }

        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0 || height <= 0) return

        val radius = if (cornerRadius < 0) height / 2f else cornerRadius

        // 绘制背景
        paint.color = progressBgColor
        rectF.set(0f, 0f, width, height)
        canvas.drawRoundRect(rectF, radius, radius, paint)

        // 绘制进度
        if (progress > 0) {
            paint.color = progressColor
            val progressWidth = width * (progress.toFloat() / maxProgress.toFloat())
            rectF.set(0f, 0f, progressWidth, height)
            canvas.drawRoundRect(rectF, radius, radius, paint)
        }
    }
}
