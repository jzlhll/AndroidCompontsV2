package com.au.module_android.utils

import android.content.res.ColorStateList
import android.content.res.TypedArray
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
        if(mBg != null) it.color = mBg

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
}

data class AnyViewIds(
    val backgroundAlpha: Int,
    val backgroundNormal: Int,
    val backgroundDisabled: Int,
    val backgroundPressed: Int,
    val cornerRadius: Int,
    val cornerSizeTopLeft: Int,
    val cornerSizeTopRight: Int,
    val cornerSizeBottomLeft: Int,
    val cornerSizeBottomRight: Int,
    val strokeColor: Int,
    val strokeWidth: Int,
    val needRippleColor: Int
)

val BgBuildLinearLayoutIds = AnyViewIds(
    R.styleable.BgBuildLinearLayout_backgroundAlpha,
    R.styleable.BgBuildLinearLayout_backgroundNormal,
    R.styleable.BgBuildLinearLayout_backgroundDisabled,
    R.styleable.BgBuildLinearLayout_backgroundPressed,
    R.styleable.BgBuildLinearLayout_cornerRadius,
    R.styleable.BgBuildLinearLayout_cornerSizeTopLeft,
    R.styleable.BgBuildLinearLayout_cornerSizeTopRight,
    R.styleable.BgBuildLinearLayout_cornerSizeBottomLeft,
    R.styleable.BgBuildLinearLayout_cornerSizeBottomRight,
    R.styleable.BgBuildLinearLayout_strokeColor,
    R.styleable.BgBuildLinearLayout_strokeWidth,
    R.styleable.BgBuildLinearLayout_needRippleColor
)

val BgBuildRelativeLayoutIds = AnyViewIds(
    R.styleable.BgBuildRelativeLayout_backgroundAlpha,
    R.styleable.BgBuildRelativeLayout_backgroundNormal,
    R.styleable.BgBuildRelativeLayout_backgroundDisabled,
    R.styleable.BgBuildRelativeLayout_backgroundPressed,
    R.styleable.BgBuildRelativeLayout_cornerRadius,
    R.styleable.BgBuildRelativeLayout_cornerSizeTopLeft,
    R.styleable.BgBuildRelativeLayout_cornerSizeTopRight,
    R.styleable.BgBuildRelativeLayout_cornerSizeBottomLeft,
    R.styleable.BgBuildRelativeLayout_cornerSizeBottomRight,
    R.styleable.BgBuildRelativeLayout_strokeColor,
    R.styleable.BgBuildRelativeLayout_strokeWidth,
    R.styleable.BgBuildRelativeLayout_needRippleColor
)

val BgBuildConstraintLayoutIds = AnyViewIds(
    R.styleable.BgBuildConstraintLayout_backgroundAlpha,
    R.styleable.BgBuildConstraintLayout_backgroundNormal,
    R.styleable.BgBuildConstraintLayout_backgroundDisabled,
    R.styleable.BgBuildConstraintLayout_backgroundPressed,
    R.styleable.BgBuildConstraintLayout_cornerRadius,
    R.styleable.BgBuildConstraintLayout_cornerSizeTopLeft,
    R.styleable.BgBuildConstraintLayout_cornerSizeTopRight,
    R.styleable.BgBuildConstraintLayout_cornerSizeBottomLeft,
    R.styleable.BgBuildConstraintLayout_cornerSizeBottomRight,
    R.styleable.BgBuildConstraintLayout_strokeColor,
    R.styleable.BgBuildConstraintLayout_strokeWidth,
    R.styleable.BgBuildConstraintLayout_needRippleColor
)

val BgBuildFrameLayoutIds = AnyViewIds(
    R.styleable.BgBuildFrameLayout_backgroundAlpha,
    R.styleable.BgBuildFrameLayout_backgroundNormal,
    R.styleable.BgBuildFrameLayout_backgroundDisabled,
    R.styleable.BgBuildFrameLayout_backgroundPressed,
    R.styleable.BgBuildFrameLayout_cornerRadius,
    R.styleable.BgBuildFrameLayout_cornerSizeTopLeft,
    R.styleable.BgBuildFrameLayout_cornerSizeTopRight,
    R.styleable.BgBuildFrameLayout_cornerSizeBottomLeft,
    R.styleable.BgBuildFrameLayout_cornerSizeBottomRight,
    R.styleable.BgBuildFrameLayout_strokeColor,
    R.styleable.BgBuildFrameLayout_strokeWidth,
    R.styleable.BgBuildFrameLayout_needRippleColor
)

