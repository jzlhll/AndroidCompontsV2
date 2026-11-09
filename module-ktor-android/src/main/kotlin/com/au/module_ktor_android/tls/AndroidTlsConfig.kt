package com.au.module_ktor_android.tls

import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

enum class HostnamePolicy { Verify, Off }

sealed class CertificateTrust {
    class System : CertificateTrust()
    class AppendCertificates(certificates: List<X509Certificate>) : CertificateTrust() {
        val certificates = certificates.toList()
        init { require(certificates.isNotEmpty()) }
    }
    class ReplaceCertificates(certificates: List<X509Certificate>) : CertificateTrust() {
        val certificates = certificates.toList()
        init { require(certificates.isNotEmpty()) }
    }
    class All : CertificateTrust()
}

/** 证书链信任与 hostname 校验独立配置，只作用于所属客户端。 */
class AndroidTlsConfig(
    val trust: CertificateTrust = CertificateTrust.System(),
    val hostname: HostnamePolicy = HostnamePolicy.Verify,
) {
    fun applyTo(builder: OkHttpClient.Builder) {
        val manager = when (trust) {
            is CertificateTrust.System -> null
            is CertificateTrust.ReplaceCertificates -> certificateManager(trust.certificates)
            is CertificateTrust.AppendCertificates -> CombinedTrustManager(managers(null), certificateManager(trust.certificates))
            is CertificateTrust.All -> object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        }
        if (manager != null) {
            val ssl = SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf<TrustManager>(manager), SecureRandom())
            builder.sslSocketFactory(ssl.socketFactory, manager)
        }
        if (hostname == HostnamePolicy.Off) builder.hostnameVerifier { _, _ -> true }
    }
}

private fun certificateManager(certificates: List<X509Certificate>): X509TrustManager {
    val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
    certificates.forEachIndexed { index, certificate -> store.setCertificateEntry("certificate-$index", certificate) }
    return managers(store)
}

private fun managers(store: KeyStore?): X509TrustManager {
    val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    factory.init(store)
    return factory.trustManagers.filterIsInstance<X509TrustManager>().single()
}

private class CombinedTrustManager(private val system: X509TrustManager, private val additional: X509TrustManager) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
        try { system.checkClientTrusted(chain, authType) } catch (error: CertificateException) {
            try { additional.checkClientTrusted(chain, authType) } catch (failure: CertificateException) { failure.addSuppressed(error); throw failure }
        }
    }
    override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
        try { system.checkServerTrusted(chain, authType) } catch (error: CertificateException) {
            try { additional.checkServerTrusted(chain, authType) } catch (failure: CertificateException) { failure.addSuppressed(error); throw failure }
        }
    }
    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + additional.acceptedIssuers
}
