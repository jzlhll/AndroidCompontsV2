package com.au.module_android.utilsandroid

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import java.lang.reflect.Field

class StatusBarUtils {
    companion object {
        const val STATUS_BAR_DEFAULT_HEIGHT_DP: Int = 25 // 大部分状态栏都是25dp
        // 在某些机子上存在不同的density值，所以增加两个虚拟值
        var sVirtualDensity: Float = -1f
        var sVirtualDensityDpi: Float = -1f

        fun getNavigationHeight(): Int {
            return try {
                val res = Resources.getSystem()

                @SuppressLint("InternalInsetResource", "DiscouragedApi")
                val resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android")
                var height = 0
                if (resourceId != 0) {
                    height = res.getDimensionPixelSize(resourceId)
                }
                height
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }

        fun getStatusBarHeight(): Int {
            return try {
                val res = Resources.getSystem()

                @SuppressLint("InternalInsetResource", "DiscouragedApi")
                val resourceId = res.getIdentifier("status_bar_height", "dimen", "android")
                var height = 0
                if (resourceId != 0) {
                    height = res.getDimensionPixelSize(resourceId)
                }
                return height
            } catch (e: Exception) {
                0
            }
        }

        fun getStatusBarHeight(context: Context) : Int{
            val height = getStatusBarHeight()
            return if (height > 0) {
                height
            } else {
                getStatusBarHeight2(context)
            }
        }

        private fun getStatusBarHeight2(context: Context): Int {
            var sStatusbarHeight = 0
            val clazz: Class<*>?
            var obj: Any? = null
            var field: Field? = null
            try {
                clazz = Class.forName("com.android.internal.R\$dimen")
                obj = clazz.newInstance()
                if (XDeviceHelper.isMeizu()) {
                    try {
                        field = clazz.getField("status_bar_height_large")
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
                if (field == null) {
                    field = clazz.getField("status_bar_height")
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
            if (field != null && obj != null) {
                try {
                    val id = field.get(obj)?.toString()?.toInt()
                    if (id != null) {
                        sStatusbarHeight = context.resources.getDimensionPixelSize(id)
                    }
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
            if (XDeviceHelper.isTablet(context)
                && sStatusbarHeight > dp2px(context, STATUS_BAR_DEFAULT_HEIGHT_DP)
            ) {
                //状态栏高度大于25dp的平板，状态栏通常在下方
                sStatusbarHeight = 0
            } else {
                if (sStatusbarHeight <= 0) {
                    if (sVirtualDensity == -1f) {
                        sStatusbarHeight = dp2px(context, STATUS_BAR_DEFAULT_HEIGHT_DP)
                    } else {
                        sStatusbarHeight = (STATUS_BAR_DEFAULT_HEIGHT_DP * sVirtualDensity + 0.5f).toInt()
                    }
                }
            }
            return sStatusbarHeight
        }

        /**
         * 将px值转换为dp值
         */
        fun px2dp(context: Context, pxValue: Int): Int {
            val scale = context.resources.displayMetrics.density
            return (pxValue / scale + 0.5f).toInt()
        }

        /**
         * 将dp值转换为px值
         */
        fun dp2px(context: Context, dpValue: Int): Int {
            val scale = context.resources.displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }
    }
}