package com.au.module_android.glide

import android.graphics.Bitmap
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.util.Util

/** 委托原生池，仅阻止快速图复用分配空间大于实际像素需求的 Bitmap。 */
class ExactSmallBitmapPool(
    private val delegate: BitmapPool,
    private val policy: TinyThumbnailPolicy = TinyThumbnailPolicy.DEFAULT,
) : BitmapPool by delegate {
    /** 返回清零的 Bitmap，保持原生 get 语义。 */
    override fun get(width: Int, height: Int, config: Bitmap.Config?): Bitmap {
        return exactSmallBitmap(delegate.get(width, height, config), width, height, config)
    }

    /** 返回允许包含旧像素的 Bitmap，保持原生 getDirty 语义。 */
    override fun getDirty(width: Int, height: Int, config: Bitmap.Config?): Bitmap {
        return exactSmallBitmap(delegate.getDirty(width, height, config), width, height, config)
    }

    // 候选归还原生池；新建 Bitmap 自带透明像素，同时满足 get 和 getDirty 的契约。
    private fun exactSmallBitmap(bitmap: Bitmap, width: Int, height: Int, config: Bitmap.Config?): Bitmap {
        val requestedConfig = config ?: Bitmap.Config.ARGB_8888
        if (width > policy.maxDimension || height > policy.maxDimension ||
            bitmap.allocationByteCount <= Util.getBitmapByteSize(width, height, requestedConfig)
        ) {
            return bitmap
        }
        delegate.put(bitmap)
        return Bitmap.createBitmap(width, height, requestedConfig)
    }
}
