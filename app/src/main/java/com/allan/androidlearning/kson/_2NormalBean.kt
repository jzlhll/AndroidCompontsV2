package com.allan.androidlearning.kson

data class _2NormalBean(
    val avatar: String? = null,
    val createdAt: Long = 0L,
    val email: String? = null,
) {
    /**
     * 与后端无关
     */
    var _isIgnored: Boolean = false

    override fun toString(): String {
        return "对象Bean{" +
                "avatar='" + avatar + '\'' +
                ", email='" + email + '\'' +
                '}'
    }
}