package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
@SerialName("result")
data class BaseResultBean<T>(
    override val code: String,
    override val message: String?,
    override val status: Boolean,
    val data: T? = null
) : BaseBean()