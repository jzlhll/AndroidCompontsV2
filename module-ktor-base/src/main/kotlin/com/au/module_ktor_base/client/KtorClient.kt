package com.au.module_ktor_base.client

import com.au.module_ktor_base.api.RequestAttemptContext
import com.au.module_ktor_base.api.RequestHeaderProvider
import com.au.module_ktor_base.cache.CachePolicy
import com.au.module_ktor_base.cache.CachedResponse
import com.au.module_ktor_base.cache.apiCacheKey
import com.au.module_ktor_base.config.KtorClientConfig
import com.au.module_ktor_base.config.RequestOptions
import com.au.module_ktor_base.error.*
import com.au.module_ktor_base.log.truncateUtf8
import com.au.module_ktor_base.model.TransferProgress
import com.au.module_ktor_base.policy.ApiResponsePolicy
import com.au.module_ktor_base.policy.CredentialRequirement
import com.au.module_ktor_base.policy.Origin
import com.au.module_ktor_base.policy.TargetPolicy
import com.au.module_ktor_base.transfer.DownloadSink
import com.au.module_ktor_base.transfer.KtorBody
import com.au.module_ktor_base.transfer.UploadSource
import com.au.module_ktor_base.transfer.storageOperation
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.forEachHeader
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.io.encoding.Base64
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private val configurationKey = AttributeKey<KtorClientConfig>("ControlledKtorConfiguration")
private val approvalKey = AttributeKey<SendApproval>("ControlledKtorSendApproval")
private val lifecycleKey = AttributeKey<RequestLifecycle>("ControlledKtorLifecycle")

/** 不选择平台引擎；凭据、重定向和重发全部留给执行器。 */
private fun HttpClientConfig<*>.configureKtor(config: KtorClientConfig) {
    followRedirects = false
    expectSuccess = false
    install(DefaultRequest) { headers.appendAll(config.defaultHeaders) }
    install(HttpTimeout) { config.timeout.applyTo(this) }
    config.cookies?.let { install(HttpCookies) { storage = it } }
    install("ControlledKtorConfiguration") {
        check(engine is GuardedEngine && (engine as GuardedEngine).configuration === config) {
            "Use createKtorClient to install the final engine guard"
        }
        attributes.put(configurationKey, config)
        attributes.put(lifecycleKey, RequestLifecycle())
    }
}

/** 将引擎所有权交给返回值；用于 Android 工厂及只替换引擎的受控回放。 */
fun createKtorClient(
    engine: HttpClientEngine,
    config: KtorClientConfig,
    releaseResources: suspend () -> Unit = {},
): KtorClientOwner {
    val guarded = GuardedEngine(engine, config)
    try {
        val client = HttpClient(guarded) { configureKtor(config) }
        return KtorClientOwner(client, releaseResources)
    } catch (error: Throwable) {
        guarded.close()
        throw error
    }
}

/** 服务或认证会话持有所有者，API 只持有 executor，没有关闭权限。 */
class KtorClientOwner(
    private val client: HttpClient,
    private val releaseResources: suspend () -> Unit = {},
) {
    val executor = KtorRequestExecutor(client)
    private val closeMutex = Mutex()
    private var closed = false

    suspend fun close(cancelInFlight: Boolean = true) = withContext(NonCancellable) {
        closeMutex.withLock {
            if (closed) return@withLock
            client.attributes[lifecycleKey].stop(cancelInFlight)
            var failure: Throwable? = null
            try {
                try { client.close() } catch (error: Throwable) {
                    failure = error
                    client.coroutineContext.cancel()
                }
                client.coroutineContext.job.join()
                try {
                    client.engine.close()
                    client.engine.coroutineContext.job.join()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            } finally {
                try { releaseResources() } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                } finally { closed = true }
            }
            failure?.let { throw it }
            Unit
        }
    }
}

