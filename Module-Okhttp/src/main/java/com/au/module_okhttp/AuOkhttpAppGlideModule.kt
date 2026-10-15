package com.au.module_okhttp

import android.content.Context
import android.util.Log
import com.au.module_android.glide.GlideFailureListener
import com.au.module_android.glide.applyGlideCacheConfig
import com.au.module_android.glide.glideCacheConfig
import com.au.module_okhttp.beans.OkhttpInitParams
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
@Excludes(OkHttpLibraryGlideModule::class)
class AuOkhttpAppGlideModule : AppGlideModule() {
    companion object Companion {
        private const val NO_SSL_CHECK = true
        private var _okhttpClient : OkHttpClient? = null
        private fun okHttpClient() : OkHttpClient {
            val client = _okhttpClient
            if (client == null) {
                //添加okhttp的使用。
                val builder = OkhttpGlobal.createCertOkHttpBuilder(OkhttpInitParams().also {
                    it.okHttpEnableTrustAllCertificates = NO_SSL_CHECK
                })
                val c = builder.build()
                _okhttpClient = c
                return c
            } else {
                return client
            }
        }
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.apply {
            applyGlideCacheConfig(context, context.glideCacheConfig)
            // 原生 WARN 无法区分缓存探测；统一由 listener 分类并保留真实失败堆栈。
            setLogLevel(Log.ERROR)
            addGlobalRequestListener(GlideFailureListener<Any>())
            // 普通图片优先使用 565，单图预览和整行大图由入口显式选择 8888。
            setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_RGB_565).disallowHardwareConfig())
        }
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient()))
    }
}