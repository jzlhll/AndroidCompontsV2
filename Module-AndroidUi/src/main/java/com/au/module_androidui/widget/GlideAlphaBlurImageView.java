package com.au.module_androidui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.au.module_androidui.R;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import jp.wasabeef.glide.transformations.BlurTransformation;

public class GlideAlphaBlurImageView extends androidx.appcompat.widget.AppCompatImageView {
    @ColorInt
    public static final int ALPHA_WHITE       = 0xcFFFFFFF;

    /**
     * 如下3个可以通过attr来修改
     */
    private int mBlurBgColor = ALPHA_WHITE;
    private int mBlurRadius = 50;
    private int mBlurSampling = 6;

    private boolean isDrawn = false;

    public GlideAlphaBlurImageView(@NonNull Context context) {
        this(context, null);
    }

    public GlideAlphaBlurImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GlideAlphaBlurImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initAttrs(attrs);
    }

    private void initAttrs(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray typedArray = getContext().obtainStyledAttributes(attrs, R.styleable.GlideAlphaBlurImageView);
            try {
                mBlurBgColor = typedArray.getColor(R.styleable.GlideAlphaBlurImageView_blurBgColor, ALPHA_WHITE);
                mBlurRadius = typedArray.getInt(R.styleable.GlideAlphaBlurImageView_blurRadius, 50);
                mBlurSampling = typedArray.getInt(R.styleable.GlideAlphaBlurImageView_blurSampling, 6);
            } finally {
                typedArray.recycle();
            }
        }
    }

    public ColorDrawable blurBgDrawable() {
        return new ColorDrawable(mBlurBgColor);
    }

    /**
     * 如果界面是一开始是不可见的；则不用调用这个函数；
     * 当第一次可见时，才调用这个函数
     */
    public void drawBlur() {
        if (isDrawn) {
            return;
        }
        isDrawn = true;
        Glide.with(this).load(blurBgDrawable())
                .apply(RequestOptions.bitmapTransform(new BlurTransformation(mBlurRadius, mBlurSampling)))
                .into(this);
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && changedView == this) {
            drawBlur();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) {
            drawBlur();
        }
    }
}