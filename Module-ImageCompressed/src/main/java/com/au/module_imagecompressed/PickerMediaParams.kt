package com.au.module_imagecompressed

import com.au.module_imagecompressed.compressor.BestImageCompressor

class PickerMediaParams private constructor(
    val alwaysCopyImage: Boolean,
    val alwaysCopyVideo: Boolean,
    val limitImageSize: Int,   //图片最大限制，将不会做转换。如果传入了compressEngine，则会允许2倍的size进行压缩
    val targetImageSize: Int,
    val limitVideoSize: Long,  //视频最大限制，将不会做转换
    val ignoreSizeKb: Int,
    val compressEngine: ICompressEngine? = null //如果传入空，则不做转换
) {
    fun needCompress() = compressEngine != null

    class Builder {
        private var limitImageSize = 50 * 1024 * 1024
        private var targetImageSize = 5 * 1024 * 1024
        private var limitVideoSize = 500 * 1024 * 1024L
        private var ignoreSizeKb = 5 * 1024 * 1024

        private var alwaysCopyImage = false
        private var alwaysCopyVideo = false

        private var compressEngine: ICompressEngine? = null

        /**
         * 创建一个吝啬的参数
         */
        fun asCopyAndStingy(engine: ICompressEngine?=null) : Builder {
            alwaysCopyImage = true
            alwaysCopyVideo = false
            limitImageSize = 25 * 1024 * 1024
            targetImageSize = 2 * 1024 * 1024
            limitVideoSize = 150 * 1024 * 1024L
            ignoreSizeKb = 500 * 1024
            compressEngine = engine ?: defaultCompressEngine(config = BestImageCompressor.Config(ignoreSizeInKB = ignoreSizeKb))
            return this
        }

        /**
         * 不拷贝的参数
         */
        fun asNoCopy() : Builder {
            alwaysCopyImage = false
            alwaysCopyVideo = false
            limitImageSize = Int.MAX_VALUE
            targetImageSize = Int.MAX_VALUE
            limitVideoSize = Long.MAX_VALUE
            ignoreSizeKb = Int.MAX_VALUE
            compressEngine = null
            return this
        }

        fun setLimitImageSize(limitImageSize: Int) = apply { this.limitImageSize = limitImageSize }
        fun setTargetImageSize(targetImageSize: Int) = apply { this.targetImageSize = targetImageSize }
        fun setLimitVideoSize(limitVideoSize: Long) = apply { this.limitVideoSize = limitVideoSize }
        fun setIgnoreSizeKb(ignoreSizeKb: Int) = apply { this.ignoreSizeKb = ignoreSizeKb }
        fun setCompressEngine(compressEngine: ICompressEngine) = apply { this.compressEngine = compressEngine }

        fun setAlwaysCopyImage(alwaysCopy: Boolean) = apply { this.alwaysCopyImage = alwaysCopy }
        fun setAlwaysCopyVideo(alwaysCopy: Boolean) = apply { this.alwaysCopyVideo = alwaysCopy }

        fun build() = PickerMediaParams(
            alwaysCopyImage,
            alwaysCopyVideo,
            limitImageSize,
            targetImageSize,
            limitVideoSize,
            ignoreSizeKb,
            compressEngine
        )
    }
}