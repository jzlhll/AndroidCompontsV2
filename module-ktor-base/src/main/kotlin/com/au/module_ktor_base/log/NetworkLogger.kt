package com.au.module_ktor_base.log

interface NetworkLogger {
    fun debug(message: String)
    fun failure(message: String)
}

/** 不记录 URL、Header 或异常原文；正文须由业务提供脱敏器后才允许输出。 */
class BodyLogPolicy(
    val redact: (String) -> String,
)

fun truncateUtf8(text: String, maxBytes: Int): Pair<String, Boolean> {
    require(maxBytes >= 0)
    val bytes = text.encodeToByteArray()
    if (bytes.size <= maxBytes) return text to false
    var end = maxBytes
    while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
    return bytes.decodeToString(0, end) to true
}
