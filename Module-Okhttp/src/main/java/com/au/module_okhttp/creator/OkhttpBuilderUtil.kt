package com.au.module_okhttp.creator

import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * 信任所有证书的OkHttpClient
 * @param protocol 协议版本，默认TLS, 要“尽量用 1.3 且兼容旧环境”，用 TLS 更合适；要“强制只用 1.3”，用 TLSv1.3 。
 * 
 * 传 TLSv1.3 ：明确只创建/初始化 TLS 1.3 的上下文。后续用它生成的 SSLSocketFactory 更倾向于只支持/只启用 TLS 1.3（具体还受 Provider/实现影响）。
 * 如果运行环境（Android 系统/安全提供者/Conscrypt）或对端不支持 TLS 1.3，
 * 更容易直接握手失败 （常见是 SSLHandshakeException 、 protocol_version 之类）。
 * 
 * 传 TLS ：这是一个**“通用别名”**（不是具体版本），表示“使用 TLS 协议族”。
 * Provider 通常会返回一个能覆盖多个版本的实现（比如 TLS 1.2 + 1.3，视系统支持而定），
 * 握手时会按客户端/服务端共同支持的最高版本协商（有的环境会优先到 1.3，否则回落到 1.2）。因此 兼容性更好 。
 */
fun OkHttpClient.Builder.myTrustAll(protocol:String = "TLS") : OkHttpClient.Builder {
    val trustAllMgr = TrustAllCertsManager()
    val socketFactory = javax.net.ssl.SSLContext
        .getInstance(protocol)
        .also {
            it.init(null, arrayOf<javax.net.ssl.TrustManager>(trustAllMgr), java.security.SecureRandom())
        }.socketFactory
    this
        .sslSocketFactory(socketFactory, trustAllMgr)
        .hostnameVerifier(TrustAllHostnameVerifier())

    return this
}

/**
 * 自定义信任证书
 */
fun OkHttpClient.Builder.myTrustCert(certStr:String): OkHttpClient.Builder {
    // 创建证书
    val certificate = createCertificateFromPem(certStr)
    val handshakeCertificates = createHandshakeCertificates(certificate)

    // 创建OkHttpClient
    this
        .sslSocketFactory(
            handshakeCertificates.sslSocketFactory(),
            handshakeCertificates.trustManager
        )
        .hostnameVerifier { _, _ -> true } // 如果使用自签名证书，可能需要验证主机名
    return this
}

/**
 * 创建HandshakeCertificates，用于SSL/TLS握手
 */
private fun createHandshakeCertificates(vararg certificates: X509Certificate): HandshakeCertificates {
    val builder = HandshakeCertificates.Builder()

    // 添加自定义证书到信任列表
    certificates.forEach { certificate ->
        builder.addTrustedCertificate(certificate)
    }

    return builder.build()
}

/**
 * 将PEM格式的证书字符串转换为X509Certificate对象
 */
private fun createCertificateFromPem(pemString: String): X509Certificate {
    // 移除PEM格式的标记和换行符
    val cleanedPem = pemString
        .replace("-----BEGIN CERTIFICATE-----", "")
        .replace("-----END CERTIFICATE-----", "")
        .replace("\n", "")
        .trim()

    // Base64解码
    val certificateBytes = Base64.decode(cleanedPem, Base64.DEFAULT)

    // 创建证书工厂并生成证书
    val certificateFactory = CertificateFactory.getInstance("X.509")
    return certificateFactory.generateCertificate(
        ByteArrayInputStream(certificateBytes)
    ) as X509Certificate
}