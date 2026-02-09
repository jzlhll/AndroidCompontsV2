package com.au.module_androidui.ui

import android.content.res.TypedArray
import android.view.View
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.ViewShadowBuilder
import com.au.module_androidui.R

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
    val needRippleColor: Int,
    val backgroundGradientStart: Int = 0,
    val backgroundGradientEnd: Int = 0,
    val backgroundGradientAngle: Int = 0,
    // 阴影属性
    val shadowColor: Int = 0,
    val shadowOffsetX: Int = 0,
    val shadowOffsetY: Int = 0,
    val shadowBlur: Int = 0,
    val shadowSpread: Int = 0
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
    R.styleable.BgBuildLinearLayout_needRippleColor,
    R.styleable.BgBuildLinearLayout_backgroundGradientStart,
    R.styleable.BgBuildLinearLayout_backgroundGradientEnd,
    R.styleable.BgBuildLinearLayout_backgroundGradientAngle,
    R.styleable.BgBuildLinearLayout_shadowColor,
    R.styleable.BgBuildLinearLayout_shadowOffsetX,
    R.styleable.BgBuildLinearLayout_shadowOffsetY,
    R.styleable.BgBuildLinearLayout_shadowBlur,
    R.styleable.BgBuildLinearLayout_shadowSpread
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
    R.styleable.BgBuildRelativeLayout_needRippleColor,
    R.styleable.BgBuildRelativeLayout_backgroundGradientStart,
    R.styleable.BgBuildRelativeLayout_backgroundGradientEnd,
    R.styleable.BgBuildRelativeLayout_backgroundGradientAngle,
    R.styleable.BgBuildRelativeLayout_shadowColor,
    R.styleable.BgBuildRelativeLayout_shadowOffsetX,
    R.styleable.BgBuildRelativeLayout_shadowOffsetY,
    R.styleable.BgBuildRelativeLayout_shadowBlur,
    R.styleable.BgBuildRelativeLayout_shadowSpread
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
    R.styleable.BgBuildConstraintLayout_needRippleColor,
    R.styleable.BgBuildConstraintLayout_backgroundGradientStart,
    R.styleable.BgBuildConstraintLayout_backgroundGradientEnd,
    R.styleable.BgBuildConstraintLayout_backgroundGradientAngle,
    R.styleable.BgBuildConstraintLayout_shadowColor,
    R.styleable.BgBuildConstraintLayout_shadowOffsetX,
    R.styleable.BgBuildConstraintLayout_shadowOffsetY,
    R.styleable.BgBuildConstraintLayout_shadowBlur,
    R.styleable.BgBuildConstraintLayout_shadowSpread
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
    R.styleable.BgBuildFrameLayout_needRippleColor,
    R.styleable.BgBuildFrameLayout_backgroundGradientStart,
    R.styleable.BgBuildFrameLayout_backgroundGradientEnd,
    R.styleable.BgBuildFrameLayout_backgroundGradientAngle,
    R.styleable.BgBuildFrameLayout_shadowColor,
    R.styleable.BgBuildFrameLayout_shadowOffsetX,
    R.styleable.BgBuildFrameLayout_shadowOffsetY,
    R.styleable.BgBuildFrameLayout_shadowBlur,
    R.styleable.BgBuildFrameLayout_shadowSpread
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
    R.styleable.BgBuildCustomFontText_needRippleColor,
    R.styleable.BgBuildCustomFontText_backgroundGradientStart,
    R.styleable.BgBuildCustomFontText_backgroundGradientEnd,
    R.styleable.BgBuildCustomFontText_backgroundGradientAngle,
    R.styleable.BgBuildCustomFontText_shadowColor,
    R.styleable.BgBuildCustomFontText_shadowOffsetX,
    R.styleable.BgBuildCustomFontText_shadowOffsetY,
    R.styleable.BgBuildCustomFontText_shadowBlur,
    R.styleable.BgBuildCustomFontText_shadowSpread
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
    R.styleable.BgBuildView_needRippleColor,
    R.styleable.BgBuildView_backgroundGradientStart,
    R.styleable.BgBuildView_backgroundGradientEnd,
    R.styleable.BgBuildView_backgroundGradientAngle,
    // 阴影属性
    R.styleable.BgBuildView_shadowColor,
    R.styleable.BgBuildView_shadowOffsetX,
    R.styleable.BgBuildView_shadowOffsetY,
    R.styleable.BgBuildView_shadowBlur,
    R.styleable.BgBuildView_shadowSpread
)

