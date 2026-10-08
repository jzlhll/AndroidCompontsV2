package com.au.module_okhttp.interceptors

import com.au.module_android.log.logdNoFile
import com.au.module_okhttp.exceptions.RefreshTokenExpiredException
import com.au.module_okhttp.exceptions.TimestampErrorException
import com.au.module_okhttp.exceptions.TokenExpiredException
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 简易重试拦截器，使用时保持 OkHttpClient.Builder.retryOnConnectionFailure(false)。
 * 必须通过 addInterceptor 在 PretreatmentInterceptor 之前添加。
 * 1. 根据异常类型和请求体可重发性进行有限次数的网络重试，不判断剩余路由。
 * 2. 支持时间戳纠正和 Token 刷新后的业务重试。
 */
class SimpleRetryInterceptor(
    val headersResetBlock:(Request)-> Request,
    val timestampOffsetBlock:(Long)->Unit,
    val tokenExpiredBlock:((String)->Boolean)?=null,
    val refreshTokenExpiredBlock:((String)->Unit)?=null) : Interceptor {
    private val retryMaxCount = 3

    override fun intercept(chain: Interceptor.Chain): Response {
        var retryCount = 0
        var request = chain.request()
        var errorException: Exception? = null
        var isTimestampAlreadyRetry = false
        val call = chain.call()

        while (true) {
            try {
                if (call.isCanceled()) {
                    throw IOException("Canceled")
                }
                return chain.proceed(request)
            } catch (e: IOException) {
                errorException = e
                val isRecoverable = recover(e, call, request)
                if (!isRecoverable) {
                    break
                }
            } catch (e: TimestampErrorException) {
                errorException = e
                if (e.hasTimestampInfo) {
                    timestampOffsetBlock(e.timestampOffset)
                }
                if (isTimestampAlreadyRetry || !e.hasTimestampInfo) {
                    break
                } else {
                    isTimestampAlreadyRetry = true
                }
            } catch (e: TokenExpiredException) {
                errorException = e
                //处理一下，立刻继续上抛
                if (tokenExpiredBlock?.invoke(e.message ?: "") == true) {
                    //token 刷新成功，则可以继续到 retryCount
                } else {
                    break
                }
            } catch (e: RefreshTokenExpiredException) {
                errorException = e
                //处理一下，立刻继续上抛
                refreshTokenExpiredBlock?.invoke(e.message ?: "")
                break
            }

            // 业务异常同样不能重复发送一次性或双工请求体。
            val body = request.body
            if (retryCount >= retryMaxCount || call.isCanceled() ||
                Thread.currentThread().isInterrupted ||
                body?.isOneShot() == true || body?.isDuplex() == true
            ) {
                break
            }
            retryCount++
            logdNoFile { "retry url ${request.url} exception: ${errorException.message}" }
            request = headersResetBlock(request)
            try {
                Thread.sleep(200) // 重试前略微等待网络恢复。
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while waiting to retry").apply {
                    initCause(e)
                }
            }
        }
        throw errorException
    }

    private fun recover(
        e: IOException,
        call: Call,
        userRequest: Request,
    ): Boolean {
        if (call.isCanceled() || Thread.currentThread().isInterrupted) return false

        // 无法确认发送进度，保守地禁止重复发送一次性或双工请求体。
        val body = userRequest.body
        if (body?.isOneShot() == true || body?.isDuplex() == true) return false

        return when {
            e is FileNotFoundException -> false
            e is UnknownHostException -> false
            e is UnknownServiceException -> false
            e is ProtocolException -> false
            e is InterruptedIOException -> false
            e is SSLPeerUnverifiedException -> false
            e is SSLHandshakeException && e.cause is CertificateException -> false
            else -> true
        }
    }
}
