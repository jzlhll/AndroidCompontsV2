package com.allan.androidlearning.picwall;

import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import android.graphics.Outline
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import com.au.module_imagecompressed.blur.BlurTarget
import com.au.module_imagecompressed.blur.BlurView

/**
 * 模糊背景
 * @param blurView BlurView
 * @param cornerRadius BlurView的圆角, 注意不需要 dp转换，已经处理。
 */
class BlurViewEx3(private val blurView: BlurView,
                  private val cornerRadius: Int) {
    val isNotLegacy = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val viewOutlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerRadius.toFloat().dp)
        }
    }

    fun setBlur(target: BlurTarget, blurRadius:Float, overlayColor:Int) {
        if (isNotLegacy) { //高版本使用模糊，低版本纯半透
//float radius = 20f;
//
//    View decorView = getWindow().getDecorView();
//    // A view hierarchy you want blur. The BlurTarget can't include the BlurView that targets it.
//    BlurTarget target = findViewById(R.id.target);
//
//    // Optional:
//    // Set the drawable to draw in the beginning of each blurred frame.
//    // Can be used in case your layout has a lot of transparent space and your content
//    // gets a low alpha value after blur is applied.
//    Drawable windowBackground = decorView.getBackground();
//
//    // Optionally pass a custom BlurAlgorithm and scale factor as additional parameters.
//    // You might want to set a smaller scale factor on API 31+ to have a more precise blur with less flickering.
            blurView.setupWith(target)
                .setOverlayColor(overlayColor)
                //.setFrameClearDrawable(windowBackground) // Optional. Useful when your root has a lot of transparent background, which results in semi-transparent blurred content. This will make the background opaque
                .setBlurRadius(blurRadius)

            blurView.outlineProvider = viewOutlineProvider
            blurView.clipToOutline = true
        } else {
            ViewBackgroundBuilder()
            .setBackground(overlayColor)
            .setCornerRadius(cornerRadius.toFloat().dp)
            .build()?.let {
                blurView.background = it
            }
        }
    }
}