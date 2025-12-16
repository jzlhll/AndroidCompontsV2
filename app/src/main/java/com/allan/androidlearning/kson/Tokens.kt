package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tokens(
    val token: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
)
