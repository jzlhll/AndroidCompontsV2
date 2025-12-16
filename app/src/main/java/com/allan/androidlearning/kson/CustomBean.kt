package com.allan.androidlearning.kson

import android.graphics.Color
import android.net.Uri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.util.Date

@Serializable
data class CustomBean(
    @Serializable(with = ColorSerializer::class)
    val color: Color,
    @Serializable(with = UriSerializer::class)
    val uuid: Uri,
    @Serializable(with = DateSerializer::class)
    val date : Date,
)

object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Color") {
        element<Int>("argb")
    }

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.toArgb())
        }
    }

    override fun deserialize(decoder: Decoder): Color {
        return decoder.decodeStructure(descriptor) {
            var argb = 0
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> argb = decodeIntElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Color.valueOf(argb)
        }
    }
}

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Uri") {
        element<String>("uri")
    }

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Uri {
        return decoder.decodeStructure(descriptor) {
            var uri = ""
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> uri = decodeStringElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Uri.parse(uri)
        }
    }
}

object DateSerializer : KSerializer<Date> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Date") {
        element<Long>("epochMillis")
    }

    override fun serialize(encoder: Encoder, value: Date) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.time)
        }
    }

    override fun deserialize(decoder: Decoder): Date {
        return decoder.decodeStructure(descriptor) {
            var epoch = 0L
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    0 -> epoch = decodeLongElement(descriptor, 0)
                    CompositeDecoder.DECODE_DONE -> break
                    else -> error("Unexpected index: $index")
                }
            }
            Date(epoch)
        }
    }
}
