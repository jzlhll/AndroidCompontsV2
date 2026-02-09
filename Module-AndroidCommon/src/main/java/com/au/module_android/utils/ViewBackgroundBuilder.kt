package com.au.module_android.utils

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import androidx.annotation.ColorInt
import com.au.module_android.Globals
import com.au.module_android.R
import kotlin.math.max

class ViewBackgroundBuilder {
    //private var mShape:Int = -1
    //private var mAlpha:Float = -1f
    internal var mCorner: CornerRadius? = null
    private var mStrokeWidth:Float = 0f
    private var mStrokeColor:Int = 0
    private var mBg:ColorStateList? = null
    private var mGradientStartColor: Int = 0
    private var mGradientEndColor: Int = 0
    private var mGradientAngle: Int = 0

    private var mBgAlpha = -1

    private var mNeedRippleColor = false

    var isAtLeastOne = false

    /**
     * 圆角
     */
    sealed class CornerRadius {
        class AllCornerRadius(val size: Float) : CornerRadius() {
            override fun size(): Float {
                return size
            }
        }

        class EachCornerRadius(val topLeft:Float, val topRight:Float, val bottomLeft:Float, val bottomRight:Float) : CornerRadius() {
            fun convert() = floatArrayOf(
                topLeft, topLeft,
                topRight, topRight,
                bottomLeft, bottomLeft,
                bottomRight, bottomRight
            )

            override fun size(): Float {
                return max(topLeft, topRight)
            }
        }

        abstract fun size() : Float
    }

    /**
     * 0~3 RECT, OVAL, LINE, RING
     */
//    fun setShape(shape:Int) : ViewBackgroundBuilder {
//        mShape = shape
//        return this
//    }
//
//    fun setAlpha(alpha:Float) : ViewBackgroundBuilder {
//        mAlpha = alpha
//        return this
//    }

    fun setStroke(width:Float, color:Int) : ViewBackgroundBuilder {
        if (width > 0) {
            mStrokeWidth = width
            mStrokeColor = color
            isAtLeastOne = true
        }
        return this
    }

    fun setCornerRadius(cornerRadius: Float) : ViewBackgroundBuilder {
        if (cornerRadius > 0) {
            mCorner = CornerRadius.AllCornerRadius(cornerRadius)
            isAtLeastOne = true
        }
        return this
    }

    fun setCornerRadius(topLeft:Float, topRight:Float, bottomLeft:Float, bottomRight:Float) : ViewBackgroundBuilder {
        if (topLeft > 0f || topRight > 0f || bottomLeft > 0f || bottomRight > 0f) {
            mCorner = CornerRadius.EachCornerRadius(topLeft, topRight, bottomLeft, bottomRight)
            isAtLeastOne = true
        }
        return this
    }

    fun setBackgroundAlpha(alpha:Int): ViewBackgroundBuilder {
        mBgAlpha = alpha
        return this
    }

    fun needRippleColor(need: Boolean) : ViewBackgroundBuilder {
        mNeedRippleColor = need
        return this
    }

    /**
     * 设置线性渐变背景
     * @param startColor 渐变开始颜色
     * @param endColor 渐变结束颜色
     * @param angle 渐变角度。虽然支持任意 int 值，但内部会映射到最接近的 45 度倍数方向：
     *              0: LEFT_RIGHT (左 -> 右)
     *              45: BL_TR (左下 -> 右上)
     *              90: BOTTOM_TOP (下 -> 上)
     *              135: BR_TL (右下 -> 左上)
     *              180: RIGHT_LEFT (右 -> 左)
     *              225: TR_BL (右上 -> 左下)
     *              270: TOP_BOTTOM (上 -> 下)
     *              315: TL_BR (左上 -> 右下)
     */
    fun setGradient(startColor: Int, endColor: Int, angle: Int): ViewBackgroundBuilder {
        if (startColor != 0 && endColor != 0) {
            mGradientStartColor = startColor
            mGradientEndColor = endColor
            mGradientAngle = angle
            isAtLeastOne = true
        }
        return this
    }

