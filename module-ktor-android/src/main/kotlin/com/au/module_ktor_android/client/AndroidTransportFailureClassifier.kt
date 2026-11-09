package com.au.module_ktor_android.client

import com.au.module_ktor_base.error.StorageException
import com.au.module_ktor_base.error.TransportFailure
import com.au.module_ktor_base.error.TransportFailureClassifier
import com.au.module_ktor_base.error.classifyKtorFailure
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** 在网络边界识别 JVM 异常，文件错误不进入 IOException 兜底。 */
class AndroidTransportFailureClassifier : TransportFailureClassifier {
    override fun classify(cause: Throwable): TransportFailure? {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = cause
        while (current != null && chain.size < 16 && chain.none { it === current }) {
            chain += current
            current = current.cause
        }
        if (chain.any { it is StorageException }) return null
        if (chain.any { it is CertificateException || it is SSLPeerUnverifiedException }) return TransportFailure.TlsCertificate
        if (chain.any { it is SSLHandshakeException || it is SSLException }) return TransportFailure.TlsHandshake
        chain.forEach { classifyKtorFailure(it)?.let { result -> return result } }
        return when {
            chain.any { it is UnknownHostException } -> TransportFailure.Dns
            chain.any { it is SocketTimeoutException } -> TransportFailure.SocketTimeout
            chain.any { it is ConnectException || it is NoRouteToHostException } -> TransportFailure.Connect
            chain.any { it is ProtocolException } -> TransportFailure.Protocol
            chain.any { it is SocketException || it is EOFException } -> TransportFailure.ConnectionLost
            chain.any { it is IOException } -> TransportFailure.Unknown
            else -> null
        }
    }
}
