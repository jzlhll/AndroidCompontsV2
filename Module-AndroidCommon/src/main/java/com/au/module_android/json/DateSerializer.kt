package com.au.module_android.json

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*
import java.util.Date

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