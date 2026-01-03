package com.allan.androidlearning.kson

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import okhttp3.Cookie

@Serializable
class CookieStoreBean {
    @Serializable(with = CookieListSerializer::class)
    var cookies: List<Cookie>? = null

    @Serializable(with = CookieMapSerializer::class)
    var cookieMap: Map<String, Cookie>? = null

    var url: String? = null
}

object CookieSerializer : KSerializer<Cookie> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Cookie") {
        element<String>("name")
        element<String>("value")
        element<Long>("expiresAt")
        element<String>("domain")
        element<String>("path")
        element<Boolean>("secure")
        element<Boolean>("httpOnly")
        element<Boolean>("persistent")
        element<Boolean>("hostOnly")
    }

    override fun serialize(encoder: Encoder, value: Cookie) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.name())
            encodeStringElement(descriptor, 1, value.value())
            encodeLongElement(descriptor, 2, value.expiresAt())
            encodeStringElement(descriptor, 3, value.domain())
            encodeStringElement(descriptor, 4, value.path())
            encodeBooleanElement(descriptor, 5, value.secure())
            encodeBooleanElement(descriptor, 6, value.httpOnly())
            encodeBooleanElement(descriptor, 7, value.persistent())
            encodeBooleanElement(descriptor, 8, value.hostOnly())
        }
    }

    override fun deserialize(decoder: Decoder): Cookie {
        return decoder.decodeStructure(descriptor) {
            var name: String=""
            var value: String=""
            var expiresAt: Long=0
            var domain: String=""
            var path: String=""
            var secure = false
            var httpOnly = false
            var persistent = false
            var hostOnly = false

            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> name = decodeStringElement(descriptor, 0)
                    1 -> value = decodeStringElement(descriptor, 1)
                    2 -> expiresAt = decodeLongElement(descriptor, 2)
                    3 -> domain = decodeStringElement(descriptor, 3)
                    4 -> path = decodeStringElement(descriptor, 4)
                    5 -> secure = decodeBooleanElement(descriptor, 5)
                    6 -> httpOnly = decodeBooleanElement(descriptor, 6)
                    7 -> persistent = decodeBooleanElement(descriptor, 7)
                    8 -> hostOnly = decodeBooleanElement(descriptor, 8)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .expiresAt(expiresAt)
                .path(path)

            if (hostOnly) {
                builder.hostOnlyDomain(domain)
            } else {
                builder.domain(domain)
            }

            if (secure) builder.secure()
            if (httpOnly) builder.httpOnly()

            builder.build()
        }
    }
}

object CookieListSerializer : KSerializer<List<Cookie>> {
    private val delegate = ListSerializer(CookieSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<Cookie>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): List<Cookie> {
        return delegate.deserialize(decoder)
    }
}

object CookieMapSerializer : KSerializer<Map<String, Cookie>> {
    private val delegate = MapSerializer(String.serializer(), CookieSerializer)

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<String, Cookie>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): Map<String, Cookie> {
        return delegate.deserialize(decoder)
    }
}