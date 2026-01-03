package com.au.module_okhttp.api

import androidx.annotation.Keep

@Keep
open class BaseDataStrBean(code:String,
                           msg:String?,
                           data: String?) : BaseBean(code, msg) {
}