package com.allan.mydroid.beans

import androidx.annotation.Keep
import com.au.module_okhttp.api.ResultBean

@Keep
class WSResultBox<T>(code: String,
                     msg: String?,
                     val api:String,
                     data:T?)
    : ResultBean<T>(code, msg, data)