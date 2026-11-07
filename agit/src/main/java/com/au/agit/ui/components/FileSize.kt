package com.au.agit.ui.components

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.round

fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
    if (bytes < 1024) return "${if (bytes < 0) 0 else bytes}B"
    var value = bytes.toDouble() / 1024
    var unit = "KB"
    if (value >= 1024) { value /= 1024; unit = "MB" }
    if (value >= 1024 && unit == "MB") { value /= 1024; unit = "GB" }
    val rounded = round(value * 10) / 10
    val format = DecimalFormat(if (rounded < 10) "0.0" else "0", DecimalFormatSymbols(locale))
    return format.format(rounded) + unit
}
