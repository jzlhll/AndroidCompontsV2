package com.allan.androidlearning.kson

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("baseDataStr")
data class BaseDataStrBean(
    override val code: String,
    override val message: String?,
    override val status: Boolean,
    val data: String?
) : BaseBean()