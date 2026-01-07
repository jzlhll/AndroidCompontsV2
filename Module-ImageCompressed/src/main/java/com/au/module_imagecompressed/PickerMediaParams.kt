package com.au.module_imagecompressed

class PickerMediaParams private constructor(
    val limitImageSize: Int, //图片最大限制，将不会做转换
    val shouldTryCompressImageSize:Int, //图片最大限制，将不会做转换，UriWrap beLimitedSize=true
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

        private var compressEngine: ICompressEngine? = null

        /**
         * 创建一个吝啬的参数
         */
        fun asCopyAndStingy(engine: ICompressEngine?=null) : Builder {
            limitImageSize = 25 * 1024 * 1024
            targetImageSize = 2 * 1024 * 1024
            limitVideoSize = 150 * 1024 * 1024L
            ignoreSizeKb = 500 * 1024
            compressEngine = engine ?: defaultCompressEngine(ignoreSizeKb)
            return this
        }

        /**
         * 不拷贝的参数
         */
        fun asNoLimit() : Builder {
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

        fun build() = PickerMediaParams(
            limitImageSize,
            targetImageSize,
            limitVideoSize,
            ignoreSizeKb,
            compressEngine
        )
    }
}