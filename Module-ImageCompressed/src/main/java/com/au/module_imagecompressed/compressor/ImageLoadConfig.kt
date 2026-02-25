package com.au.module_imagecompressed.compressor

import com.au.module_imagecompressed.compressor.ImageLoaderUtil.Companion.DEFAULT_IGNORE_KB

data class ImageLoadConfig(
    /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxWidth: Int = 1440,
    /** 最大边长限制，防止内存溢出，也能显著降低图片大小 */
        val maxHeight: Int = 1920,
    /**  压缩质量选择，*/
        val quality:ImageLoadQuality = ImageLoadQuality.High,
    /**
         * 忽略列表，不做压缩
         */
        val ignoreFileTypes:List<String> = listOf("gif", "webp", "svg"),
    /**
         * 多少kb就忽略
         */
        val ignoreSizeInKB : Int = DEFAULT_IGNORE_KB,

    /**
         * 是否总是加载原图，忽略压缩配置
         */
        val alwaysLoadOriginal: Boolean = false,
    
    /**
         * 固定目标宽度，设置为大于0的值时生效
         */
        val fixedWidth: Int = 0,
    
    /**
         * 固定目标高度，设置为大于0的值时生效
         */
        val fixedHeight: Int = 0,
    )