/** 作用域响应只允许受限分块读取，回调结束后不能继续消费正文。 */
class KtorResponse(
    val status: Int,
    val headers: Headers,
    val requestId: String,
    private val readBlock: suspend (ByteArray, Int, Int) -> Int,
) {
    suspend fun readAvailable(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset): Int {
        require(offset >= 0 && length > 0 && offset <= buffer.size - length)
        return readBlock(buffer, offset, length)
    }
}

/** 所有请求共用目标快照、Header、恢复预算和限量流式读取。 */
class KtorRequestExecutor(private val client: HttpClient) {
    val config: KtorClientConfig = client.attributes.getOrNull(configurationKey)
        ?: throw PolicyConfigurationException("Client lacks controlled configuration")
    private val lifecycle = client.attributes[lifecycleKey]

    init {
        if (client.engine !is GuardedEngine || (client.engine as GuardedEngine).configuration !== config) {
            throw PolicyConfigurationException("Client lacks final send guard")
        }
    }

    suspend fun bytes(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
    ): ByteArray = buffered(baseUrlProvider, request, options, headerProvider) { bytes, _ -> bytes }

    suspend fun text(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
    ): String = buffered(baseUrlProvider, request, options, headerProvider) { bytes, _ -> bytes.decodeToString() }

    suspend fun <T> json(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        serializer: KSerializer<T>,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
        responsePolicy: ApiResponsePolicy? = null,
    ): T = buffered(baseUrlProvider, request, options, headerProvider) { bytes, requestId ->
        try {
            val element = config.json.parseToJsonElement(bytes.decodeToString())
            config.json.decodeFromJsonElement(serializer, responsePolicy?.data(element, requestId) ?: element)
        } catch (error: KtorException) {
            throw error
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw ResponseDecodingException(requestId, error)
        }
    }

    suspend fun <T> execute(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
        block: suspend (KtorResponse) -> T,
    ): T = tracked {
        val resolved = resolve(baseUrlProvider, request)
        var entered = false
        send(resolved, request, options, headerProvider, false, { !entered }) {
            entered = true
            block(it)
        }
    }

    suspend fun <T> executeUrl(
        url: Url,
        options: RequestOptions = RequestOptions(),
        method: HttpMethod = HttpMethod.Get,
        headers: Headers = Headers.Empty,
        body: KtorBody = KtorBody.Empty(),
        block: suspend (KtorResponse) -> T,
    ): T = tracked {
        val request = KtorRequest("", method, headers = headers, body = body)
        val resolved = resolveAbsolute(url)
        var entered = false
        send(resolved, request, options, null, false, { !entered }) {
            entered = true
            block(it)
        }
    }

    suspend fun <T> stream(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
        block: suspend (KtorResponse) -> T,
    ): T = tracked {
        var entered = false
        send(resolve(baseUrlProvider, request), request, options, headerProvider, true, { !entered }) {
            entered = true
            block(it)
        }
    }

    suspend fun downloadUrl(
        url: Url,
        sink: DownloadSink,
        options: RequestOptions = RequestOptions(),
    ) = tracked { download(resolveAbsolute(url), KtorRequest(""), sink, options, null) }

    suspend fun download(
        baseUrlProvider: suspend () -> Url?,
        request: KtorRequest,
        sink: DownloadSink,
        options: RequestOptions = RequestOptions(),
        headerProvider: RequestHeaderProvider? = null,
    ) = tracked { download(resolve(baseUrlProvider, request), request, sink, options, headerProvider) }

    private suspend fun <T> tracked(block: suspend () -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        lifecycle.add(job)
        try { coroutineScope { block() } } finally {
            withContext(NonCancellable) { lifecycle.remove(job) }
        }
    }

    private suspend fun resolve(provider: suspend () -> Url?, request: KtorRequest): ResolvedRequest {
        val base = provider() ?: throw NoBaseUrlException()
        validateBaseUrl(base)
        val origin = config.targetPolicy.validate(base)
        val frozen = if (config.targetPolicy is TargetPolicy.ResolvedBaseOrigin) origin else null
        val url = request.resolve(base)
        config.targetPolicy.validate(url, frozen)
        return ResolvedRequest(url, frozen)
    }

