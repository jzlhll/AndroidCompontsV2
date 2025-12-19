package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class _1SerializableBean (
    val avatar: String? = null,
    @SerialName("created_at") val createdAt: Long = 0L,

    val email: String? = null,
) {
    /**
     * 与后端无关
     */
    @Transient
    var _isIgnored: Boolean = false

    override fun toString(): String {
        return "对象Bean{" +
                "avatar='" + avatar + '\'' +
                ", email='" + email + '\'' +
                '}'
    }
}