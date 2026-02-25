package com.au.module_okhttp.exceptions

import androidx.annotation.Keep

@Keep
class RefreshTokenExpiredException(msg:String) : Exception(msg)