    private fun resolveAbsolute(url: Url): ResolvedRequest {
        if (config.targetPolicy !is TargetPolicy.FixedOrigins) throw PolicyConfigurationException("Absolute URLs require fixed origins")
        config.targetPolicy.validate(url)
        return ResolvedRequest(url, null)
    }

    private suspend fun <T> buffered(
        provider: suspend () -> Url?,
        request: KtorRequest,
        options: RequestOptions,
        headerProvider: RequestHeaderProvider?,
        decode: (ByteArray, String) -> T,
    ): T = tracked {
        val resolved = resolve(provider, request)
        val requestId = requestId()
        val cache = config.apiCache
        val preparedBody = prepareBody(request.body, options)
        val useCache = options.cachePolicy != CachePolicy.NetworkOnly
        val key = if (useCache) {
            val bodyKey = preparedBody.stableBody
            if (cache == null || options.sensitive || bodyKey == null || config.transferOnly) {
                throw PolicyConfigurationException("Request is not eligible for business caching")
            }
            apiCacheKey(cache.store.namespace, request.method, resolved.url, bodyKey)
        } else null
        val cached = key?.let { storageOperation { cache!!.store.read(it) } }
            ?.takeIf { it.expiresAtMillis > config.clockMillis() }
        val limit = (options.responseLimits ?: config.responseLimits).maxBufferedResponseBytes
        if (cached != null && cached.bytes.size > limit) throw ResponseTooLargeException(limit.toLong(), requestId)
        if (options.cachePolicy == CachePolicy.CacheOnly) {
            return@tracked decode(cached?.bytes ?: throw CacheMissException(), requestId)
        }
        if (options.cachePolicy == CachePolicy.CacheFirst && cached != null) return@tracked decode(cached.bytes, requestId)
        try {
            val result = send(resolved, request, options, headerProvider, false, { true }, requestId, preparedBody) { response ->
                val bytes = readAll(response, limit)
                val value = decode(bytes, requestId)
                config.bodyLogPolicy?.let { policy ->
                    val maxBytes = (options.responseLimits ?: config.responseLimits).maxLogBodyBytes
                    val (summary, truncated) = truncateUtf8(policy.redact(bytes.decodeToString()), maxBytes)
                    config.logger?.debug("response body requestId=$requestId truncated=$truncated body=$summary")
                }
                bytes to value
            }
            if (key != null) storageOperation {
                cache!!.store.write(key, CachedResponse(result.first, config.clockMillis() + cache.ttlMillis))
            }
            result.second
        } catch (error: TransportException) {
            if (options.cachePolicy == CachePolicy.NetworkFirst && cached != null && error.failure in cache!!.fallbackFailures &&
                cached.expiresAtMillis > config.clockMillis()) {
                decode(cached.bytes, requestId)
            } else throw error
        }
    }

    private suspend fun download(
        resolved: ResolvedRequest,
        request: KtorRequest,
        sink: DownloadSink,
        options: RequestOptions,
        headerProvider: RequestHeaderProvider?,
    ) {
        var written = false
        try {
            send(resolved, request, options, headerProvider, true, { !written }) { response ->
                val buffer = ByteArray(64 * 1024)
                var received = 0L
                val total = if (response.headers[HttpHeaders.ContentEncoding] == null) {
                    response.headers[HttpHeaders.ContentLength]?.toLongOrNull()?.takeIf { it >= 0 }
                } else null
                var progressAt = TimeSource.Monotonic.markNow()
                while (true) {
                    val count = response.readAvailable(buffer)
                    if (count < 0) break
                    written = true
                    storageOperation { sink.write(buffer, 0, count) }
                    received += count
                    if (progressAt.elapsedNow().inWholeMilliseconds >= options.progressIntervalMillis) {
                        options.onProgress?.invoke(TransferProgress(received, total))
                        progressAt = TimeSource.Monotonic.markNow()
                    }
                }
                if (total != null && received != total) {
                    throw TransportException(TransportFailure.ConnectionLost, response.requestId, IllegalStateException("Incomplete response body"))
                }
                options.onProgress?.invoke(TransferProgress(received, total))
            }
            currentCoroutineContext().ensureActive()
            storageOperation { sink.complete() }
        } catch (error: Throwable) {
            withContext(NonCancellable) {
                try { storageOperation { sink.abort(error) } } catch (cleanup: Throwable) { error.addSuppressed(cleanup) }
            }
            throw error
        }
    }

