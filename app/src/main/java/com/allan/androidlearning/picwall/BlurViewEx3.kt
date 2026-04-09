package com.allan.androidlearning.picwall;

import android.R.attr.angle
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import eightbitlab.com.blurview.BlurController
import eightbitlab.com.blurview.BlurTarget
import eightbitlab.com.blurview.BlurView

/**
 * 模糊背景
 * @param blurView BlurView
 * @param cornerRadius BlurView的圆角, 注意不需要 dp转换，内部处理。
 * @param blurRadius 模糊半径
 */
class BlurViewEx3(private val blurView: BlurView,
                  private val cornerRadius: Int,
                  private val blurRadius: Float) {
    val isLegacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    val cornerRadiusDp = cornerRadius.toFloat().dp

    val viewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusDp)
        }
    }

    /**
     * 设置常规模糊
     *
     @param target 模糊目标
     @param legacyBgColor 过时的替代颜色
     */
    fun setBlur(target: BlurTarget, legacyBgColor:Int) {
        if (!isLegacy) {
            blurView.setupWith(target)
                .setBlurRadius(blurRadius)
            blurView.outlineProvider = viewOutlineProvider
            blurView.clipToOutline = true
        } else {
            ViewBackgroundBuilder()
                .setBackground(legacyBgColor)
                .setCornerRadius(cornerRadiusDp)
                .build()?.let {
                    blurView.background = it
                }
        }
    }

    /**
     * 设置渐进模糊
     *
     * @param target 模糊目标
     * @param direction 模糊方向
     * @param applyNoise 是否应用噪点，推荐false，否则会有明显的分界线
     * @param legacyBgStartColor 过时的替代开始颜色
     * @param legacyBgEndColor 过时的替代结束颜色
     */
    fun setProgressiveBlur(target: BlurTarget, direction: Int, applyNoise:Boolean = false,
                          legacyBgStartColor:Int, legacyBgEndColor:Int) {
        if (!isLegacy) {
            blurView.setupWith(target, BlurController.DEFAULT_SCALE_FACTOR, applyNoise)
                .setBlurGradient(direction)
                .setBlurRadius(blurRadius)

            blurView.outlineProvider = viewOutlineProvider
            blurView.clipToOutline = true
        } else {
            ViewBackgroundBuilder()
                .setGradient(legacyBgStartColor, legacyBgEndColor, getAngle(direction))
                .setCornerRadius(cornerRadiusDp)
                .build()?.let {
                    blurView.background = it
                }
        }
    }

    private fun getAngle(direction: Int) = when (direction) {
        BlurView.GRADIENT_TOP_TO_BOTTOM -> {270}
        BlurView.GRADIENT_BOTTOM_TO_TOP -> {90}
        BlurView.GRADIENT_LEFT_TO_RIGHT -> {0}
        BlurView.GRADIENT_RIGHT_TO_LEFT -> {180}
        else -> 0
    }
}