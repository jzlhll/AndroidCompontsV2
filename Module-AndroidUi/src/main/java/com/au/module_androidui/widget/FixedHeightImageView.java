
package com.au.module_androidui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.au.module_androidui.R;
import com.google.android.material.imageview.ShapeableImageView;

/**
 *  固定高度、宽度随原图比例变化的图片区域（ShapeableImageView）。
 * <p>
 * <b>测量</b>：{@code layout_height} 须为 EXACTLY（定值或 {@code match_parent}）。宽度 = 固定高度 × 原图「宽/高」像素比，
 * 再夹紧到 {@code [高度×minWidthRatio, 高度×maxWidthRatio]}（属性表示的是视图「宽/高」相对高度的倍率上下限）。
 * 关闭 {@code adjustViewBounds}，避免与 {@code centerCrop} 叠加导致只算对了尺寸却留白。
 * <p>
 * <b>展示</b>：用原图固有宽高比与 {@code minWidthRatio}/{@code maxWidthRatio} 比较（带一点浮点容差，见 {@code RATIO_EPS} 常量）。
 * 比例落在区间内：{@code scaleType} 为 {@code FIT_CENTER}，此时测量出的宽高比与原图一致，完整、不裁切。
 * 比例超出区间：宽度已被夹到极限，{@code scaleType} 为 {@code CENTER_CROP}，在极限矩形内裁切铺满。
 * <p>
 * <b>与 Glide 配合</b>：对该 View 调用 {@code glideSetAny} 时，应在请求的 {@code RequestOptions} 上链式调用 {@code fitCenter()}
 *（与 Kotlin 中 {@code glideSetAny(uri) \{ it.fitCenter() \}} 等价）。这样 Glide 解码不把画面按 {@code centerCrop} 先裁死，
 * 再由本控件按是否「比例超限」切换 {@link android.widget.ImageView.ScaleType#FIT_CENTER} 与
 * {@link android.widget.ImageView.ScaleType#CENTER_CROP} 做最终铺满或等比完整显示。业务侧若强行用 {@code centerCrop} 解码，
 * xml中也写上fitCenter。
 * 容易与上述逻辑冲突。
 */
public class FixedHeightImageView extends ShapeableImageView {

    // 成员变量：从attrs解析，默认值0.5/2.0
    private float mMinWidthRatio;
    private float mMaxWidthRatio;

    // 默认比例值（宽/高）
    private static final float DEFAULT_MIN_RATIO = 0.65f;
    private static final float DEFAULT_MAX_RATIO = 2.0f;

    /** 与极限倍率比较时的浮点容差 */
    private static final float RATIO_EPS = 1e-4f;

    public FixedHeightImageView(Context context) {
        super(context);
        // 无attrs时用默认值
        init(null);
    }

    public FixedHeightImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public FixedHeightImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    /**
     * 初始化：解析自定义属性；宽度由 onMeasure 计算，不再使用 adjustViewBounds
     */
    private void init(AttributeSet attrs) {
        setAdjustViewBounds(false);

        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.FixedHeightImageView);
            try {
                mMinWidthRatio = a.getFloat(R.styleable.FixedHeightImageView_minWidthRatio, DEFAULT_MIN_RATIO);
                mMaxWidthRatio = a.getFloat(R.styleable.FixedHeightImageView_maxWidthRatio, DEFAULT_MAX_RATIO);
            } finally {
                a.recycle();
            }
        } else {
            mMinWidthRatio = DEFAULT_MIN_RATIO;
            mMaxWidthRatio = DEFAULT_MAX_RATIO;
        }
    }

    /**
     * 重写测量逻辑：约束宽度范围
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 1. 先获取固定的高度（核心：height是固定维度，需确保heightMeasureSpec为EXACTLY模式）
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        
        // 如果高度不是固定值（wrap_content），按原生ImageView逻辑处理
        if (heightMode != MeasureSpec.EXACTLY) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int fixedHeight = MeasureSpec.getSize(heightMeasureSpec);
        // 2. 计算宽度的上下限：高度 * 自定义比例
        int minWidth = (int) (fixedHeight * mMinWidthRatio);
        int maxWidth = (int) (fixedHeight * mMaxWidthRatio);

        // 3. 按原图比例得到自然宽度，并判断是否因超出 min/max 倍率而被压缩到极限宽度
        int naturalWidth = calculateAdjustedWidth(fixedHeight);
        boolean drawableOk = getDrawable() != null
                && getDrawable().getIntrinsicWidth() > 0
                && getDrawable().getIntrinsicHeight() > 0;
        boolean ratioClamped = false;
        if (drawableOk) {
            float r = (float) getDrawable().getIntrinsicWidth() / (float) getDrawable().getIntrinsicHeight();
            ratioClamped = (r < mMinWidthRatio - RATIO_EPS) || (r > mMaxWidthRatio + RATIO_EPS);
        }
        applyScaleTypeForClamp(drawableOk, ratioClamped);

        int targetWidth = Math.max(minWidth, Math.min(naturalWidth, maxWidth));

        int newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY);
        super.onMeasure(newWidthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 仅在原图宽高比超出配置区间时使用 centerCrop；否则 fitCenter，保持与原图比例一致的完整展示
     */
    private void applyScaleTypeForClamp(boolean hasValidDrawable, boolean ratioClamped) {
        ScaleType desired = !hasValidDrawable
                ? ScaleType.FIT_CENTER
                : (ratioClamped ? ScaleType.CENTER_CROP : ScaleType.FIT_CENTER);
        if (getScaleType() != desired) {
            setScaleType(desired);
        }
    }

    /**
     * 根据原图固有宽高比与固定高度计算未 clamp 时的目标宽度（像素取整）
     */
    private int calculateAdjustedWidth(int fixedHeight) {
        // 无图片时，默认用高度的1倍作为宽度
        if (getDrawable() == null || getDrawable().getIntrinsicWidth() <= 0 || getDrawable().getIntrinsicHeight() <= 0) {
            return fixedHeight;
        }

        // 获取图片原始宽高比
        float drawableRatio = (float) getDrawable().getIntrinsicWidth() / getDrawable().getIntrinsicHeight();
        // 根据固定高度和图片比例，计算适配的宽度
        return (int) (fixedHeight * drawableRatio);
    }

    // 可选：提供代码中动态修改比例的方法
    public void setWidthRatio(float minRatio, float maxRatio) {
        this.mMinWidthRatio = minRatio;
        this.mMaxWidthRatio = maxRatio;
        // 修改后触发重新测量
        requestLayout();
    }
}