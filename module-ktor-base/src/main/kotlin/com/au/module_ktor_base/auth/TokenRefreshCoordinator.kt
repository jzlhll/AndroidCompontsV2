package com.au.module_ktor_base.auth

import io.ktor.http.Headers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

interface TokenProvider {
    suspend fun currentToken(): String?
    suspend fun refreshToken(): String
    suspend fun updateToken(token: String)
}

/** 由认证会话持有共享刷新任务，单个等待者取消不会取消其他等待者。 */
class TokenRefreshCoordinator(scope: CoroutineScope, private val provider: TokenProvider) {
    private val sessionJob = SupervisorJob(requireNotNull(scope.coroutineContext[Job]) { "Authentication scope requires an owner Job" })
    private val sessionScope = CoroutineScope(scope.coroutineContext + sessionJob)
    private val mutex = Mutex()
    private var inFlight: Deferred<String>? = null
    private var closed = false

    suspend fun refresh(failedToken: String?): String {
        val task = mutex.withLock {
            check(!closed) { "Authentication session is closed" }
            val current = provider.currentToken()
            if (!current.isNullOrBlank() && current != failedToken) return current
            inFlight ?: sessionScope.async(RefreshContext(this@TokenRefreshCoordinator), start = CoroutineStart.LAZY) {
                try {
                    val token = provider.refreshToken()
                    require(token.isNotBlank()) { "Refresh returned empty credentials" }
                    currentCoroutineContext().ensureActive()
                    mutex.withLock {
                        currentCoroutineContext().ensureActive()
                        check(!closed) { "Authentication session is closed" }
                        provider.updateToken(token)
                    }
                    token
                } finally {
                    withContext(NonCancellable) { mutex.withLock { inFlight = null } }
                }
            }.also { inFlight = it; it.start() }
        }
        return task.await()
    }

    suspend fun isRefreshingRequest(): Boolean = currentCoroutineContext()[RefreshContext]?.coordinator === this

    suspend fun close() {
        mutex.withLock { closed = true }
        sessionJob.cancelAndJoin()
    }

    private class RefreshContext(val coordinator: TokenRefreshCoordinator) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<RefreshContext>
    }
}

class AuthenticationRecovery(
    val coordinator: TokenRefreshCoordinator,
    val shouldRefresh: (status: Int, headers: Headers) -> Boolean = { status, _ -> status == 401 },
    val failedToken: (Headers) -> String? = { it["Authorization"]?.removePrefix("Bearer ") },
    val onFailure: suspend (Throwable) -> Unit = {},
)

class TimestampCorrection(
    val shouldCorrect: (status: Int, headers: Headers) -> Boolean,
    val serverTimeMillis: (Headers) -> Long?,
    val updateOffsetMillis: suspend (Long) -> Unit,
)
