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
    val kiloByte = size / 1024
    if (kiloByte < 1) {
        return "0KB"
    }
    val megaByte = kiloByte / 1024
    if (megaByte < 1) {
        val result1 = BigDecimal(kiloByte)
        return result1.setScale(1, BigDecimal.ROUND_HALF_UP)
            .toPlainString() + "KB"
    }
    val gigaByte = megaByte / 1024
    if (gigaByte < 1) {
        val result2 = BigDecimal(megaByte)
        return result2.setScale(1, BigDecimal.ROUND_HALF_UP)
            .toPlainString() + "MB"
    }
    val teraBytes = gigaByte / 1024
    if (teraBytes < 1) {
        val result3 = BigDecimal(gigaByte)
        return result3.setScale(1, BigDecimal.ROUND_HALF_UP)
            .toPlainString() + "GB"
    }
    val result4 = BigDecimal(teraBytes)
    return (result4.setScale(1, BigDecimal.ROUND_HALF_UP)
        .toPlainString() + "TB")
}