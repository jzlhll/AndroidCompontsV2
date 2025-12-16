package com.allan.androidlearning.kson

import kotlinx.serialization.Serializable

@Serializable
sealed class BaseBean {
    abstract val code: String
    abstract val message: String?
    abstract val status: Boolean

    fun isSuccess() = code == "0" || code == "200" || message == "success" || status

    override fun toString(): String {
        return "(code='$code', message=$message, status=$status)"
    }
}