    private suspend fun <T> send(
        resolved: ResolvedRequest,
        request: KtorRequest,
        options: RequestOptions,
        apiHeaderProvider: RequestHeaderProvider?,
        transfer: Boolean,
        retrySafe: () -> Boolean,
        requestId: String = requestId(),
        preparedBody: PreparedBody = prepareBody(request.body, options),
        consume: suspend (KtorResponse) -> T,
    ): T {
        val retry = options.retryPolicy ?: config.retryPolicy
        val limits = options.responseLimits ?: config.responseLimits
        val skipRecovery = options.skipAuthenticationRecovery || config.authenticationRecovery?.coordinator?.isRefreshingRequest() == true
        val provider = options.headerProvider ?: apiHeaderProvider ?: config.headerProvider
        val providerManaged = provider?.managedHeaderNames.orEmpty().map { it.lowercase() }.toSet()
        val credentials = provider?.credentialHeaderNames.orEmpty().map { it.lowercase() }.toSet()
        val managed = (listOfNotNull(config.headerProvider, apiHeaderProvider, options.headerProvider)
            .flatMap { it.managedHeaderNames } + listOf(HttpHeaders.Authorization, HttpHeaders.ProxyAuthorization, "X-APP-ID", "X-APP-KEY"))
            .map { it.lowercase() }.toSet()
        if (credentials.any { it !in providerManaged }) {
            throw PolicyConfigurationException("Credential header names must be managed")
        }
        if ("host" in managed) throw PolicyConfigurationException("Host is derived from the approved URL")
        if (config.defaultHeaders.names().any { it.lowercase() in managed }) {
            throw PolicyConfigurationException("Default headers conflict with managed headers")
        }
        if (config.cookies != null && "cookie" in managed) {
            throw PolicyConfigurationException("Managed Cookie headers require HttpCookies to be disabled")
        }
        var url = resolved.url
        var method = request.method
        var body = request.body
        var prepared = preparedBody
        var attempt = 0
        var hops = 0
        var refreshed = false
        var corrected = false
        val visited = mutableSetOf(url.toString())
        while (true) {
            currentCoroutineContext().ensureActive()
            val origin = config.targetPolicy.validate(url, resolved.origin)
            val requirement = config.credentialPolicy.requirement(origin)
            val content = prepared.content
            val supplied = if (requirement != CredentialRequirement.None && provider != null) {
                provider.headersFor(RequestAttemptContext(url, method, requestId, attempt, hops, prepared.stableBody, content.contentType, content.contentLength))
            } else Headers.Empty
            if (supplied.names().any { it.lowercase() !in providerManaged }) {
                throw PolicyConfigurationException("Header provider returned an unmanaged field")
            }
            if (requirement == CredentialRequirement.Required && (provider == null || credentials.isEmpty() ||
                    credentials.any { supplied.getAll(it).isNullOrEmpty() || supplied.getAll(it)!!.any(String::isBlank) })) {
                throw MissingCredentialsException(requestId)
            }
            val headers = Headers.build {
                request.headers.forEach { name, values ->
                    if (!name.equals(HttpHeaders.Cookie, true) && name.lowercase() !in managed &&
                        (origin == Origin.from(resolved.url) || name.lowercase() in options.crossOriginHeaderNames)) {
                        appendAll(name, values)
                    }
                }
                appendAll(supplied)
                if (options.sensitive || transfer) {
                    remove(HttpHeaders.CacheControl)
                    append(HttpHeaders.CacheControl, "no-store")
                }
            }
            val expected = effectiveHeaders(headers, content)
            val approval = SendApproval(config, resolved.origin, origin, managed.associateWith { expected.getAll(it) }, requestId)
            val approvedUrl = url
            val statement = client.prepareRequest {
                this.method = method
                this.url.takeFrom(approvedUrl)
                this.headers.appendAll(headers)
                setBody(content)
                timeout { (options.timeout ?: config.timeout).applyTo(this) }
                attributes.put(approvalKey, approval)
            }
            config.logger?.debug("request send requestId=$requestId attempt=$attempt redirectHop=$hops method=${method.value}")
            val result: AttemptResult<T>
            try {
                result = statement.execute { response ->
                    val status = response.status.value
                    config.logger?.debug("response received requestId=$requestId attempt=$attempt status=$status")
                    val redirect = config.redirectPolicy.isRedirect(status)
                    val authentication = config.authenticationRecovery
                    val timestamp = config.timestampCorrection
                    val refresh = !redirect && !skipRecovery && !refreshed && attempt < retry.maxResends && body.replayable &&
                        retry.allowsReplay(method) && requirement != CredentialRequirement.None &&
                        authentication?.shouldRefresh?.invoke(status, response.headers) == true
                    val serverTime = if (!redirect && !refresh && !skipRecovery && !corrected && attempt < retry.maxResends &&
                        body.replayable && retry.allowsReplay(method) && timestamp?.shouldCorrect?.invoke(status, response.headers) == true) {
                        timestamp.serverTimeMillis(response.headers)?.takeIf { it > 0 }
                    } else null
                    when {
                        redirect -> {
                            if (hops >= config.redirectPolicy.maxHops) throw RedirectException(requestId)
                            val next = config.redirectPolicy.resolve(response.headers[HttpHeaders.Location], url, config.targetPolicy, resolved.origin, requestId)
                            if (!visited.add(next.toString())) throw RedirectException(requestId)
                            AttemptResult.Redirect(next, status)
                        }
                        refresh -> AttemptResult.Refresh(authentication!!.failedToken(supplied))
                        serverTime != null -> AttemptResult.Correct(serverTime)
                        else -> {
                            val accepted = options.acceptedStatuses?.contains(status) ?: if (transfer) status == 200 else status in 200..299
                            var active = true
                            var read = 0L
                            val channel = response.bodyAsChannel()
                            val scoped = KtorResponse(status, response.headers, requestId) { buffer, offset, length ->
                                check(active) { "Response scope has ended" }
                                val count = transportBoundary(requestId) { channel.readAvailable(buffer, offset, length) }
                                if (count > 0) {
                                    read += count
                                    val maxBytes = if (!accepted) null else if (transfer) options.maxDownloadBytes else limits.maxBufferedResponseBytes.toLong()
                                    if (maxBytes != null && read > maxBytes) throw ResponseTooLargeException(maxBytes, requestId)
                                }
                                count
                            }
                            try {
                                if (!accepted) {
                                    val diagnostic = try { readPrefix(scoped, limits.maxErrorBodyBytes) } catch (error: TransportException) {
                                        throw HttpStatusException(status, requestId, "", true, error)
                                    }
                                    val (bytes, truncated) = diagnostic
                                    // 没有显式脱敏器时不保存远端正文，避免泄露响应中的凭据。
                                    val summary = config.bodyLogPolicy?.let { truncateUtf8(it.redact(bytes.decodeToString()), limits.maxErrorBodyBytes).first }.orEmpty()
                                    throw HttpStatusException(status, requestId, summary, truncated)
                                }
                                try { AttemptResult.Done(consume(scoped)) } catch (error: Throwable) { throw ConsumerFailure(error) }
                            } finally { active = false }
                        }
                    }
                }
            } catch (error: Throwable) {
                val failure = if (error is ConsumerFailure) error.original else classifyTransport(error, requestId)
                if (failure is CancellationException) throw failure
                if (failure is TransportException && attempt < retry.maxResends && body.replayable && retrySafe() && retry.allows(method, failure.failure)) {
                    attempt++
                    val wait = retry.backoffMillis(attempt)
                    require(wait >= 0)
                    delay(wait.milliseconds)
                    continue
                }
                config.logger?.failure("request failed requestId=$requestId attempt=$attempt category=${if (failure is TransportException) failure.failure else "Request"}")
                throw failure
            }
            when (result) {
                is AttemptResult.Done -> return result.value
                is AttemptResult.Redirect -> {
                    if (result.status == 303 && method != HttpMethod.Head || result.status in setOf(301, 302) && method == HttpMethod.Post) {
                        method = HttpMethod.Get
                        body = KtorBody.Empty()
                        prepared = prepareBody(body, options)
                    } else if (!body.replayable && body !is KtorBody.Empty) throw RedirectException(requestId)
                    url = result.url
                    hops++
                }
                is AttemptResult.Refresh -> {
                    refreshed = true
                    attempt++
                    val recovery = config.authenticationRecovery!!
                    try { recovery.coordinator.refresh(result.failedToken) } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        try { recovery.onFailure(error) } catch (callback: CancellationException) {
                            throw callback
                        } catch (callback: Exception) { error.addSuppressed(callback) }
                        throw AuthenticationRecoveryException(requestId, error)
                    }
                }
                is AttemptResult.Correct -> {
                    corrected = true
                    attempt++
                    config.timestampCorrection!!.updateOffsetMillis(result.serverTime - config.clockMillis())
                }
            }
        }
    }

    private suspend fun <T> transportBoundary(requestId: String, block: suspend () -> T): T = try {
        block()
    } catch (error: Throwable) { throw classifyTransport(error, requestId) }

    private fun classifyTransport(error: Throwable, requestId: String): Throwable {
        if (error is CancellationException || error is KtorException || error is Error) return error
        var cause: Throwable? = error.cause
        val seen = mutableSetOf<Throwable>(error)
        while (cause != null && seen.size < 16 && seen.add(cause)) {
            if (cause is StorageException) return cause
            cause = cause.cause
        }
        val failure = config.failureClassifier.classify(error) ?: classifyKtorFailure(error)
        return if (failure == null) error else TransportException(failure, requestId, error)
    }

    private fun requestId(): String = config.requestIdProvider().also {
        require(it.matches(Regex("[A-Za-z0-9._-]{1,128}"))) { "Invalid request ID" }
    }

    private fun prepareBody(body: KtorBody, options: RequestOptions): PreparedBody {
        val content = outgoing(body, options)
        return PreparedBody(content) {
            when (body) {
                is KtorBody.Empty -> ""
                is KtorBody.Bytes -> "${content.contentType}:${Base64.encode((content as OutgoingContent.ByteArrayContent).bytes())}"
                is KtorBody.Text, is KtorBody.JsonValue, is KtorBody.Form ->
                    "${content.contentType}:${(content as OutgoingContent.ByteArrayContent).bytes().decodeToString()}"
                is KtorBody.Upload, is KtorBody.Multipart -> null
            }
        }
    }

    private fun outgoing(body: KtorBody, options: RequestOptions): OutgoingContent = when (body) {
        is KtorBody.Empty -> object : OutgoingContent.NoContent() {}
        is KtorBody.Bytes -> byteContent(body.copyBytes(), body.contentType)
        is KtorBody.Text -> byteContent(body.value.encodeToByteArray(), body.contentType)
        is KtorBody.JsonValue -> byteContent(config.json.encodeToString(JsonElement.serializer(), body.value).encodeToByteArray(), ContentType.Application.Json)
        is KtorBody.Form -> byteContent(body.parameters.formUrlEncode().encodeToByteArray(), ContentType.Application.FormUrlEncoded)
        is KtorBody.Upload -> object : OutgoingContent.WriteChannelContent() {
            override val contentType = body.source.contentType
            override val contentLength = body.source.contentLength
            override suspend fun writeTo(channel: ByteWriteChannel) {
                val progress = UploadProgress(options, contentLength)
                copyUpload(body.source, channel, progress)
                progress.finish()
            }
        }
        is KtorBody.Multipart -> object : OutgoingContent.WriteChannelContent() {
            private val boundary = "ktor-" + Random.nextBytes(16).joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            private val pieces = multipartPieces(body, boundary)
            override val contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary)
            override val contentLength: Long? = multipartLength(pieces)
            override suspend fun writeTo(channel: ByteWriteChannel) {
                val progress = UploadProgress(options, contentLength)
                pieces.forEach { piece ->
                    channel.writeFully(piece.prefix)
                    progress.advance(piece.prefix.size)
                    piece.source?.let { copyUpload(it, channel, progress) }
                }
                progress.finish()
            }
        }
    }
}

