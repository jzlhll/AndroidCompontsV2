package com.au.module_kson

import android.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

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