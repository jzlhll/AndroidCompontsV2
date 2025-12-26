package com.au.module_android.json

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.*

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
            uri.toUri()
        }
    }
}

