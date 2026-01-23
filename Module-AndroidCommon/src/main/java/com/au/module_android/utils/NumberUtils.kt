package com.au.module_android.utils

import android.annotation.SuppressLint
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 保留两位小数
 */
fun Float?.keepTwoPoint(roundingMode: RoundingMode = RoundingMode.HALF_EVEN): String {
    this ?: return "0.00"
    return this.toString().keepTwoPoint()
}

/**
 * 保留两位小数
 */
fun String?.keepTwoPoint(roundingMode: RoundingMode = RoundingMode.HALF_EVEN): String {
    return try {
        if (this == null) {
            "0.00"
        } else {
            BigDecimal(this).setScale(2, roundingMode)?.toString() ?: this
        }
    } catch (_: Throwable) {
        "0.00"
    }
}

/**
 * 转变为 分钟：秒。如果超过99分钟，就是99分钟。
 */
@SuppressLint("DefaultLocale")
fun convertMillisToMMSS(ts: Long): String {
    var minutes = (ts / (1000 * 60)).toInt()
    val seconds = ((ts / 1000) % 60).toInt()

    if (minutes >= 99) {
        minutes = 99
    }
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * 格式化单位
 *
 * @param size
 * @return
 */
fun getSizeFormat(size: Long): String {
    if (size < 1024) return "0KB"

    // 转成Double，避免整数除法丢失小数
    val sizeDouble = size.toDouble()
    
    // 依次计算各单位（1024进制）
    val kb = sizeDouble / 1024.0

    val mb = kb / 1024.0
    if (mb < 1) return String.format("%.1fKB", kb) // KB保留1位小数

    val gb = mb / 1024.0
    if (gb < 1) return String.format("%.1fMB", mb) // MB保留1位小数

    val tb = gb / 1024.0
    if (tb < 1) return String.format("%.2fGB", gb) // GB保留2位小数

    return String.format("%.2fTB", tb) // TB保留2位小数
}