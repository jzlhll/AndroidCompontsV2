package com.au.module_ktor_base.config

import com.au.module_ktor_base.api.RequestHeaderProvider
import com.au.module_ktor_base.cache.CachePolicy
import com.au.module_ktor_base.model.TransferProgress
import com.au.module_ktor_base.policy.RetryPolicy
import io.ktor.client.plugins.HttpTimeoutConfig

data class TimeoutPolicy(
    val connectMillis: Long = 15_000,
    val socketMillis: Long = 45_000,
    val requestMillis: Long? = 45_000,
) {
    init {
        require(connectMillis > 0 && socketMillis > 0 && (requestMillis == null || requestMillis > 0))
    }

    fun applyTo(timeout: HttpTimeoutConfig) {
        timeout.connectTimeoutMillis = connectMillis
        timeout.socketTimeoutMillis = socketMillis
        timeout.requestTimeoutMillis = requestMillis ?: HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    }

    companion object {
        val Short = TimeoutPolicy(15_000, 15_000, 15_000)
        val Normal = TimeoutPolicy()
        val LongWait = TimeoutPolicy(15_000, 300_000, 300_000)
    }
}

data class ResponseLimits(
    val maxBufferedResponseBytes: Int = 8 * 1024 * 1024,
    val maxErrorBodyBytes: Int = 8 * 1024,
    val maxLogBodyBytes: Int = 4 * 1024,
) {
    init { require(maxBufferedResponseBytes > 0 && maxErrorBodyBytes >= 0 && maxLogBodyBytes >= 0) }
}

data class StorageNamespace(val serviceId: String, val principalId: String) {
    init { require(serviceId.isNotBlank() && principalId.isNotBlank()) }
}

class RequestOptions(
    val headerProvider: RequestHeaderProvider? = null,
    val timeout: TimeoutPolicy? = null,
    val responseLimits: ResponseLimits? = null,
    val retryPolicy: RetryPolicy? = null,
    acceptedStatuses: Set<Int>? = null,
    val skipAuthenticationRecovery: Boolean = false,
    val cachePolicy: CachePolicy = CachePolicy.NetworkOnly,
    val sensitive: Boolean = true,
    val maxDownloadBytes: Long? = null,
    val progressIntervalMillis: Long = 100,
    val onProgress: (suspend (TransferProgress) -> Unit)? = null,
    crossOriginHeaderNames: Set<String> = emptySet(),
) {
    val acceptedStatuses = acceptedStatuses?.toSet()
    val crossOriginHeaderNames = crossOriginHeaderNames.map { it.lowercase() }.toSet()
    init { require(progressIntervalMillis >= 0 && (maxDownloadBytes == null || maxDownloadBytes > 0)) }
}