private class ResolvedRequest(val url: Url, val origin: Origin?)

/** 复用已编码正文，缓存和 Header provider 需要稳定表示时再派生。 */
private class PreparedBody(val content: OutgoingContent, stableBody: () -> String?) {
    val stableBody: String? by lazy(LazyThreadSafetyMode.NONE, stableBody)
}

private class RequestLifecycle {
    private val mutex = Mutex()
    private val requests = mutableSetOf<Job>()
    private var accepting = true

    suspend fun add(job: Job) = mutex.withLock {
        if (!accepting) throw ClientClosedException()
        requests += job
    }

    suspend fun remove(job: Job) = mutex.withLock { requests -= job }

    suspend fun stop(cancel: Boolean) {
        val running = mutex.withLock { accepting = false; requests.toList() }
        if (cancel) running.forEach { it.cancel() }
        running.forEach { it.join() }
    }
}
private class ConsumerFailure(val original: Throwable) : Exception(null, original)
private sealed class AttemptResult<out T> {
    class Done<T>(val value: T) : AttemptResult<T>()
    class Redirect(val url: Url, val status: Int) : AttemptResult<Nothing>()
    class Refresh(val failedToken: String?) : AttemptResult<Nothing>()
    class Correct(val serverTime: Long) : AttemptResult<Nothing>()
}

