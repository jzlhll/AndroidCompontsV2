/* 
* Created by jiangzhonglun@imagecho.ai on 2026/03/17.
*
* Copyright (C) 2026 [imagecho.ai]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@imagecho.ai]
*/

package com.au.module_androidui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.google.android.material.imageview.ShapeableImageView;

/**
 * 固定高度的ImageView：
 * 1. 默认开启adjustViewBounds
 * 2. 宽度适配图片比例，且可通过attrs设置宽度范围（高度的minRatio ~ maxRatio倍）
 * 3. 需在布局中给layout_height设置固定值（如100dp）或match_parent
 */
public class FixedHeightImageView extends ShapeableImageView {

    // 成员变量：从attrs解析，默认值0.5/2.0
    private float mMinWidthRatio;
    private float mMaxWidthRatio;

    // 默认比例值
    private static final float DEFAULT_MIN_RATIO = 0.65f;
    private static final float DEFAULT_MAX_RATIO = 2.0f;

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
     * 初始化：解析自定义属性 + 开启adjustViewBounds
     */
    private void init(AttributeSet attrs) {
        // 1. 强制默认开启adjustViewBounds
        setAdjustViewBounds(true);

        // 2. 解析自定义属性（核心修改）
        if (attrs != null) {
            TypedValue typedValue = new TypedValue();
            // 解析最小比例：有传入则用传入值，否则用默认值
            if (attrs.getAttributeFloatValue(null, "minWidthRatio", typedValue.getFloat()) != 0) {
                mMinWidthRatio = attrs.getAttributeFloatValue(null, "minWidthRatio", DEFAULT_MIN_RATIO);
            } else {
                mMinWidthRatio = DEFAULT_MIN_RATIO;
            }
            // 解析最大比例
            if (attrs.getAttributeFloatValue(null, "maxWidthRatio", typedValue.getFloat()) != 0) {
                mMaxWidthRatio = attrs.getAttributeFloatValue(null, "maxWidthRatio", DEFAULT_MAX_RATIO);
            } else {
                mMaxWidthRatio = DEFAULT_MAX_RATIO;
            }
        } else {
            // 无attrs时用默认值
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
        // 2. 计算宽度的上下限：高度 * 自定义比例（核心：替换硬编码为解析的属性值）
        int minWidth = (int) (fixedHeight * mMinWidthRatio);
        int maxWidth = (int) (fixedHeight * mMaxWidthRatio);

        // 3. 先按adjustViewBounds逻辑计算适配图片比例的目标宽度
        int targetWidth = calculateAdjustedWidth(fixedHeight);

        // 4. 约束目标宽度在 [minWidth, maxWidth] 范围内
        targetWidth = Math.max(minWidth, Math.min(targetWidth, maxWidth));

        // 5. 构建新的宽度MeasureSpec
        int newWidthMeasureSpec = MeasureSpec.makeMeasureSpec(targetWidth, MeasureSpec.EXACTLY);

        // 6. 调用父类测量
        super.onMeasure(newWidthMeasureSpec, heightMeasureSpec);
    }

    /**
     * 复用adjustViewBounds的核心逻辑：根据图片比例计算适配的宽度
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