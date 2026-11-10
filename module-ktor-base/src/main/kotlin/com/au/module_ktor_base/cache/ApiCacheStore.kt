package com.au.module_ktor_base.cache

import com.au.module_ktor_base.config.StorageNamespace
import com.au.module_ktor_base.error.TransportFailure
import io.ktor.http.HttpMethod
import io.ktor.http.Url

class CachedResponse(val bytes: ByteArray, val expiresAtMillis: Long)

interface ApiCacheStore {
    val namespace: StorageNamespace
    suspend fun read(key: String): CachedResponse?
    suspend fun write(key: String, value: CachedResponse)
    suspend fun remove(key: String)
    suspend fun clear()
}

enum class CachePolicy { NetworkOnly, CacheOnly, CacheFirst, NetworkFirst }

class ApiCacheConfig(
    val store: ApiCacheStore,
    val ttlMillis: Long,
    fallbackFailures: Set<TransportFailure> = setOf(TransportFailure.Connect, TransportFailure.ConnectionLost),
) {
    val fallbackFailures = fallbackFailures.toSet()
    init { require(ttlMillis > 0) }
}

fun apiCacheKey(namespace: StorageNamespace, method: HttpMethod, url: Url, stableBody: String): String {
    // 长度前缀消除分隔符歧义；URL 保留重复 Query 的顺序与编码，不包含 Token。
    return listOf(namespace.serviceId, namespace.principalId, method.value, url.toString(), stableBody)
        .joinToString("") { "${it.length}:$it" }
}