/** 在实际引擎 execute 入口核对，后装的 Ktor 插件也不能修改受管字段或额外重发。 */
private class GuardedEngine(
    private val delegate: HttpClientEngine,
    val configuration: KtorClientConfig,
) : HttpClientEngine by delegate {
    @OptIn(InternalAPI::class)
    override fun install(client: HttpClient) { super<HttpClientEngine>.install(client) }

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val approval = data.attributes.getOrNull(approvalKey) ?: throw PolicyConfigurationException("Request lacks executor approval")
        approval.verify(data)
        return delegate.execute(data)
    }
}

private class SendApproval(
    private val config: KtorClientConfig,
    private val resolvedOrigin: Origin?,
    private val approvedOrigin: Origin,
    private val managed: Map<String, List<String>?>,
    private val requestId: String,
) {
    private val mutex = Mutex()
    private var sent = false

    @OptIn(InternalAPI::class)
    suspend fun verify(data: HttpRequestData) = mutex.withLock {
        if (sent || config.targetPolicy.validate(data.url, resolvedOrigin) != approvedOrigin) throw RequestGuardException(requestId)
        val finalHeaders = Headers.build { data.forEachHeader { name, value -> append(name, value) } }
        if (managed.any { (name, values) -> finalHeaders.getAll(name) != values }) throw RequestGuardException(requestId)
        sent = true
    }
}

