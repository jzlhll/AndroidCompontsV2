package com.au.module_ktor_base.client

import com.au.module_ktor_base.transfer.KtorBody
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.takeFrom

class KtorRequest(
    val path: String,
    val method: HttpMethod = HttpMethod.Get,
    parameters: Parameters = Parameters.Empty,
    headers: Headers = Headers.Empty,
    val body: KtorBody = KtorBody.Empty(),
) {
    val parameters = Parameters.build { appendAll(parameters) }
    val headers = Headers.build { appendAll(headers) }

    init {
        require(!headers.contains("Host")) { "Host is derived from the approved URL" }
        require(!path.startsWith("//") && ':' !in path && '?' !in path && '#' !in path && '\\' !in path &&
            path.none { it < ' ' } && path.split('/').none { it == "." || it == ".." }) { "Expected unencoded API path" }
    }

    fun resolve(base: Url): Url = URLBuilder().takeFrom(base).apply {
        if (path.isNotEmpty()) appendPathSegments(path.trimStart('/'), encodeSlash = false)
        parameters.appendAll(this@KtorRequest.parameters)
    }.build()
}

fun parseBaseUrl(value: String): Url? {
    if (value.isBlank()) return null
    require(Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(value)) { "Expected absolute HTTP base URL" }
    require('?' !in value && '#' !in value && '\\' !in value && value.none { it <= ' ' }) { "Invalid base URL" }
    return Url(value).also(::validateBaseUrl)
}

fun validateBaseUrl(url: Url) {
    require(url.protocolOrNull?.name in setOf("http", "https") && url.host.isNotBlank() &&
        url.user == null && url.password == null && url.fragment.isEmpty() &&
        url.parameters.names().isEmpty() && !url.trailingQuery) { "Invalid base URL" }
}
