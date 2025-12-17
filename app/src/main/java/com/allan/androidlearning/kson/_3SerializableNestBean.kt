package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class _3SerializableNestBean(
    val outInfo: String? = null,
    @SerialName("server_time") val serverTime: Long = 0L,
    val data: _1SerializableBean? = null,
    val dataList: List<_1SerializableBean>? = null,
    val dataMap: Map<String, _1SerializableBean>? = null,
) {
    override fun toString(): String {
        return """
            对象NestBean:
            outInfo: $outInfo
            serverTime: $serverTime
            data: $data
            dataList: $dataList
            dataMap: $dataMap
        """.trimIndent()
    }
}