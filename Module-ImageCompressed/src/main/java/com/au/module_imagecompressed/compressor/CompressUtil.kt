package com.au.module_imagecompressed.compressor

import kotlin.math.roundToInt

class CompressUtil {
    companion object {
        /**
         * 根据文件大小选择压缩质量。适配现在的需求
         */
        fun chooseQuality(size: Long, qualityType: String): Int {
            when (qualityType) {
                "deep" -> {
                    return when {
                        size > 16_000_000 -> 55
                        size > 8_000_000 -> 65
                        size > 4_000_000 -> 78
                        size > 2_000_000 -> 82
                        size > 1_000_000 -> 88
                        else -> 100
                    }
                }
                "shallow"-> {
                    return when {
                        size > 16_000_000 -> 80
                        size > 8_000_000 -> 85
                        size > 4_000_000 -> 90
                        size > 2_000_000 -> 95
                        size > 1_000_000 -> 98
                        else -> 100
                    }
                }
                else -> {
                    return when {
                        size > 16_000_000 -> 70
                        size > 8_000_000 -> 80
                        size > 4_000_000 -> 85
                        size > 2_000_000 -> 90
                        size > 1_000_000 -> 95
                        else -> 100
                    }
                }
            }
        }

        fun calculateSampleSize(
            origW: Int,
            origH: Int,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            var inSample = 1
            if (origH > reqHeight || origW > reqWidth) {
                val heightRatio = (origH.toFloat() / reqHeight).roundToInt()
                val widthRatio = (origW.toFloat() / reqWidth).roundToInt()
                inSample = minOf(heightRatio, widthRatio)
            }

            val totalPixels = origW * origH.toFloat()
            val cap = reqWidth * reqHeight * 2f
            while (totalPixels / (inSample * inSample) > cap) {
                inSample++
            }
            return inSample
        }

        fun calculateTargetDimensions(
            width: Int,
            height: Int,
            maxWidth: Float,
            maxHeight: Float
        ): Pair<Int, Int> {
            var w = width.toFloat()
            var h = height.toFloat()

            val imgRatio = w / h
            val maxRatio = maxWidth / maxHeight

            if (h > maxHeight || w > maxWidth) {
                if (imgRatio < maxRatio) {
                    val scale = maxHeight / h
                    w *= scale
                    h = maxHeight
                } else if (imgRatio > maxRatio) {
                    val scale = maxWidth / w
                    h *= scale
                    w = maxWidth
                } else {
                    w = maxWidth
                    h = maxHeight
                }
            }
            return w.roundToInt() to h.roundToInt()
        }
    }
}