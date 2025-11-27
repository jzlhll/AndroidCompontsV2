package com.au.module_androidui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.au.module_android.widget.CustomFontText

class TypewriterTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CustomFontText(context, attrs, defStyleAttr) {

    private var fullText = ""
    private var currentIndex = 0
    private var typingSpeed = 100L // 默认每100毫秒一个字符
    private var isTyping = false
    private var showCursor = true
    private var cursorBlinkSpeed = 500L

    private var _cursorColor: Int? = null

    // 全局 Handler 和 Runnable
    private val handler = Handler(Looper.getMainLooper())
    private val typingRunnable = object : Runnable {
        override fun run() {
            if (currentIndex < fullText.length && isTyping) {
                // 显示到当前索引的文本
                text = fullText.substring(0, currentIndex + 1)
                currentIndex++
                // 继续下一个字符
                handler.postDelayed(this, typingSpeed)
            } else if (currentIndex >= fullText.length) {
                // 打字完成
                isTyping = false
                stopCursorAnimation()
                onTypingComplete?.invoke()
            }
        }
    }

    /**
     * 设置光标颜色
     */
    fun setCursorColor(@ColorInt color: Int) {
        _cursorColor = color
        cursorPaint.color = getCursorColor()
    }

    fun getCursorColor(): Int {
        return _cursorColor ?: ContextCompat.getColor(context, android.R.color.black)
    }

    private val cursorPaint by lazy {
        Paint().apply {
            color = getCursorColor()
            strokeWidth = 4f
        }
    }

    private var cursorAnimator: ValueAnimator? = null
    private var onTypingComplete: (() -> Unit)? = null

    /**
     * 开始打字机效果
     * @param text 要显示的文本
     * @param speed 打字速度（每个字符的间隔时间，毫秒）
     * @param onComplete 打字完成回调
     */
    fun startTypewriter(
        text: String,
        speed: Long = typingSpeed,
        onComplete: (() -> Unit)? = null
    ) {
        // 如果正在打字，先停止
        if (isTyping) {
            stopTypewriter()
        }

        fullText = text
        currentIndex = 0
        typingSpeed = speed
        this.showCursor = true
        onTypingComplete = onComplete
        isTyping = true

        setText("")
        // 开始光标闪烁动画
        startCursorAnimation()
        // 开始打字
        handler.post(typingRunnable)
    }

    /**
     * 开始光标闪烁动画
     */
    private fun startCursorAnimation() {
        cursorAnimator = ValueAnimator.ofInt(0, 1).apply {
            duration = cursorBlinkSpeed
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener {
                invalidate() // 重绘以更新光标显示状态
            }
            start()
        }
    }

    /**
     * 停止光标动画
     */
    private fun stopCursorAnimation() {
        cursorAnimator?.cancel()
        cursorAnimator = null
        showCursor = false
        invalidate() // 重绘以隐藏光标
    }

    /**
     * 停止打字机效果
     */
    fun stopTypewriter() {
        isTyping = false
        stopCursorAnimation()
        // 移除所有待处理的回调
        handler.removeCallbacks(typingRunnable)
    }

    /**
     * 立即完成打字
     */
    fun completeNow() {
        if (isTyping) {
            text = fullText
            stopTypewriter()
            onTypingComplete?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 绘制光标（只在打字过程中且光标可见时绘制）
        if (showCursor && isTyping && cursorAnimator?.animatedValue == 1) {
            val textWidth = paint.measureText(text.toString())
            val cursorX = paddingLeft + textWidth
            val baseline = getBaseline().toFloat()
            val bottom = baseline + paint.descent()
            canvas.drawLine(cursorX, baseline, cursorX, bottom, cursorPaint)
        }
    }

    /**
     * 是否正在打字
     */
    fun isTyping(): Boolean = isTyping

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // 在 View 从窗口分离时清理所有资源
        stopTypewriter()
        handler.removeCallbacksAndMessages(null) // 移除所有待处理的回调
    }
}