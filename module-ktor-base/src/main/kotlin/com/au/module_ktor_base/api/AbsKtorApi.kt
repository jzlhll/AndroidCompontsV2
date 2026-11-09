package com.au.module_ktor_base.api

import com.au.module_ktor_base.client.KtorRequest
import com.au.module_ktor_base.client.KtorRequestExecutor
import com.au.module_ktor_base.client.KtorResponse
import com.au.module_ktor_base.config.RequestOptions
import com.au.module_ktor_base.policy.ApiResponsePolicy
import com.au.module_ktor_base.transfer.DownloadSink
import com.au.module_ktor_base.transfer.KtorBody
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/** API 只声明请求与响应协议，不拥有客户端资源。 */
abstract class AbsKtorApi(
    protected val executor: KtorRequestExecutor,
    private val baseUrlProvider: suspend () -> Url?,
    private val responsePolicy: ApiResponsePolicy? = null,
) {
    protected open val headerProvider: RequestHeaderProvider? = null

    suspend fun <T> request(
        path: String,
        serializer: KSerializer<T>,
        method: HttpMethod = HttpMethod.Get,
        parameters: Parameters = Parameters.Empty,
        body: KtorBody = KtorBody.Empty(),
        headers: Headers = Headers.Empty,
        options: RequestOptions = RequestOptions(),
    ): T = executor.json(baseUrlProvider, KtorRequest(path, method, parameters, headers, body), serializer, options, headerProvider, responsePolicy)

    suspend inline fun <reified T> request(
        path: String,
        method: HttpMethod = HttpMethod.Get,
        parameters: Parameters = Parameters.Empty,
        body: KtorBody = KtorBody.Empty(),
        headers: Headers = Headers.Empty,
        options: RequestOptions = RequestOptions(),
    ): T = request(path, serializer<T>(), method, parameters, body, headers, options)

    suspend fun text(request: KtorRequest, options: RequestOptions = RequestOptions()): String =
        executor.text(baseUrlProvider, request, options, headerProvider)

    suspend fun bytes(request: KtorRequest, options: RequestOptions = RequestOptions()): ByteArray =
        executor.bytes(baseUrlProvider, request, options, headerProvider)

    suspend fun <T> execute(
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        block: suspend (KtorResponse) -> T,
    ): T = executor.execute(baseUrlProvider, request, options, headerProvider, block)

    suspend fun download(request: KtorRequest, sink: DownloadSink, options: RequestOptions = RequestOptions()) =
        executor.download(baseUrlProvider, request, sink, options, headerProvider)

    suspend fun <T> stream(
        request: KtorRequest,
        options: RequestOptions = RequestOptions(),
        block: suspend (KtorResponse) -> T,
    ): T = executor.stream(baseUrlProvider, request, options, headerProvider, block)

    fun <T> requestFlow(
        path: String,
        serializer: KSerializer<T>,
        method: HttpMethod = HttpMethod.Get,
        parameters: Parameters = Parameters.Empty,
        body: KtorBody = KtorBody.Empty(),
        options: RequestOptions = RequestOptions(),
    ): Flow<T> = flow { emit(request(path, serializer, method, parameters, body, options = options)) }

    fun <T> jsonBody(value: T, serializer: KSerializer<T>): KtorBody =
        KtorBody.JsonValue(executor.config.json.encodeToJsonElement(serializer, value))

    inline fun <reified T> jsonBody(value: T): KtorBody = jsonBody(value, serializer<T>())
}
