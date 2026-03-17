/*  Created on 2026/03/11.
* Copyright (C) 2026 @jzlhll. All rights reserved.
*
* Licensed under the MIT License.
* See LICENSE file in the project root for full license information.
*/
package com.au.module_imagecompressed.loader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import kotlin.math.ceil
import kotlin.math.min

/**
 * 独立的图片加载工具类，专注于使用 ImageDecoder 实现加载并裁剪为正方形 Bitmap。
 *
 * 不依赖项目其他组件，自包含逻辑。
 * 注意：ImageDecoder 仅在 Android P (API 28) 及以上版本可用。
 */
object SquareImageLoader {

    /**
     * 使用 ImageDecoder 加载图片，并直接缩放、裁剪为指定边长的正方形。
     *
     * 逻辑说明：
     * 1. 计算缩放比例：确保图片的短边长度至少等于 [targetSide]。
     * 2. 设置目标尺寸 (setTargetSize)：使用向上取整 (ceil) 避免浮点数精度导致的尺寸不足。
     * 3. 设置裁剪区域 (setCrop)：在缩放后的图片中心截取 [targetSide] * [targetSide] 的区域。
     *
     * @param context 上下文
     * @param uri 图片 Uri
     * @param targetSide 目标正方形边长（像素）
     * @param ignoreSide 如果图片的原始宽或高小于此值，则直接忽略（返回 null）。默认为 null，表示不限制。
     * @return 裁剪后的 Bitmap，如果发生异常、或尺寸不满足要求则返回 null
     */
    fun load(context: Context, uri: Uri, targetSide: Int, ignoreSide: Int? = null): Bitmap? {
        return try {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val originW = info.size.width
                val originH = info.size.height

                // 0. 尺寸检查：如果指定了 ignoreSide，且任一边长小于该值，则取消加载
                if (ignoreSide != null && (originW < ignoreSide || originH < ignoreSide)) {
                    // ImageDecoder 没有直接的 cancel 机制，通过设置极小尺寸或抛出异常来中断不太优雅
                    // 但 onHeaderDecoded 回调中如果抛出异常会被 catch 住
                    // 这里我们选择通过 targetSize 设置为 1x1 并在后续判断中处理，或者直接抛出自定义异常中断解码
                    // 更简单的做法是：利用 decodeBitmap 的返回值。但 decodeBitmap 是阻塞的。
                    // 实际上，在 onHeaderDecoded 里我们无法直接 "return null" 给 decodeBitmap。
                    // 抛出异常是中断解码的标准方式。
                    throw IllegalStateException("Image size ($originW x $originH) is too small, ignored by ignoreSide=$ignoreSide")
                }

                // 1. 计算缩放比例：保证短边 >= targetSide
                // scale = target / min(w, h)
                val scale = targetSide.toFloat() / min(originW, originH)

                // 2. 计算缩放后的目标尺寸，使用 ceil 向上取整确保尺寸只大不小
                val scaledW = ceil(originW * scale).toInt()
                val scaledH = ceil(originH * scale).toInt()

                decoder.setTargetSize(scaledW, scaledH)

                // 3. 计算居中裁剪区域
                val cropX = (scaledW - targetSide) / 2
                val cropY = (scaledH - targetSide) / 2

                // setCrop 的 Rect 决定了最终输出 Bitmap 的尺寸，这里严格等于 targetSide
                decoder.crop = Rect(cropX, cropY, cropX + targetSide, cropY + targetSide)

                // 4. 配置 Bitmap 属性
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // 软件渲染，兼容性更好
                decoder.isMutableRequired = true // 允许修改
            }
        } catch (_: Exception) {
            //e.printStackTrace()
            null
        }
    }
}
