package com.au.module_android.glide

import com.bumptech.glide.load.model.GlideUrl

/**
 * 4种处理签名URL的实用方法
 * 方法1：使用ObjectKey签名
 * 这是处理动态URL最常用的方法。你可以为每个URL版本创建一个唯一的签名：
 *
 * Glide.with(context)
 *     .load(imageUrl)
 *     .signature(new ObjectKey(urlSignature))
 *     .into(imageView);
 * 方法2：MediaStoreSignature
 * 如果你的图片来自媒体库，可以使用MediaStoreSignature：
 * .signature(new MediaStoreSignature(mimeType, dateModified, orientation)
 *
 * 方法3：应用版本签名
 * 确保应用更新时缓存得到更新：
 * .signature(ApplicationVersionSignature.obtain(context))
 *
 * 方法4： CustomGlideUrl
 */
class KeyedGlideUrl(
                         private val customKey:String, url: String?) : GlideUrl(url) {
    override fun getCacheKey(): String {
        return customKey
    }
}