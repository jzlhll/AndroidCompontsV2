package com.au.audiorecordplayer.cam2.view.gl        // 滤镜类型枚举
enum class FilterType {
    ORIGINAL,
    GRAY,
    INVERT,
    SEPIA,
    GAUSSIAN,
    SHARPEN,
    BRIGHTNESS
}

fun String.toType(): FilterType {
    return when (this) {
        "original" -> FilterType.ORIGINAL
        "gray" -> FilterType.GRAY
        "invert" -> FilterType.INVERT
        "sepia" -> FilterType.SEPIA
        "gaussian" -> FilterType.GAUSSIAN
        "sharpen" -> FilterType.SHARPEN
        "brightness" -> FilterType.BRIGHTNESS
        else -> FilterType.ORIGINAL
    }
}

fun FilterType.toName(): String {
    return when (this) {
        FilterType.ORIGINAL -> "original"
        FilterType.GRAY -> "gray"
        FilterType.INVERT -> "invert"
        FilterType.SEPIA -> "sepia"
        FilterType.GAUSSIAN -> "gaussian"
        FilterType.SHARPEN -> "sharpen"
        FilterType.BRIGHTNESS -> "brightness"
    }
}

fun FilterType.needSize(): Boolean {
    return when (this) {
        FilterType.SHARPEN -> true
        FilterType.GAUSSIAN -> true
        else -> false
    }
}

fun FilterType.needBrightness(): Boolean {
    return when (this) {
        FilterType.BRIGHTNESS -> true
        else -> false
    }
}