private fun byteContent(value: ByteArray, type: ContentType) = object : OutgoingContent.ByteArrayContent() {
    override val contentType = type
    override val contentLength = value.size.toLong()
    override fun bytes(): ByteArray = value
}

@OptIn(InternalAPI::class)
private fun effectiveHeaders(headers: Headers, content: OutgoingContent): Headers = Headers.build {
    mergeHeaders(headers, content) { name, value -> append(name, value) }
}

private fun disposition(name: String, fileName: String?): String {
    require(listOfNotNull(name, fileName).none { '\r' in it || '\n' in it || '\u0000' in it }) { "Invalid multipart name" }
    var value = ContentDisposition("form-data").withParameter(ContentDisposition.Parameters.Name, name)
    if (fileName != null) value = value.withParameter(ContentDisposition.Parameters.FileName, fileName)
    return value.toString()
}

private class MultipartPiece(val prefix: ByteArray, val source: UploadSource? = null)

private fun multipartPieces(body: KtorBody.Multipart, boundary: String): List<MultipartPiece> = buildList {
    body.fields.forEach { name, values ->
        val header = disposition(name, null)
        values.forEach { value -> add(MultipartPiece("--$boundary\r\nContent-Disposition: $header\r\n\r\n$value\r\n".encodeToByteArray())) }
    }
    body.files.forEach { part ->
        val header = disposition(part.fieldName, part.fileName)
        add(MultipartPiece("--$boundary\r\nContent-Disposition: $header\r\nContent-Type: ${part.source.contentType}\r\n\r\n".encodeToByteArray(), part.source))
        add(MultipartPiece("\r\n".encodeToByteArray()))
    }
    add(MultipartPiece("--$boundary--\r\n".encodeToByteArray()))
}

