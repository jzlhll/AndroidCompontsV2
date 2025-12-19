package com.allan.androidlearning.kson

import android.net.Uri
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ContextualBean(
    //@Serializable(with = UriSerializer::class)
    @Contextual
    val uuid: Uri,
    val message:String,
)
