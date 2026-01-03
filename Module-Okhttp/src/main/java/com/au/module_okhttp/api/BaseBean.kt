package com.au.module_okhttp.api

import androidx.annotation.Keep

@Keep
open class BaseBean(val code:String, val msg:String?) {
    fun isApiResultSuccess() = code == "200"
}