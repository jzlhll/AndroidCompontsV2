package com.au.module_ktor_base.policy

import com.au.module_ktor_base.error.PolicyConfigurationException
import com.au.module_ktor_base.error.TargetRejectedException
import io.ktor.http.Url

data class Origin private constructor(val scheme: String, val host: String, val port: Int) {
    companion object {
        fun from(url: Url): Origin {
            if (url.protocolOrNull?.name !in setOf("http", "https") || url.host.isBlank()) throw TargetRejectedException()
            return Origin(url.protocol.name.lowercase(), url.host.lowercase(), url.port)
        }
    }
}

/** 目标规则在客户端创建时固定；动态地址在每个逻辑请求开始时冻结。 */
sealed class TargetPolicy {
    abstract val allowExplicitPort: Boolean
    abstract val allowQuery: Boolean

    class FixedOrigins(
        origins: Set<Origin>,
        override val allowExplicitPort: Boolean = false,
        override val allowQuery: Boolean = false,
    ) : TargetPolicy() {
        val origins: Set<Origin> = origins.toSet()
        init { if (origins.isEmpty()) throw PolicyConfigurationException("At least one origin is required") }
    }

    class ResolvedBaseOrigin(
        schemes: Set<String>,
        val ports: IntRange,
        override val allowExplicitPort: Boolean,
        override val allowQuery: Boolean = false,
    ) : TargetPolicy() {
        val schemes: Set<String> = schemes.map { it.lowercase() }.toSet()
        init {
            if (this.schemes.isEmpty() || this.schemes.any { it !in setOf("http", "https") } ||
                ports.isEmpty() || ports.first < 1 || ports.last > 65535) {
                throw PolicyConfigurationException("Invalid resolved origin constraints")
            }
        }
    }

    fun validate(url: Url, resolvedOrigin: Origin? = null): Origin {
        val origin = Origin.from(url)
        if (url.user != null || url.password != null || url.fragment.isNotEmpty() ||
            (!allowExplicitPort && url.specifiedPort != 0) ||
            (!allowQuery && (url.parameters.names().isNotEmpty() || url.trailingQuery))) throw TargetRejectedException()
        when (this) {
            is FixedOrigins -> if (origin !in origins) throw TargetRejectedException()
            is ResolvedBaseOrigin -> if (origin.scheme !in schemes || origin.port !in ports ||
                (resolvedOrigin != null && origin != resolvedOrigin)) throw TargetRejectedException()
        }
        return origin
    }

    fun validateRawLocation(location: String) {
        if (location.isBlank() || location.any { it <= ' ' || it == '\\' || it == '\u007f' } || '#' in location) {
            throw TargetRejectedException()
        }
        val authority = Regex("^(?:[A-Za-z][A-Za-z0-9+.-]*:)?//([^/?#]*)").find(location)?.groupValues?.get(1)
        if (authority != null) {
            if ('@' in authority) throw TargetRejectedException()
            val hasPort = if (authority.startsWith('[')) authority.substringAfter(']', "").isNotEmpty() else ':' in authority
            if (!allowExplicitPort && hasPort) throw TargetRejectedException()
        }
        var index = location.indexOf('%')
        while (index >= 0) {
            if (index + 2 >= location.length || location.substring(index + 1, index + 3).any { it.digitToIntOrNull(16) == null }) {
                throw TargetRejectedException()
            }
            index = location.indexOf('%', index + 3)
        }
    }
}
