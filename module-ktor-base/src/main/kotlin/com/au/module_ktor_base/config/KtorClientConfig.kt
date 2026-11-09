package com.au.module_ktor_base.config

import com.au.module_ktor_base.api.RequestHeaderProvider
import com.au.module_ktor_base.auth.AuthenticationRecovery
import com.au.module_ktor_base.auth.TimestampCorrection
import com.au.module_ktor_base.cache.ApiCacheConfig
import com.au.module_ktor_base.cache.NamespacedCookiesStorage
import com.au.module_ktor_base.error.PolicyConfigurationException
import com.au.module_ktor_base.error.TransportFailureClassifier
import com.au.module_ktor_base.error.classifyKtorFailure
import com.au.module_ktor_base.log.BodyLogPolicy
import com.au.module_ktor_base.log.NetworkLogger
import com.au.module_ktor_base.policy.CredentialPolicy
import com.au.module_ktor_base.policy.RedirectPolicy
import com.au.module_ktor_base.policy.RetryPolicy
import com.au.module_ktor_base.policy.TargetPolicy
import io.ktor.http.Headers
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.time.Clock

data class KtorClientConfig(
    val targetPolicy: TargetPolicy,
    val credentialPolicy: CredentialPolicy,
    val headerProvider: RequestHeaderProvider? = null,
    val json: Json = Json { ignoreUnknownKeys = true },
    val transferOnly: Boolean = false,
    val timeout: TimeoutPolicy = TimeoutPolicy.Normal,
    val responseLimits: ResponseLimits = ResponseLimits(),
    val retryPolicy: RetryPolicy = RetryPolicy(),
    val redirectPolicy: RedirectPolicy = RedirectPolicy(),
    val failureClassifier: TransportFailureClassifier = TransportFailureClassifier(::classifyKtorFailure),
    val requestIdProvider: () -> String = { Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } },
    val logger: NetworkLogger? = null,
    val bodyLogPolicy: BodyLogPolicy? = null,
    val defaultHeaders: Headers = Headers.Empty,
    val namespace: StorageNamespace? = null,
    val cookies: NamespacedCookiesStorage? = null,
    val apiCache: ApiCacheConfig? = null,
    val authenticationRecovery: AuthenticationRecovery? = null,
    val timestampCorrection: TimestampCorrection? = null,
    val clockMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    init {
        credentialPolicy.validate(targetPolicy)
        if ((cookies != null || apiCache != null) && namespace == null) {
            throw PolicyConfigurationException("Stateful clients require a storage namespace")
        }
        if (apiCache != null && apiCache.store.namespace != namespace) {
            throw PolicyConfigurationException("Cache namespace differs from client namespace")
        }
        if (cookies != null && cookies.namespace != namespace) {
            throw PolicyConfigurationException("Cookie namespace differs from client namespace")
        }
        if (transferOnly && (cookies != null || apiCache != null)) {
            throw PolicyConfigurationException("Transfer clients must be stateless")
        }
        if (defaultHeaders.names().any { it.equals("Host", true) || it.equals("Authorization", true) || it.equals("Proxy-Authorization", true) || it.equals("Cookie", true) ||
                it.equals("X-APP-ID", true) || it.equals("X-APP-KEY", true) ||
                headerProvider?.managedHeaderNames?.any { name -> name.equals(it, true) } == true }) {
            throw PolicyConfigurationException("Default headers must not contain credentials or managed headers")
        }
    }
}
