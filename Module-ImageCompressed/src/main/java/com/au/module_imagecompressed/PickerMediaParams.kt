package com.au.module_imagecompressed

import com.au.module_imagecompressed.CopyMode

class PickerMediaParams private constructor(
    val copyMode: CopyMode,
    val limitImageSize: Int, //图片最大限制，将不会做转换，UriWrap beLimitedSize=true
    val targetImageSize: Int,
    val limitVideoSize: Long,  //视频最大限制，将不会做转换，UriWrap beLimitedSize=true
    val ignoreSizeKb: Int,
    val needLuban: Boolean
) {
    companion object {
        const val DEFAULT_IGNORE_SIZE_KB = 3 * 1024 * 1024

    }

    class Builder {
        private var copyMode: CopyMode = CopyMode.COPY_NOTHING
        private var limitImageSize = 50 * 1024 * 1024
        private var targetImageSize = 5 * 1024 * 1024
        private var limitVideoSize = 500 * 1024 * 1024L
        private var ignoreSizeKb = 5 * 1024 * 1024
        private var needLuban = false

        /**
         * 创建一个更吝啬的参数
         */
        fun asStingy() : Builder {
            copyMode = CopyMode.COPY_CVT_IMAGE_TO_JPG
            limitImageSize = 25 * 1024 * 1024
            targetImageSize = 2 * 1024 * 1024
            limitVideoSize = 150 * 1024 * 1024L
            ignoreSizeKb = 500 * 1024
            needLuban = true
            return this
        }

        /**
         * 创建一个更宽容的参数
         */
        fun asLoose() : Builder {
            copyMode = CopyMode.COPY_NOTHING
            limitImageSize = 30 * 1024 * 1024
            targetImageSize = 5 * 1024 * 1024
            limitVideoSize = 200 * 1024 * 1024L
            ignoreSizeKb = 5 * 1024 * 1024
            needLuban = true
            return this
        }

        /**
         * 最宽容的参数
         */
        fun asNoLimit() : Builder {
            copyMode = CopyMode.COPY_NOTHING
            limitImageSize = 100 * 1024 * 1024
            targetImageSize = 5 * 1024 * 1024
            limitVideoSize = 4 * 1024 * 1024 * 1024L //4个G上限已经足够大
            needLuban = false
            return this
        }

        fun setCopyMode(copyMode: CopyMode) = apply { this.copyMode = copyMode }
        fun setLimitImageSize(limitImageSize: Int) = apply { this.limitImageSize = limitImageSize }
        fun setTargetImageSize(targetImageSize: Int) = apply { this.targetImageSize = targetImageSize }
        fun setLimitVideoSize(limitVideoSize: Long) = apply { this.limitVideoSize = limitVideoSize }
        fun setIgnoreSizeKb(ignoreSizeKb: Int) = apply { this.ignoreSizeKb = ignoreSizeKb }
        fun setNeedLuban(ignoreSizeKb: Int = DEFAULT_IGNORE_SIZE_KB) = apply {
            setIgnoreSizeKb(DEFAULT_IGNORE_SIZE_KB)
            needLuban = true
        }
        fun noNeedLuban() = apply { needLuban = false }

        fun build() = PickerMediaParams(
            copyMode,
            limitImageSize,
            targetImageSize,
            limitVideoSize,
            ignoreSizeKb,
            needLuban
        )
    }
}