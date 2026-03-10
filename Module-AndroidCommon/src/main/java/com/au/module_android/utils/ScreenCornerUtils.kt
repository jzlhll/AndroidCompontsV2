/*
* Created by jiangzhonglun@imagecho.ai on 2026/03/10.
*
* Copyright (C) 2026 [imagecho.ai]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@imagecho.ai]
*/

package com.au.module_android.utils

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue

class ScreenCornerUtils {
    companion object {
        /**
         * 获取系统屏幕边缘的圆角像素值
         * @param context 上下文
         * @return 圆角像素值（如果获取失败返回0）
         */
        fun getScreenCornerRadius(context: Context): Float {
            val resources: Resources = context.resources
            val packageName = "android" // 系统资源包名

            // 不同Android版本的系统属性名（优先级从高到低）
            val cornerRadiusAttrs = listOf(
                "config_screenCornerRadius", // 主流版本（Android 10+ 通用）
                "status_bar_round_corner_radius", // 部分厂商（如小米、华为）的兼容属性
                "rounded_corner_radius" // 早期版本/小众厂商属性
            )

            // 方式1：通过系统属性ID获取（推荐）
            for (attrName in cornerRadiusAttrs) {
                val attrId = resources.getIdentifier(attrName, "dimen", packageName)
                if (attrId > 0) {
                    return try {
                        // 获取原始尺寸（可能是dp/sp）并转换为像素
                        TypedValue().apply {
                            resources.getValue(attrId, this, true)
                        }.getDimension(resources.displayMetrics)
                    } catch (_: Exception) {
                        // 异常时尝试直接获取像素值
                        resources.getDimensionPixelSize(attrId).toFloat()
                    }
                }
            }

            // 方式2：Android 12+ 新增的WindowManager方式（备用）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return try {
                    val windowManager = context.getSystemService(Context.WINDOW_SERVICE)
                    val method = windowManager.javaClass.getMethod("getDefaultDisplayRoundCornerRadius")
                    method.invoke(windowManager) as Float
                } catch (e: Exception) {
                    0f
                }
            }

            // 所有方式都失败时返回0
            return 0f
        }
    }
}