package com.allan.mydroid.beans.wsdata

import androidx.annotation.Keep

data class UriUuidData(@Keep val uriUuid:String)

data class UriUuidSuccessData(@Keep val uriUuid:String, @Keep val isSuccess: Boolean?)