package com.au.module_androidui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 高斯模糊容器布局
 * - 只允许添加一个content内容
 * - 使用Glide作为技术依赖实现模糊效果
 * - 通过showBlur和hideBlur方法控制模糊层的显示与隐藏
 */
public class AlphaBlurFrameLayout extends FrameLayout {
    private GlideAlphaBlurImageView mBlurImageView;

    public AlphaBlurFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public AlphaBlurFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AlphaBlurFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void addView(View child) {
        if (getChildCount() >= 1) {
            throw new IllegalStateException("AlphaBlurFrameLayout can only have one child view");
        }
        super.addView(child);
    }

    @Override
    public void addView(View child, int index) {
        if (getChildCount() >= 1) {
            throw new IllegalStateException("AlphaBlurFrameLayout can only have one child view");
        }
        super.addView(child, index);
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (getChildCount() >= 1) {
            throw new IllegalStateException("AlphaBlurFrameLayout can only have one child view");
        }
        super.addView(child, params);
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (getChildCount() >= 1) {
            throw new IllegalStateException("AlphaBlurFrameLayout can only have one child view");
        }
        super.addView(child, index, params);
    }

    @Override
    public void addView(View child, int width, int height) {
        if (getChildCount() >= 1) {
            throw new IllegalStateException("AlphaBlurFrameLayout can only have one child view");
        }
        super.addView(child, width, height);
    }

    /**
     * 显示模糊效果
     * 创建一个完全匹配父容器大小的GlideAlphaBlurImageView并添加到布局中
     */
    public void showBlur() {
        if (mBlurImageView == null) {
            mBlurImageView = new GlideAlphaBlurImageView(getContext());
            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT);
            super.addView(mBlurImageView, params);
        }
        mBlurImageView.drawBlur();
    }

    /**
     * 隐藏模糊效果
     * 移除添加的GlideAlphaBlurImageView
     */
    public void hideBlur() {
        if (mBlurImageView != null) {
            super.removeView(mBlurImageView);
        }
    }
}