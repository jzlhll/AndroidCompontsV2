package com.au.module_okhttp.creator

interface AbsOkhttpCacheManager {
    suspend fun cacheToDisk(url:String, paramsStr:String?, method: HttpMethod, resultStr:String)
}

class NoOkhttpCacheManager : AbsOkhttpCacheManager {
    override suspend fun cacheToDisk(url: String, paramsStr: String?, method: HttpMethod, resultStr: String) {
    }
}