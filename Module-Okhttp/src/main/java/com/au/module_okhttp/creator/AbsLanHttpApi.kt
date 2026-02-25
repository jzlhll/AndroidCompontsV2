package com.au.module_okhttp.creator

import android.content.Context
import android.net.Uri
import com.au.module_android.log.logdNoFile
import com.au.module_android.utilsmedia.UriParsedInfo
import com.au.module_kson.toKsonStringLimited
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

abstract class AbsLanApi {
    abstract val httpClient : OkHttpClient

    abstract fun context(): Context

    /**
     * 获取基础URL
     * @return 基础URL字符串
     */
    abstract suspend fun getHost():String?

    /**
     * 设置请求头
     */
    private fun Request.Builder.setupHeader(map:Map<String, Any?>? = null): Request.Builder {
        map?.forEach { (key, value) ->
            if (value != null) {
                val valueStr = when (value) {
                    is String,
                    is Number,
                    is Boolean, -> value.toString()
                    else -> value.toKsonStringLimited()
                }

                this@setupHeader.addHeader(key, valueStr)
            }
        }
        return this
    }

    open fun generateMagicCode(): String {
        return "LANApi_${UUID.randomUUID().toString().subSequence(0, 6)}"
    }

    @Throws
    suspend fun requestPostFile(
        api:String,
        headers: Map<String, Any?>? = null,
        queries: Map<String, Any?>? = null,
        uri: Uri,
        fileSize:Long,
        mime:String,
    ): String {
        val host = getHost() ?: throw Exception("getIpAddress() is null")

        val url = "${host}/${api.trimStart('/')}"
        val magicCode = generateMagicCode()
        val urlBuilder = url.toHttpUrl().newBuilder()
        queries?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value?.toString())
        }

        val builderUri = urlBuilder.build()

        logdNoFile{"$magicCode request Post: $builderUri"}

        val body = uri.asInputStreamRequestBody(context(), fileSize, mime)

        val request = Request.Builder()
            .url(builderUri)
            .setupHeader(headers)
            .post(body)
            .build()

        val resultStr = request.awaitHttpResultStr(httpClient) ?: "{}"
        logdNoFile{"$magicCode result: $resultStr"}
        return resultStr
    }

    data class FileFormData(
        val uri: Uri,
        val fileName:String,
        val fileSize:Long,
        val mime:String,
    )

    @Throws
    suspend fun requestPostFileFormData(
        api:String,
        headers: Map<String, Any?>? = null,
        queries: Map<String, Any?>? = null,
        fileFormDataList: List<FileFormData>,
        textFormDataList: Map<String, String>? = null,
    ): String {
        val host = getHost() ?: throw Exception("getHost() is null")
        val url = "${host}/${api.trimStart('/')}"
        val magicCode = generateMagicCode()
        val urlBuilder = url.toHttpUrl().newBuilder()
        queries?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value?.toString())
        }

        val builderUri = urlBuilder.build()

        logdNoFile{"$magicCode request Post: $builderUri"}

        //todo FullSizeRender 相关
        val multipartBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)

        textFormDataList?.forEach { (key, value) ->
            multipartBodyBuilder.addFormDataPart(key, value)
        }
        fileFormDataList.forEach {
            val body = it.uri.asInputStreamRequestBody(context(), it.fileSize, it.mime)
            multipartBodyBuilder.addFormDataPart("file", it.fileName, body)
        }

        val request = Request.Builder()
            .url(builderUri)
            .setupHeader(headers)
            .post(multipartBodyBuilder.build())
            .build()
        val resultStr = request.awaitHttpResultStr(httpClient) ?: "{}"
        logdNoFile{"$magicCode result: $resultStr"}
        return resultStr
    }

    @Throws
    suspend fun requestGetRespStr(
        api:String,
        headers: Map<String, Any?>? = null,
        queries: Map<String, Any?>? = null,
    ): String {
        val url = "${getHost()}/${api.trimStart('/')}"
        val magicCode = generateMagicCode()
        val urlBuilder = url.toHttpUrl().newBuilder()
        queries?.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value?.toString())
        }

        val builderUri = urlBuilder.build()
        logdNoFile{"$magicCode request Get: $builderUri"}
        val request = Request.Builder()
            .url(builderUri)
            .setupHeader(headers)
            .get()
            .build()
        val resultStr = request.awaitHttpResultStr(httpClient) ?: "{}"
        logdNoFile{"$magicCode result: $resultStr"}
        return resultStr
    }
}

fun UriParsedInfo.toFileFormData(): AbsLanApi.FileFormData {
    return AbsLanApi.FileFormData(
        uri = this.uri,
        fileName = this.name,
        fileSize = this.fileLength,
        mime = this.mimeType,
    )
}