    fun setBackground(color:Int, pressedColor:Int = 0, disabledColor:Int = 0)
            : ViewBackgroundBuilder {
        val colorMap = mutableListOf<Pair<IntArray, Int>>()
        val noColor = 0

        var hasColor = false
        if (pressedColor != noColor) {
            colorMap.add(Pair(intArrayOf(android.R.attr.state_pressed), pressedColor))
            hasColor = true
        }
        if (disabledColor != noColor) {//-代表false
            colorMap.add(Pair(intArrayOf(-android.R.attr.state_enabled), disabledColor))
            hasColor = true
        }
        if(color != noColor) {
            colorMap.add(Pair(intArrayOf(0), color))
            hasColor = true
        }

        if (hasColor) {
            val size = colorMap.size
            val stateArray = arrayOfNulls<IntArray>(size)
            val colorArray = IntArray(size)
            colorMap.forEachIndexed { index, data ->
                stateArray[index] = data.first
                colorArray[index] = data.second
            }
            mBg = ColorStateList(stateArray, colorArray)

            isAtLeastOne = true
        }

        return this
    }

    fun build() : Drawable? {
        if (!isAtLeastOne) {
            return null
        }

        val it = GradientDrawable()
        //背景
        if (mGradientStartColor != 0 && mGradientEndColor != 0) {
            it.colors = intArrayOf(mGradientStartColor, mGradientEndColor)
            it.orientation = getGradientOrientation(mGradientAngle)
            it.gradientType = GradientDrawable.LINEAR_GRADIENT
        } else if (mBg != null) {
             it.color = mBg
        }

        //圆角
        when (mCorner) {
            is CornerRadius.AllCornerRadius -> {
                it.cornerRadius = (mCorner as CornerRadius.AllCornerRadius).size
            }
            is CornerRadius.EachCornerRadius -> {
                it.cornerRadii = (mCorner as CornerRadius.EachCornerRadius).convert()
            }
            null -> {}
        }

        //边框
        if (mStrokeWidth > 0 && mStrokeColor != 0) {
            it.setStroke(mStrokeWidth.toInt(), mStrokeColor)
        }

        //形状 RECTANGLE, OVAL, LINE, RING
//        when (mShape) {
//            0->it.shape = GradientDrawable.RECTANGLE
//            1->it.shape = GradientDrawable.OVAL
//            2->it.shape = GradientDrawable.LINE
//            3->it.shape = GradientDrawable.RING
//        }

        //alpha
        if (mBgAlpha >= 0) {
            it.alpha = mBgAlpha
        }

        if (mNeedRippleColor) {
            val color = Globals.app.resources.getColor(R.color.ripple_default, null)
            return it.setRippleColor(color)
        }
        return it
    }

    private fun getGradientOrientation(angle: Int): GradientDrawable.Orientation {
        val normalizedAngle = ((angle % 360) + 360) % 360
        val index = ((normalizedAngle + 22.5) / 45).toInt() % 8
        return when (index) {
            0 -> GradientDrawable.Orientation.LEFT_RIGHT // 0
            1 -> GradientDrawable.Orientation.BL_TR      // 45
            2 -> GradientDrawable.Orientation.BOTTOM_TOP // 90
            3 -> GradientDrawable.Orientation.BR_TL      // 135
            4 -> GradientDrawable.Orientation.RIGHT_LEFT // 180
            5 -> GradientDrawable.Orientation.TR_BL      // 225
            6 -> GradientDrawable.Orientation.TOP_BOTTOM // 270
            7 -> GradientDrawable.Orientation.TL_BR      // 315
            else -> GradientDrawable.Orientation.LEFT_RIGHT
        }
    }

    fun getCornerRadiiArray(): FloatArray {
        return when (val c = mCorner) {
            is CornerRadius.AllCornerRadius -> {
                val r = c.size
                floatArrayOf(r, r, r, r, r, r, r, r)
            }
            is CornerRadius.EachCornerRadius -> c.convert()
            null -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        }
    }
}

/**
 * 对任何view设置RippleColor颜色
 */
fun Drawable?.setRippleColor(@ColorInt rippleColor: Int, radius: Int? = null) : Drawable{
    if (this is RippleDrawable) {
        this.setColor(ColorStateList.valueOf(rippleColor))
        if (radius != null) {
            this.radius = radius
        }
        return this
    }

    val newDrawable = RippleDrawable(
        ColorStateList.valueOf(rippleColor),
        this,
        null
    )
    if (radius != null) {
        newDrawable.radius = radius
    }
    return newDrawable
}

/**
 * 对任何view设置RippleColor颜色
 */
fun View.setRippleColor(@ColorInt rippleColor: Int, radius: Int? = null) {
    val drawable = foreground ?: background
    val fixDrawable = drawable.setRippleColor(rippleColor, radius)
    if (foreground != null) {
        foreground = fixDrawable
    } else {
        background = fixDrawable
    }
}