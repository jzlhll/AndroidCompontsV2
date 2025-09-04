package com.au.module_android.utilsmedia

enum class CopyMode {
    /**
     * 不做拷贝, 都是原始Uri。
     */
    COPY_NOTHING,

    //基本上所有都不做拷贝，原始Uri。只有heic图片做转换为jpg拷贝。
    COPY_NOTHING_BUT_CVT_HEIC,

    /** 除了视频不做拷贝。基本上所有做拷贝，heic和png等图片还会进行jpg转换。*/
    COPY_CVT_IMAGE_TO_JPG,

    //直接拷贝
    COPY_ALWAYS,
}