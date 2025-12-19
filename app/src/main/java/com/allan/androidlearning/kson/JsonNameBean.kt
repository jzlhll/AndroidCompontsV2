package com.allan.androidlearning.kson

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class JsonNameBean(
    @JsonNames("user_name", "username", "userName")
    val name: String,

    @JsonNames("user_email", "email_address")
    val email: String? = null
)