private fun multipartLength(pieces: List<MultipartPiece>): Long? {
    var total = 0L
    pieces.forEach { piece ->
        val length = if (piece.source != null) piece.source.contentLength ?: return null else 0L
        require(length >= 0 && length <= Long.MAX_VALUE - piece.prefix.size - total) { "Invalid upload length" }
        total += piece.prefix.size + length
    }
    return total
}

private class UploadProgress(private val options: RequestOptions, private val total: Long?) {
    private var sent = 0L
    private var progressAt = TimeSource.Monotonic.markNow()

    suspend fun advance(count: Int) {
        sent += count
        if (progressAt.elapsedNow().inWholeMilliseconds >= options.progressIntervalMillis) {
            options.onProgress?.invoke(TransferProgress(sent, total))
            progressAt = TimeSource.Monotonic.markNow()
        }
    }

    suspend fun finish() { options.onProgress?.invoke(TransferProgress(sent, total)) }
}

private suspend fun copyUpload(source: UploadSource, output: ByteWriteChannel, progress: UploadProgress) {
    val channel = storageOperation { source.open() }
    var failure: Throwable? = null
    try {
        val buffer = ByteArray(64 * 1024)
        var sent = 0L
        while (true) {
            val count = storageOperation { channel.readAvailable(buffer) }
            if (count < 0) break
            if (source.contentLength != null && count > source.contentLength!! - sent) {
                throw StorageException(IllegalStateException("Upload source length changed"))
            }
            output.writeFully(buffer, 0, count)
            sent += count
            progress.advance(count)
        }
        if (source.contentLength != null && sent != source.contentLength) {
            throw StorageException(IllegalStateException("Upload source length changed"))
        }
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        withContext(NonCancellable) {
            try { storageOperation { source.close(channel, failure) } } catch (cleanup: Throwable) {
                if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
            }
        }
    }
}

private suspend fun readAll(response: KtorResponse, limit: Int): ByteArray {
    val chunks = mutableListOf<ByteArray>()
    var size = 0
    val buffer = ByteArray(8192)
    while (true) {
        val count = response.readAvailable(buffer)
        if (count < 0) break
        if (count > limit - size) throw ResponseTooLargeException(limit.toLong(), response.requestId)
        chunks += buffer.copyOf(count)
        size += count
    }
    val result = ByteArray(size)
    var offset = 0
    chunks.forEach { it.copyInto(result, offset); offset += it.size }
    return result
}

private suspend fun readPrefix(response: KtorResponse, limit: Int): Pair<ByteArray, Boolean> {
    val output = ByteArray(limit)
    var size = 0
    while (size < limit) {
        val count = response.readAvailable(output, size, limit - size)
        if (count < 0) return output.copyOf(size) to false
        size += count
    }
    val extra = response.readAvailable(ByteArray(1))
    return output to (extra >= 0)
}
