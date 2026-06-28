package com.au.module_androidui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import com.au.module_androidui.R;
import com.google.android.material.imageview.ShapeableImageView;

/**
 * 固定宽度、高度随原图比例变化的图片区域。
 */
public class FixedWidthImageView extends ShapeableImageView {
    private static final float DEFAULT_MIN_RATIO = 0.5f;
    private static final float DEFAULT_MAX_RATIO = 2.0f;

    private float minAspectRatio;
    private float maxAspectRatio;
    private int fixedImageWidth;
    private int fixedImageHeight;
    private boolean shouldNotifyMeasuredSize;
    private boolean settingImageResource;
    private int lastNotifyWidth;
    private int lastNotifyHeight;
    private OnFinalSizeChangedListener onFinalSizeChangedListener;

    /**
     * 图片最终测量尺寸变化监听。
     */
    public interface OnFinalSizeChangedListener {
        /**
         * 图片最终测量尺寸变化。
         */
        void onFinalSizeChanged(int width, int height);
    }

    public FixedWidthImageView(Context context) {
        super(context);
        init(null);
    }

    public FixedWidthImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public FixedWidthImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        setAdjustViewBounds(false);
        setScaleType(ScaleType.CENTER_CROP);
        if (attrs == null) {
            minAspectRatio = DEFAULT_MIN_RATIO;
            maxAspectRatio = DEFAULT_MAX_RATIO;
            return;
        }
        TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FixedWidthImageView);
        try {
            minAspectRatio = a.getFloat(R.styleable.FixedWidthImageView_minAspectRatio, DEFAULT_MIN_RATIO);
            maxAspectRatio = a.getFloat(R.styleable.FixedWidthImageView_maxAspectRatio, DEFAULT_MAX_RATIO);
        } finally {
            a.recycle();
        }
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        Drawable oldDrawable = getDrawable();
        int oldIntrinsicWidth = oldDrawable == null ? 0 : oldDrawable.getIntrinsicWidth();
        int oldIntrinsicHeight = oldDrawable == null ? 0 : oldDrawable.getIntrinsicHeight();
        boolean oldShouldNotifyMeasuredSize = shouldNotifyMeasuredSize;
        shouldNotifyMeasuredSize = !settingImageResource && drawable != null;
        super.setImageDrawable(drawable);
        requestLayoutIfImageInfoChanged(oldIntrinsicWidth, oldIntrinsicHeight, oldShouldNotifyMeasuredSize);
    }

    @Override
    public void setImageBitmap(Bitmap bm) {
        Drawable oldDrawable = getDrawable();
        int oldIntrinsicWidth = oldDrawable == null ? 0 : oldDrawable.getIntrinsicWidth();
        int oldIntrinsicHeight = oldDrawable == null ? 0 : oldDrawable.getIntrinsicHeight();
        boolean oldShouldNotifyMeasuredSize = shouldNotifyMeasuredSize;
        shouldNotifyMeasuredSize = bm != null;
        super.setImageBitmap(bm);
        requestLayoutIfImageInfoChanged(oldIntrinsicWidth, oldIntrinsicHeight, oldShouldNotifyMeasuredSize);
    }

    @Override
    public void setImageResource(int resId) {
        Drawable oldDrawable = getDrawable();
        int oldIntrinsicWidth = oldDrawable == null ? 0 : oldDrawable.getIntrinsicWidth();
        int oldIntrinsicHeight = oldDrawable == null ? 0 : oldDrawable.getIntrinsicHeight();
        boolean oldShouldNotifyMeasuredSize = shouldNotifyMeasuredSize;
        settingImageResource = true;
        shouldNotifyMeasuredSize = false;
        try {
            super.setImageResource(resId);
        } finally {
            settingImageResource = false;
        }
        requestLayoutIfImageInfoChanged(oldIntrinsicWidth, oldIntrinsicHeight, oldShouldNotifyMeasuredSize);
    }

    /**
     * 设置缓存尺寸，后续测量直接使用该尺寸比例。
     */
    public void setFixedImageSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            clearFixedImageSize();
            return;
        }
        if (fixedImageWidth == width && fixedImageHeight == height) {
            return;
        }
        fixedImageWidth = width;
        fixedImageHeight = height;
        requestLayout();
    }

    /**
     * 清除缓存尺寸，恢复按 drawable 比例测量。
     */
    public void clearFixedImageSize() {
        if (fixedImageWidth == 0 && fixedImageHeight == 0) {
            return;
        }
        fixedImageWidth = 0;
        fixedImageHeight = 0;
        requestLayout();
    }

    /**
     * 设置图片最终测量尺寸变化监听。
     */
    public void setOnFinalSizeChangedListener(OnFinalSizeChangedListener listener) {
        if (onFinalSizeChangedListener == listener) {
            return;
        }
        onFinalSizeChangedListener = listener;
        lastNotifyWidth = 0;
        lastNotifyHeight = 0;
    }

    private void requestLayoutIfImageInfoChanged(int oldIntrinsicWidth, int oldIntrinsicHeight, boolean oldShouldNotifyMeasuredSize) {
        Drawable drawable = getDrawable();
        int intrinsicWidth = drawable == null ? 0 : drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable == null ? 0 : drawable.getIntrinsicHeight();
        if (oldIntrinsicWidth != intrinsicWidth
                || oldIntrinsicHeight != intrinsicHeight
                || oldShouldNotifyMeasuredSize != shouldNotifyMeasuredSize) {
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode != MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int fixedWidth = MeasureSpec.getSize(widthMeasureSpec);
        int targetHeight = calculateHeightByFixedSize(fixedWidth);
        if (targetHeight <= 0) {
            targetHeight = calculateHeight(fixedWidth);
        }
        int newHeightMeasureSpec = MeasureSpec.makeMeasureSpec(targetHeight, MeasureSpec.EXACTLY);
        super.onMeasure(widthMeasureSpec, newHeightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        notifyFinalSizeIfNeeded();
    }

    private int calculateHeightByFixedSize(int fixedWidth) {
        if (fixedImageWidth <= 0 || fixedImageHeight <= 0) {
            return 0;
        }
        if (fixedImageWidth == fixedWidth) {
            return fixedImageHeight;
        }
        return Math.round((float) fixedWidth * (float) fixedImageHeight / (float) fixedImageWidth);
    }

    private int calculateHeight(int fixedWidth) {
        Drawable drawable = getDrawable();
        boolean hasValidDrawable = drawable != null
                && drawable.getIntrinsicWidth() > 0
                && drawable.getIntrinsicHeight() > 0;
        if (!hasValidDrawable) {
            return fixedWidth;
        }

        float ratio = (float) drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight();
        if (ratio < minAspectRatio) {
            ratio = minAspectRatio;
        } else if (ratio > maxAspectRatio) {
            ratio = maxAspectRatio;
        }
        return (int) (fixedWidth / ratio);
    }

    private void notifyFinalSizeIfNeeded() {
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        if (!shouldNotifyMeasuredSize || onFinalSizeChangedListener == null || width <= 0 || height <= 0) {
            return;
        }
        if (lastNotifyWidth == width && lastNotifyHeight == height) {
            return;
        }
        lastNotifyWidth = width;
        lastNotifyHeight = height;
        onFinalSizeChangedListener.onFinalSizeChanged(width, height);
    }
}
