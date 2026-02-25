package com.au.module_okhttp.interceptors
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.ignoreError
import com.au.module_kson.fromKson
import com.au.module_okhttp.BuildConfig
import com.au.module_okhttp.api.BaseDataStrBean
import com.au.module_okhttp.exceptions.ApiErrorException
import com.au.module_okhttp.exceptions.ResponseErrorException
import com.au.module_okhttp.exceptions.TokenExpiredException
import com.au.module_okhttp.exceptions.TimestampErrorException
import com.au.module_okhttp.beans.CODE_OK
import com.au.module_okhttp.beans.CODE_TIMESTAMP_ERROR
import com.au.module_okhttp.beans.CODE_TOKEN_EXPIRED
import com.au.module_okhttp.beans.CODE_TOKEN_REFRESH_EXPIRED
import com.au.module_okhttp.exceptions.RefreshTokenExpiredException
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import org.json.JSONObject
import java.io.EOFException
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException
import kotlin.text.toInt

/**
 * 拦截器，对响应进行预处理。必须放在OkhttpSimpleRetryInterceptor之后。
 * 1. 检查错误码
 * 2. 检查token是否过期
 * 3. 时间戳偏移纠正
 */
class PretreatmentInterceptor(
    val checkIsRefreshTokenError: ((url:String?, httpRespCode:Int?, apiRespCode:String?)-> Boolean)?=null) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val responseCode = response.code
        //过滤掉sse
        if (request.isEventStream()) {
            return response
        }
        val responseBody = response.body ?: return response
        //过滤掉sse
        if (responseBody.isEventStream()) {
            return response
        }

        val url = request.url.toString()

        //现在不论如何都解析httpCode 和 content
        val error = checkHttpResponseCode(responseCode)
        val result = getCloneResult(response)

        // 1. 如果响应异常，但是 result 解析不出来。就直接报响应错误
        if (error != null && result == null) {
            throw ResponseErrorException(responseCode, error)
        }

        logdNoFile{"($error): $responseCode $result"}
        // 2. 我们直接在result 进行业务错误码解析，
        // 因为暂时我不能肯定是否影响错误的逻辑，一定不是httpSuccess200。所以不在上面直接 return。
        // later：优化。
        //所以这里不论是否是错误都会走，正常逻辑也会走。效率上大约拷贝+json 解析了一次。
        checkContentThrow(responseCode, url, result)
        return response //大概率不走了。
    }

    private fun ResponseBody.isEventStream(): Boolean {
        val contentType = contentType() ?: return false
        return contentType.type == "text" && contentType.subtype == "event-stream"
    }

    fun Request.isEventStream(): Boolean {
        return header("Accept")?.contains("text/event-stream") == true
    }

    /**
     * clone 后解析响应体
     */
    private fun getCloneResult(response: Response) : String? {
        if (bodyEncoded(response.headers)) {
            logdNoFile{"parseContentAndPrint response body is encoded"}
            return null
        }
        val responseBody = response.body
        if (responseBody == null) {
            logdNoFile{"parseContentAndPrint response body is null"}
            return null
        }
        val source = responseBody.source()
        source.request(Long.MAX_VALUE) // Buffer the entire body.
        val buffer: Buffer = source.buffer
        if (!isPlaintext(buffer)) {
            logdNoFile{"parseContentAndPrint response body is not plaintext"}
            return null
        }

        val contentLength = responseBody.contentLength()
        if (contentLength != 0L) {
            var charset: Charset? = utf8Charset
            val contentType: MediaType? = responseBody.contentType()
            if (contentType != null) {
                try {
                    charset = contentType.charset(utf8Charset)
                } catch (_: UnsupportedCharsetException) {
                    logdNoFile{"parseContentAndPrint response body charset is not supported"}
                    return null
                }
            }
            //clone 保证不消费 body
            val result: String = buffer.clone().readString(charset ?: utf8Charset)
            if (BuildConfig.DEBUG) {
                logdNoFile{ "parseContentAndPrint response body: $result" }
            }
            return result
        } else {
            logdNoFile{"parseContentAndPrint response contentLength is 0"}
            return null
        }
    }

    private fun checkContentThrow(responseCode: Int, url: String, result: String?) { //业务错误码
        if (result.isNullOrEmpty()) {
            return
        }
        val dataBean = ignoreError { result.fromKson<BaseDataStrBean>() }  ?: return
        logdNoFile("🌟kson") { "dataBean $dataBean" }
        val code = dataBean.code
        //由于刷新token判断比较复杂，所以交给业务实现。框架不做判断。做解耦。
        val isRefreshTokenError = checkIsRefreshTokenError?.invoke(url, responseCode, code) ?: false
        if (isRefreshTokenError) {
            throw RefreshTokenExpiredException(dataBean.msg ?: result)
        } else {
            when (code) {
                CODE_OK -> {
                    return
                }
                CODE_TIMESTAMP_ERROR -> {
                    val msg = dataBean.msg ?: result
//                    if (dataBean.has("data")) {//处理时间戳的偏移
//                        val data = dataBean.optJSONObject("data")
//                        if (data != null && data.has("timestamp")) {
//                            val timestamp = data.getLong("timestamp")
//                            val timestampOffset = timestamp - System.currentTimeMillis()
//                            throw TimestampErrorException(timestampOffset, true, msg)
//                        } else {
//                            throw TimestampErrorException(0, false, msg)
//                        }
//                    } else {
//                        throw TimestampErrorException(0, false, msg)
//                    }
                    val timestamp = System.currentTimeMillis() //todo
                    val timestampOffset = timestamp - System.currentTimeMillis()
                    throw TimestampErrorException(timestampOffset, false, msg)
                }
                CODE_TOKEN_REFRESH_EXPIRED -> {
                    throw RefreshTokenExpiredException(dataBean.msg ?: result)
                }
                CODE_TOKEN_EXPIRED -> {
                    throw TokenExpiredException(dataBean.msg ?: result)
                }
                else -> {
                    throw ApiErrorException(code, dataBean.msg ?: result, "")
                }
            }
        }
    }

    private val utf8Charset: Charset = Charset.forName("UTF-8")

    private fun bodyEncoded(headers: Headers): Boolean {
        val contentEncoding: String? = headers["Content-Encoding"]
        return contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)
    }

    @Throws(EOFException::class)
    fun isPlaintext(buffer: Buffer): Boolean {
        try {
            val prefix = Buffer()
            val byteCount: Long = if (buffer.size < 64) buffer.size else 64
            buffer.copyTo(prefix, 0, byteCount)
            for (i in 0..15) {
                if (prefix.exhausted()) {
                    break
                }
                val codePoint: Int = prefix.readUtf8CodePoint()
                if (Character.isISOControl(codePoint) && !Character.isWhitespace(codePoint)) {
                    return false
                }
            }
            return true
        } catch (_: EOFException) {
            return false // Truncated UTF-8 sequence.
        }
    }

    /**
     *检查响应码，没错错误，返回null
     */
    fun checkHttpResponseCode(code: Int): String? = when (code) {
        200 -> null
        202 -> Globals.getString(com.au.module_okhttp.R.string.http_202)
        400 -> Globals.getString(com.au.module_okhttp.R.string.http_400)
        401 -> Globals.getString(com.au.module_okhttp.R.string.http_401)
        403 -> Globals.getString(com.au.module_okhttp.R.string.http_403)
        404 -> Globals.getString(com.au.module_okhttp.R.string.http_404)
        405 -> Globals.getString(com.au.module_okhttp.R.string.http_405)
        408 -> Globals.getString(com.au.module_okhttp.R.string.http_408)
        500 -> Globals.getString(com.au.module_okhttp.R.string.http_500)
        501 -> Globals.getString(com.au.module_okhttp.R.string.http_501)
        502 -> Globals.getString(com.au.module_okhttp.R.string.http_502)
        503 -> Globals.getString(com.au.module_okhttp.R.string.http_503)
        504 -> Globals.getString(com.au.module_okhttp.R.string.http_504)
        505 -> Globals.getString(com.au.module_okhttp.R.string.http_505)
        else -> Globals.getString(com.au.module_okhttp.R.string.http_unknown)
    }
}