val BgBuildImageViewIds = AnyViewIds(
    R.styleable.BgBuildImageView_backgroundAlpha,
    R.styleable.BgBuildImageView_backgroundNormal,
    R.styleable.BgBuildImageView_backgroundDisabled,
    R.styleable.BgBuildImageView_backgroundPressed,
    R.styleable.BgBuildImageView_cornerRadius,
    R.styleable.BgBuildImageView_cornerSizeTopLeft,
    R.styleable.BgBuildImageView_cornerSizeTopRight,
    R.styleable.BgBuildImageView_cornerSizeBottomLeft,
    R.styleable.BgBuildImageView_cornerSizeBottomRight,
    R.styleable.BgBuildImageView_strokeColor,
    R.styleable.BgBuildImageView_strokeWidth,
    R.styleable.BgBuildImageView_needRippleColor,
    R.styleable.BgBuildImageView_backgroundGradientStart,
    R.styleable.BgBuildImageView_backgroundGradientEnd,
    R.styleable.BgBuildImageView_backgroundGradientAngle,
    // 阴影属性
    R.styleable.BgBuildImageView_shadowColor,
    R.styleable.BgBuildImageView_shadowOffsetX,
    R.styleable.BgBuildImageView_shadowOffsetY,
    R.styleable.BgBuildImageView_shadowBlur,
    R.styleable.BgBuildImageView_shadowSpread
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
    R.styleable.CustomButton_needRippleColor,
    R.styleable.CustomButton_backgroundGradientStart,
    R.styleable.CustomButton_backgroundGradientEnd,
    R.styleable.CustomButton_backgroundGradientAngle,
    R.styleable.CustomButton_shadowColor,
    R.styleable.CustomButton_shadowOffsetX,
    R.styleable.CustomButton_shadowOffsetY,
    R.styleable.CustomButton_shadowBlur,
    R.styleable.CustomButton_shadowSpread
)

fun View.viewShadowBuild(array: TypedArray, viewIds: AnyViewIds, backgroundBuilder: ViewBackgroundBuilder? = null): ViewShadowBuilder? {
    val noColor = 0
    // 阴影配置
    if (viewIds.shadowColor != 0) {
        val shadowColor = array.getColor(viewIds.shadowColor, noColor)
        if (shadowColor != noColor) {
            val shadowOffsetX = array.getDimension(viewIds.shadowOffsetX, 0f)
            val shadowOffsetY = array.getDimension(viewIds.shadowOffsetY, 0f)
            val shadowBlur = array.getDimension(viewIds.shadowBlur, 0f)
            val shadowSpread = array.getDimension(viewIds.shadowSpread, 0f)
            val builder = ViewShadowBuilder()
            builder.setShadow(shadowColor, shadowOffsetX, shadowOffsetY, shadowBlur, shadowSpread)
            if (backgroundBuilder != null) {
                builder.setCornerRadii(backgroundBuilder.getCornerRadiiArray())
            }
            return builder
        }
    }
    return null
}

fun View.viewBackgroundBuild(array: TypedArray, viewIds: AnyViewIds): ViewBackgroundBuilder {
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

    val startColor = array.getColor(viewIds.backgroundGradientStart, noColor)
    val endColor = array.getColor(viewIds.backgroundGradientEnd, noColor)
    val angle = array.getInt(viewIds.backgroundGradientAngle, 0)
    builder.setGradient(startColor, endColor, angle)

    builder.setStroke(strokeWidth, strokeColor)

    if (builder.isAtLeastOne) {
        background = builder.build()
    }
    return builder
}
