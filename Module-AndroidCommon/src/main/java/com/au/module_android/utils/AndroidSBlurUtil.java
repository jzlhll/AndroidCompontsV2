package com.au.module_android.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;

/**
 * 不得改成kotlin
 */
public final class AndroidSBlurUtil {
    private static final boolean isS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

    private AndroidSBlurUtil() {}

    @ColorInt
    private static final int LIGHT_BLUR_COLOR = isS ? 0xcfFFFFFF : 0xeeffffff;
    @ColorInt
    private static final int DARK_BLUR_COLOR = isS ? 0xcc000000 : 0xd8000000;

    private static final int DEFAULT_RADIUS_DARK = 12;
    private static final int DEFAULT_RADIUS_LIGHT = 24;

    private static boolean isDark(Context context) {
        var mode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applyBlurEffect(
            ViewGroup blurView
    ) {
        var isDark = isDark(blurView.getContext());
        var radius = isDark ? DEFAULT_RADIUS_DARK : DEFAULT_RADIUS_LIGHT;
        applyBlurEffect(blurView, radius, radius, Shader.TileMode.MIRROR, isDark);
    }

    /**
     * 对View应用模糊效果
     * @param beBlurView 要应用模糊效果的View
     * @param radiusX X轴模糊半径
     * @param radiusY Y轴模糊半径
     */
    public static void applyBlurEffect(
            View beBlurView,
            float radiusX,
            float radiusY
    ) {
        var isDark = isDark(beBlurView.getContext());
        if (isS) {
            applyBlurEffect(beBlurView, radiusX, radiusY, Shader.TileMode.DECAL, isDark);
        }
    }

    public static void applyBlurEffect(
            View beBlurView,
            float radiusX,
            float radiusY,
            Shader.TileMode tileMode
    ) {
        var isDark = isDark(beBlurView.getContext());
        applyBlurEffect(beBlurView, radiusX, radiusY, tileMode, isDark);
    }

    /**
     * 对View应用模糊效果
     * @param beBlurView 要应用模糊效果的View
     * @param radiusX X轴模糊半径
     * @param radiusY Y轴模糊半径
     * @param tileMode 边缘处理模式
     */
    private static void applyBlurEffect(
            View beBlurView,
            float radiusX,
            float radiusY,
            Shader.TileMode tileMode,
            boolean isDark
    ) {
        var baseColor = isDark ? DARK_BLUR_COLOR : LIGHT_BLUR_COLOR;
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(baseColor);
        gradientDrawable.setCornerRadius(20f); // 设置圆角半径
        beBlurView.setForeground(gradientDrawable);

        beBlurView.setClipToOutline(true); //保持形状，如果不添加圆角将模糊后丢失

        if (isS) {
            var blurEffect = RenderEffect.createBlurEffect(radiusX, radiusY, tileMode);
            beBlurView.setRenderEffect(blurEffect);
        }
    }

    /**
     * 清除View的模糊效果
     */
    public static void clearBlurEffect(View beBlurView) {
        beBlurView.setForeground(null);
        if (isS) {
            beBlurView.setRenderEffect(null);
        }
    }
}
