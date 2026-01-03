package com.au.module_okhttp.interceptors

import com.au.module_android.log.logdNoFile
import com.au.module_okhttp.exceptions.AuTimestampErrorException
import com.au.module_okhttp.exceptions.AuTokenExpiredException
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.connection.RealCall
import okhttp3.internal.http2.ConnectionShutdownException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * 参考 RetryAndFollowUpInterceptor 实现一个简易够用的版本。如果使用了该拦截器，请将okhttpBuilder移除retryWhenFail。
 * 必须在OkhttpPretreatmentInterceptor之前添加。
 * 1. 内部网络问题重试支持，简化实现
 * 2. 重试timestamp
 */
class SimpleRetryInterceptor(
    val headersResetBlock:(Request)-> Request,
    val timestampOffsetBlock:(Long)->Unit,
    val tokenExpiredBlock:((String)->Unit)?=null) : Interceptor {
    private val retryMaxCount = 3

    override fun intercept(chain: Interceptor.Chain): Response {
        var retryCount = 0
        var request = chain.request()
        var errorException: Exception? = null
        var isTimestampAlreadyRetry = false
        val call = chain.call() as? RealCall

        while (true) {
            var response: Response
            try {
                if (call?.isCanceled() == true) {
                    throw IOException("Canceled")
                }
                response = chain.proceed(request)
                return response
            }  catch (e: IOException) {
                errorException = e
                // An attempt to communicate with a server failed. The request may have been sent.
                val isRecoverable = recover(e,  call, request)
                if (!isRecoverable) {
                    break
                }
            } catch (e: IOException) {
                errorException = e
                // An attempt to communicate with a server failed. The request may have been sent.
                if (!isRecoverable(e, requestSendStarted = e !is ConnectionShutdownException)) {
                    break
                }
            } catch (e: AuTimestampErrorException) {
                errorException = e
                if (e.hasTimestampInfo) {
                    timestampOffsetBlock(e.timestampOffset)
                }
                if (isTimestampAlreadyRetry || !e.hasTimestampInfo) {
                    break
                } else {
                    isTimestampAlreadyRetry = true
                }
            } catch (e: AuTokenExpiredException) {
                errorException = e
                //处理一下，立刻继续上抛
                tokenExpiredBlock?.invoke(e.message ?: "")
                break
            }

            logdNoFile { "okhttp: retry url ${request.url} exception: ${errorException?.message}" }
            if (retryCount++ < retryMaxCount) {
                request = headersResetBlock(request)
                Thread.sleep(200) //重试的时候，略微延迟，等等网络。
            } else {
                break
            }
        }
        throw errorException
    }

    private fun requestIsOneShot(
        e: IOException,
        userRequest: Request,
    ): Boolean {
        val requestBody = userRequest.body
        return (requestBody != null && requestBody.isOneShot()) ||
                e is FileNotFoundException
    }

    private fun recover(
        e: IOException,
        call:RealCall?,
        userRequest: Request,
    ): Boolean {
        val requestSendStarted = e !is ConnectionShutdownException

        // We can't send the request body again.
        if (requestSendStarted && requestIsOneShot(e, userRequest)) return false

        // No more routes to attempt.
        if (call != null && !call.retryAfterFailure()) return false

        // This exception is fatal.
        if (!isRecoverable(e, requestSendStarted)) return false

        // For failure recovery, use the same route selector with a new connection.
        return true
    }


    private fun isRecoverable(e: IOException, requestSendStarted: Boolean): Boolean {
        // If there was a protocol problem, don't recover.
        if (e is ProtocolException) {
            return false
        }

        // If there was an interruption don't recover, but if there was a timeout connecting to a route
        // we should try the next route (if there is one).
        if (e is InterruptedIOException) {
            return e is SocketTimeoutException && !requestSendStarted
        }

        // Look for known client-side or negotiation errors that are unlikely to be fixed by trying
        // again with a different route.
        if (e is SSLHandshakeException) {
            // If the problem was a CertificateException from the X509TrustManager,
            // do not retry.
            if (e.cause is CertificateException) {
                return false
            }
        }
        if (e is SSLPeerUnverifiedException) {
            // e.g. a certificate pinning error.
            return false
        }
        // An example of one we might want to retry with a different route is a problem connecting to a
        // proxy and would manifest as a standard IOException. Unless it is one we know we should not
        // retry, we return true and try a new route.
        return true
    }
}