package com.au.module_android.utils

import android.graphics.*

/**
 * 独立的阴影构建器，负责处理 View 的阴影绘制
 * warning: 使用规则：
 * 1. 必须在 View 的 onDraw 方法中调用，在super.onDraw(canvas)之前调用
 * 2. - clipChildren ：设置为 false 允许子 View 绘制在父控件边界之外。
 *     - clipToPadding ：如果父控件设置了 Padding，需设置为 false 才能让子 View 绘制到 Padding 区域。
 *     - 传递性 ：如果子 View 嵌套多层，每一层父布局都必须设置该属性，否则会被上层截断。
 *
 *     一个良好的示例：
     <FrameLayout
         android:id="@+id/selectBgView"
         app:layout_constraintStart_toStartOf="parent"
         app:layout_constraintEnd_toStartOf="@id/centerGuideline"
         android:layout_height="match_parent"
         android:padding="4dp"
         android:clipChildren="false"
         android:clipToPadding="false"
         android:layout_width="0dp">
         <com.au.module_androidui.widget.BgBuildView
             android:id="@+id/selectBgViewReal"
             app:cornerRadius="30dp"
             app:backgroundNormal="#000000"
             app:shadowBlur="10dp"
             app:shadowColor="#3f000000"
             app:shadowOffsetY="4dp"
             android:layout_height="match_parent"
             android:layout_width="match_parent"
             tools:ignore="ContentDescription" />
     </FrameLayout>
 */
class ViewShadowBuilder {
    // 阴影属性
    private var mShadowColor: Int = Color.TRANSPARENT
    private var mShadowOffsetX: Float = 0f
    private var mShadowOffsetY: Float = 0f
    // 阴影模糊半径, 推荐必须有值，否则颜色很生硬。
    private var mShadowBlur: Float = 0f
    private var mShadowSpread: Float = 0f

    // 绘制缓存对象
    private val mShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val mShadowPath = Path()
    private val mShadowRect = RectF()
    private var mCachedShadowBlur = -1f
    private var mCachedMaskFilter: BlurMaskFilter? = null
    
    // 引用 ViewBackgroundBuilder 的 CornerRadius 定义，保持一致性
    // 但为了解耦，这里接收 float array 或者 ViewBackgroundBuilder.CornerRadius
    private var mCornerRadii: FloatArray? = null

    fun setShadow(color: Int, offsetX: Float, offsetY: Float, blur: Float, spread: Float): ViewShadowBuilder {
        mShadowColor = color
        mShadowOffsetX = offsetX
        mShadowOffsetY = offsetY
        mShadowBlur = blur
        mShadowSpread = spread
        return this
    }

    fun setCornerRadii(radii: FloatArray?) {
        mCornerRadii = radii
    }

    fun onDrawShadow(canvas: Canvas, width: Float, height: Float, strokeWidth: Float = 0f) {
        if (mShadowColor == Color.TRANSPARENT) return

        val inset = strokeWidth / 2f
        
        mShadowRect.set(
            inset - mShadowSpread,
            inset - mShadowSpread,
            width - inset + mShadowSpread,
            height - inset + mShadowSpread
        )

        mShadowPath.reset()
        // 使用传入的圆角或者默认0
        val radii = mCornerRadii ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        mShadowPath.addRoundRect(mShadowRect, radii, Path.Direction.CW)

        mShadowPaint.color = mShadowColor

        if (mShadowBlur > 0f) {
            if (mShadowBlur != mCachedShadowBlur) {
                mCachedMaskFilter = BlurMaskFilter(mShadowBlur, BlurMaskFilter.Blur.NORMAL)
                mCachedShadowBlur = mShadowBlur
            }
            mShadowPaint.maskFilter = mCachedMaskFilter
        } else {
            mShadowPaint.maskFilter = null // 锐利边缘
        }

        canvas.save()
        canvas.translate(mShadowOffsetX, mShadowOffsetY)
        canvas.drawPath(mShadowPath, mShadowPaint)
        canvas.restore()
    }
}
