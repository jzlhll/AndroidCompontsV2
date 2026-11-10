package com.au.module_ktor_base.policy

import com.au.module_ktor_base.error.RedirectException
import com.au.module_ktor_base.error.TargetRejectedException
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import io.ktor.http.takeFrom

class RedirectPolicy(val maxHops: Int = 5) {
    init { require(maxHops >= 0) }

    fun isRedirect(status: Int): Boolean = status in setOf(301, 302, 303, 307, 308)

    fun resolve(
        location: String?,
        current: Url,
        targetPolicy: TargetPolicy,
        resolvedOrigin: Origin?,
        requestId: String,
    ): Url {
        if (location.isNullOrBlank()) throw RedirectException(requestId)
        try {
            targetPolicy.validateRawLocation(location)
            val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(location)
            if (hasScheme && !Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(location)) {
                throw RedirectException(requestId)
            }
            val builder = when {
                hasScheme -> URLBuilder().takeFrom(location)
                location.startsWith("//") -> URLBuilder(protocol = current.protocol).takeFrom(location)
                else -> URLBuilder().takeFrom(current).apply {
                    encodedParameters.clear()
                    trailingQuery = false
                    takeFrom(location)
                }
            }
            val segments = mutableListOf<String>()
            builder.encodedPathSegments.forEach { segment ->
                when (segment.decodeURLPart()) {
                    "." -> Unit
                    ".." -> if (segments.isNotEmpty() && segments.last().isNotEmpty()) segments.removeAt(segments.lastIndex)
                    else -> segments += segment
                }
            }
            if (builder.encodedPathSegments.lastOrNull()?.decodeURLPart() in setOf(".", "..")) segments += ""
            builder.encodedPathSegments = segments
            val resolved = builder.build()
            targetPolicy.validate(resolved, resolvedOrigin)
            return resolved
        } catch (error: IllegalArgumentException) {
            throw RedirectException(requestId, error)
        } catch (error: TargetRejectedException) {
            throw RedirectException(requestId, error)
        }
    }
}
