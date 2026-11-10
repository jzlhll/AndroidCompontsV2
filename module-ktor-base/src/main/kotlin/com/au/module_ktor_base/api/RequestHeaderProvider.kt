package com.au.module_ktor_base.api

import io.ktor.http.Headers
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url

data class RequestAttemptContext(
    val url: Url,
    val method: HttpMethod,
    val requestId: String,
    val attemptIndex: Int,
    val redirectHop: Int,
    val stableBody: String?,
    val contentType: ContentType?,
    val contentLength: Long?,
)

interface RequestHeaderProvider {
    val managedHeaderNames: Set<String>
    val credentialHeaderNames: Set<String> get() = setOf("Authorization")
    suspend fun headersFor(context: RequestAttemptContext): Headers
}
