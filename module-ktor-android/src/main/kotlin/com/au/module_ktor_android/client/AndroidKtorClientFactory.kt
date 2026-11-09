package com.au.module_ktor_android.client

import com.au.module_ktor_android.cache.namespaceDirectory
import com.au.module_ktor_android.log.AndroidNetworkLogger
import com.au.module_ktor_android.tls.AndroidTlsConfig
import com.au.module_ktor_base.client.KtorClientOwner
import com.au.module_ktor_base.client.createKtorClient
import com.au.module_ktor_base.config.KtorClientConfig
import com.au.module_ktor_base.config.StorageNamespace
import com.au.module_ktor_base.error.PolicyConfigurationException
import com.au.module_ktor_base.error.TransportFailureClassifier
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.File
import java.net.Proxy
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

private val httpCacheClaims = ConcurrentHashMap<Path, Any>()

class AndroidHttpCacheConfig(
    val directory: File,
    val namespace: StorageNamespace,
    val maxSizeBytes: Long = 32L * 1024 * 1024,
) {
    init { require(maxSizeBytes > 0) }
}

/** 每次创建独立的 Dispatcher、连接池、引擎及 Cache，按服务或会话长期复用返回值。 */
class AndroidKtorClientFactory {
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun create(
        config: KtorClientConfig,
        tls: AndroidTlsConfig = AndroidTlsConfig(),
        proxy: Proxy? = null,
        dns: Dns = Dns.SYSTEM,
        certificatePinner: CertificatePinner? = null,
        cache: AndroidHttpCacheConfig? = null,
    ): KtorClientOwner {
        var created: KtorClientOwner? = null
        try {
            return withContext(Dispatchers.IO) {
                if (cache != null && config.transferOnly) {
                    throw PolicyConfigurationException("Transfer clients must be stateless")
                }
                if (cache != null && cache.namespace != config.namespace) {
                    throw PolicyConfigurationException("HTTP cache must match client namespace")
                }
                val cacheDirectory = cache?.let { File(namespaceDirectory(it.directory, it.namespace), "http").canonicalFile.toPath() }
                val claim = Any()
                if (cacheDirectory != null && httpCacheClaims.putIfAbsent(cacheDirectory, claim) != null) {
                    throw PolicyConfigurationException("HTTP cache is already owned by another client")
                }
                val dispatcher = Dispatcher()
                val connectionPool = ConnectionPool()
                var ownedCache: Cache? = null
                try {
                    ownedCache = cache?.let { Cache(cacheDirectory!!.toFile(), it.maxSizeBytes) }
                    val builder = OkHttpClient.Builder()
                        .dispatcher(dispatcher)
                        .connectionPool(connectionPool)
                        .cache(ownedCache)
                        .dns(dns)
                        .proxy(proxy)
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .retryOnConnectionFailure(false)
                        .authenticator(Authenticator.NONE)
                        .proxyAuthenticator(Authenticator.NONE)
                        .connectTimeout(config.timeout.connectMillis, TimeUnit.MILLISECONDS)
                        .readTimeout(config.timeout.socketMillis, TimeUnit.MILLISECONDS)
                        .writeTimeout(config.timeout.socketMillis, TimeUnit.MILLISECONDS)
                        .callTimeout(0, TimeUnit.MILLISECONDS)
                    tls.applyTo(builder)
                    certificatePinner?.let { builder.certificatePinner(it) }
                    val ownedClient = builder.build()
                    val engine = OkHttp.create {
                        preconfigured = ownedClient
                        // 3.6.1 的默认 config 会覆盖 preconfigured 的 retryOnConnectionFailure(false)。
                        config { retryOnConnectionFailure(false) }
                    }
                    // 3.6.1 的连接池清理任务属于 requestsJob 的父 Job，关闭后也要等待它完成。
                    val engineCleanup = engine.coroutineContext.job.parent
                    val platformClassifier = AndroidTransportFailureClassifier()
                    val configured = config.copy(
                        failureClassifier = TransportFailureClassifier { config.failureClassifier.classify(it) ?: platformClassifier.classify(it) },
                        logger = config.logger ?: AndroidNetworkLogger(),
                    )
                    createKtorClient(engine, configured) {
                        engineCleanup?.join()
                        withContext(Dispatchers.IO) {
                            try {
                                dispatcher.cancelAll()
                                connectionPool.evictAll()
                                dispatcher.executorService.shutdown()
                                while (!dispatcher.executorService.awaitTermination(1, TimeUnit.SECONDS)) Unit
                            } finally {
                                try { ownedCache?.close() } finally { cacheDirectory?.let { httpCacheClaims.remove(it, claim) } }
                            }
                        }
                    }.also { created = it }
                } catch (error: Throwable) {
                    dispatcher.cancelAll()
                    connectionPool.evictAll()
                    dispatcher.executorService.shutdown()
                    try { ownedCache?.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                    cacheDirectory?.let { httpCacheClaims.remove(it, claim) }
                    throw error
                }
            }
        } catch (error: Throwable) {
            created?.let { owner ->
                withContext(NonCancellable) {
                    try { owner.close() } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
                }
            }
            throw error
        }
    }
}
