package com.au.module_android.glide

import com.bumptech.glide.load.DecodeFormat

/** 极小图请求、缓存分类与 Bitmap 复用共用固定像素上限，不依赖来源或展示尺寸。 */
data class TinyThumbnailPolicy(
    val maxDimension: Int = 51,
    val maxAllocationBytes: Int = 12 * 1024,
    val decodeFormat: DecodeFormat = DecodeFormat.PREFER_RGB_565,
) {
    init {
        require(maxDimension > 0 && maxAllocationBytes > 0)
    }

    companion object {
        val DEFAULT = TinyThumbnailPolicy()
    }
}
