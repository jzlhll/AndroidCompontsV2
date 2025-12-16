package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserListBean(
    val users: List<UserItem>? = null
) {
    @Serializable
    data class UserItem(
        @SerialName("is_admin") val isAdmin: Boolean = false,
        @SerialName("nick_name") val nickName: String = "",
        @SerialName("user_avatar") val userAvatar: String = "",
        @SerialName("user_email") val userEmail: String = "",
    )
}
