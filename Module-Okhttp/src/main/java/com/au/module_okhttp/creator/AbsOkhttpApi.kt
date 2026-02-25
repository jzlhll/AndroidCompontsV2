package com.au.module_okhttp.creator

import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_gson.fromGson
import com.au.module_kson.toKsonStringLimited
import com.au.module_okhttp.OkhttpGlobal
import com.au.module_okhttp.api.BaseBean
import com.au.module_okhttp.api.ResultBean
import com.au.module_okhttp.api.ResultBeanList
import com.au.module_okhttp.exceptions.NoBaseUrlException
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.util.UUID

abstract class AbsOkhttpApi(private val cacheMgr: AbsOkhttpCacheManager = NoOkhttpCacheManager()) {
    private val subScope = Globals.createBackAppScope("OkhttpApi Coroutine catch: ")

    /**
     * 获取基础URL
     * @return 基础URL字符串
     */
    abstract fun getBaseUrl():String
    
    /**
     * 获取应用ID
     * @return 应用ID字符串
     */
    abstract fun getAppId():String
    
    /**
     * 获取应用密钥
     * @return 应用密钥字符串
     */
    abstract fun getAppKey():String
    
    /**
     * 获取令牌
     * @return 令牌字符串
     */
    abstract fun getApiToken():String

    /**
     * 设置请求头
     * @param builder Request.Builder对象
     */
    open fun setupHeader(builder: Request.Builder, needToken: Boolean) {
        builder.addHeader("X-APP-ID", getAppId())
        builder.addHeader("X-APP-KEY", getAppKey())
        if (needToken) {
            builder.addHeader("Authorization", getApiToken())
        }
    }

    /**
     * 重置request其中的部分header
     */
    fun refreshRequest(request: Request) : Request {
        val builder = request.newBuilder()
        val oldHasToken = !request.header("Authorization").isNullOrEmpty()
        if (oldHasToken) {
            builder.removeHeader("Authorization")
            builder.addHeader("Authorization", getApiToken())
        }
        return builder.build()
    }

    /**
     * 转成基础 Api Bean
     */
    fun String.toApiBaseBean() : BaseBean? {
        val r = this.fromGson<BaseBean>()
        logdNoFile("🌟kson") { "base Bean $this, $r" }
        return r
    }

    open fun generateMagicCode(): String {
        return "🛜${UUID.randomUUID().toString().subSequence(0, 6)}"
    }

    /**
     * 直接请求，获取得到的json字符串
     * 如果想转成 baseBean，可以配套 [toApiBaseBean]
     *
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @param method HTTP请求方法
     * @return 响应的json字符串
     * @throws NoBaseUrlException 当基础URL为空时抛出
     */
    @Throws
    suspend fun String.requestApi(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
        method: HttpMethod,
        needToken: Boolean = true
    ): String {
        val baseUrl = getBaseUrl()
        if (baseUrl.isEmpty()) {
            throw NoBaseUrlException("No Base Url!")
        }

        val url = "${baseUrl.trimEnd('/')}/${this.trimStart('/')}"

        val magicCode = generateMagicCode()
        val builder = Request.Builder()
        val paramsStr = params?.toKsonStringLimited() ?: "{}"
        logdNoFile(javaClass = this@AbsOkhttpApi.javaClass){"request [$magicCode]: $url method: $method paramsStr $paramsStr"}

        when (method) {
            HttpMethod.GET -> {
                val urlBuilder = url.toHttpUrl().newBuilder()
                params?.forEach { (key, value) ->
                    urlBuilder.addQueryParameter(key, value.toString())
                }
                builder.url(urlBuilder.build())
                when (method) {
                    HttpMethod.GET -> builder.get()
                    else -> {}
                }
            }
            else  -> {
                val body = paramsStr.toParamsStrRequestBody()
                builder.url(url)
                when (method) {
                    HttpMethod.POST -> builder.post(body)
                    HttpMethod.PATCH -> builder.patch(body)
                    HttpMethod.DELETE -> builder.delete(body)
                    else -> {}
                }
            }
        }
        setupHeader(builder, needToken)

        val request = builder.build()

        val resultStr = request.awaitHttpResultStr(OkhttpGlobal.okHttpClient(timeOutMode)) ?: "{}"
        logdNoFile(javaClass = this@AbsOkhttpApi.javaClass){"result [$magicCode]: $resultStr"}

        subScope.launch {
            cacheMgr.cacheToDisk(url, paramsStr, method, resultStr)
        }
        return resultStr
    }

    /**
     * 获取数据: GET，直接得到T
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return T对象
     */
    suspend inline fun <reified T> String.requestResultGet(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
    ): T? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.GET)
        return resultStr.fromGson<ResultBean<T>>()?.data
    }

    /**
     * 获取数据: POST，直接得到T
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return T对象
     */
    suspend inline fun <reified T> String.requestResultPost(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
        needToken: Boolean = true
    ): T? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.POST, needToken)
        val resultBean = resultStr.fromGson<ResultBean<T>>()
        val d = resultBean?.data
        return d
    }

    /**
     * 获取数据: DELETE，直接得到T
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return T对象
     */
    suspend inline fun <reified T> String.requestResultDelete(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
    ): T? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.DELETE)
        return resultStr.fromGson<ResultBean<T>>()?.data
    }

    /**
     * 获取数据: PATCH，直接得到T
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return T对象
     */
    suspend inline fun <reified T> String.requestResultPatch(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
    ): T? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.PATCH)
        return resultStr.fromGson<ResultBean<T>>()?.data
    }

    /**
     * 获取数据列表: GET，直接得到List<T>
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return List<T>对象
     */
    suspend inline fun <reified T> String.requestResultListGet(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
        needToken: Boolean = true
    ): List<T>? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.GET, needToken)
        return resultStr.fromGson<ResultBeanList<T>>()?.data
    }

    /**
     * 获取数据列表: POST，直接得到List<T>
     * @receiver API路径
     * @param params 请求参数
     * @param timeOutMode 超时模式
     * @return List<T>对象
     */
    suspend inline fun <reified T> String.requestResultListPost(
        params: Map<String, Any?>? = null,
        timeOutMode: Int = 0,
    ): List<T>? {
        val resultStr = this.requestApi(params, timeOutMode, HttpMethod.POST)
        return resultStr.fromGson<ResultBeanList<T>>()?.data
    }

    fun clearCache() {
    }
}