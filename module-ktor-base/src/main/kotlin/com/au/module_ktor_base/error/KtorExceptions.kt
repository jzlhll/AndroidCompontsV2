package com.au.module_ktor_base.error

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException

open class KtorException(
    message: String,
    val requestId: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

class PolicyConfigurationException(message: String) : KtorException(message)
class TargetRejectedException(requestId: String? = null, cause: Throwable? = null) : KtorException("Request target rejected", requestId, cause)
class NoBaseUrlException : KtorException("No base URL available")
class MissingCredentialsException(requestId: String) : KtorException("Required credentials unavailable", requestId)
class RequestGuardException(requestId: String) : KtorException("Final request differs from approved request", requestId)
class RedirectException(requestId: String, cause: Throwable? = null) : KtorException("Invalid or excessive redirect", requestId, cause)
class ClientClosedException : KtorException("Client is closed")
class HttpStatusException(
    val status: Int,
    requestId: String,
    val summary: String,
    val truncated: Boolean,
    cause: Throwable? = null,
) : KtorException("HTTP request failed with status $status", requestId, cause)

class BusinessException(val code: String, requestId: String) : KtorException("API response rejected", requestId)
class ResponseDecodingException(requestId: String, cause: Throwable) : KtorException("Response decoding failed", requestId, cause)
class ResponseTooLargeException(val limit: Long, requestId: String) : KtorException("Response exceeds configured limit", requestId)
class StorageException(cause: Throwable, requestId: String? = null) : KtorException("Transfer storage failed", requestId, cause)
class CacheMissException : KtorException("No valid cached response")
class AuthenticationRecoveryException(requestId: String, cause: Throwable) : KtorException("Authentication recovery failed", requestId, cause)

enum class TransportFailure {
    Dns, Connect, ConnectionLost, ConnectTimeout, SocketTimeout, RequestTimeout,
    TlsCertificate, TlsHandshake, Protocol, Unknown,
}

fun interface TransportFailureClassifier {
    fun classify(cause: Throwable): TransportFailure?
}

class TransportException(
    val failure: TransportFailure,
    requestId: String,
    cause: Throwable,
) : KtorException("Network transport failed: $failure", requestId, cause)

fun classifyKtorFailure(cause: Throwable): TransportFailure? = when (cause) {
    is HttpRequestTimeoutException -> TransportFailure.RequestTimeout
    is ConnectTimeoutException -> TransportFailure.ConnectTimeout
    is SocketTimeoutException -> TransportFailure.SocketTimeout
    else -> null
}