val BgBuildCustomFontTextIds = AnyViewIds(
    R.styleable.BgBuildCustomFontText_backgroundAlpha,
    R.styleable.BgBuildCustomFontText_backgroundNormal,
    R.styleable.BgBuildCustomFontText_backgroundDisabled,
    R.styleable.BgBuildCustomFontText_backgroundPressed,
    R.styleable.BgBuildCustomFontText_cornerRadius,
    R.styleable.BgBuildCustomFontText_cornerSizeTopLeft,
    R.styleable.BgBuildCustomFontText_cornerSizeTopRight,
    R.styleable.BgBuildCustomFontText_cornerSizeBottomLeft,
    R.styleable.BgBuildCustomFontText_cornerSizeBottomRight,
    R.styleable.BgBuildCustomFontText_strokeColor,
    R.styleable.BgBuildCustomFontText_strokeWidth,
    R.styleable.BgBuildCustomFontText_needRippleColor
)

val BgBuildViewIds = AnyViewIds(
    R.styleable.BgBuildView_backgroundAlpha,
    R.styleable.BgBuildView_backgroundNormal,
    R.styleable.BgBuildView_backgroundDisabled,
    R.styleable.BgBuildView_backgroundPressed,
    R.styleable.BgBuildView_cornerRadius,
    R.styleable.BgBuildView_cornerSizeTopLeft,
    R.styleable.BgBuildView_cornerSizeTopRight,
    R.styleable.BgBuildView_cornerSizeBottomLeft,
    R.styleable.BgBuildView_cornerSizeBottomRight,
    R.styleable.BgBuildView_strokeColor,
    R.styleable.BgBuildView_strokeWidth,
    R.styleable.BgBuildView_needRippleColor
)

val CustomButtonIds = AnyViewIds(
    R.styleable.CustomButton_backgroundAlpha,
    R.styleable.CustomButton_backgroundNormal,
    R.styleable.CustomButton_backgroundDisabled,
    R.styleable.CustomButton_backgroundPressed,
    R.styleable.CustomButton_cornerRadius,
    R.styleable.CustomButton_cornerSizeTopLeft,
    R.styleable.CustomButton_cornerSizeTopRight,
    R.styleable.CustomButton_cornerSizeBottomLeft,
    R.styleable.CustomButton_cornerSizeBottomRight,
    R.styleable.CustomButton_strokeColor,
    R.styleable.CustomButton_strokeWidth,
    R.styleable.CustomButton_needRippleColor
)

fun View.viewBackgroundBuild(array: TypedArray, viewIds: AnyViewIds) {
    val builder = ViewBackgroundBuilder()

    val noColor = 0

    val bgAlpha = array.getFloat(viewIds.backgroundAlpha, -1f)
    if (bgAlpha in 0f..255f) {
        val alpha = if (bgAlpha <= 1f) {
            (225f * bgAlpha).toInt()
        } else {
            bgAlpha.toInt()
        }
        builder.setBackgroundAlpha(alpha)
    }

    val bgNormalColor = array.getColor(viewIds.backgroundNormal, noColor)
    val bgDisabledColor = array.getColor(viewIds.backgroundDisabled, noColor)
    val bgPressedColor = array.getColor(viewIds.backgroundPressed, noColor)
    builder.setBackground(bgNormalColor, bgPressedColor, bgDisabledColor)

    val cornerRadius = array.getDimension(viewIds.cornerRadius, -1f)
    if (cornerRadius <= 0f) {
        val cornerSizeTopLeft = array.getDimension(viewIds.cornerSizeTopLeft, 0f)
        val cornerSizeTopRight = array.getDimension(viewIds.cornerSizeTopRight, 0f)
        val cornerSizeBottomLeft = array.getDimension(viewIds.cornerSizeBottomLeft, 0f)
        val cornerSizeBottomRight = array.getDimension(viewIds.cornerSizeBottomRight, 0f)
        if (cornerSizeTopLeft > 0f || cornerSizeTopRight > 0f
            || cornerSizeBottomLeft > 0 || cornerSizeBottomRight > 0) {
            builder.setCornerRadius(cornerSizeTopLeft, cornerSizeTopRight, cornerSizeBottomLeft, cornerSizeBottomRight)
        }
    } else {
        builder.setCornerRadius(cornerRadius)
    }

    val strokeColor = array.getColor(viewIds.strokeColor, noColor)
    val strokeWidth = array.getDimension(viewIds.strokeWidth, 0f)

    builder.needRippleColor(array.getBoolean(viewIds.needRippleColor, false))

    builder.setStroke(strokeWidth, strokeColor)
    if (builder.isAtLeastOne) {
        background = builder.build()
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