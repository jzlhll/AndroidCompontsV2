package com.allan.mydroid.nanohttp

import com.au.module_android.utils.logd
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BufferManager(uriUuid: String, val chunkSize:Int) {
    init {
        logd { "chunkSize $chunkSize" }
    }

    val uuidBytes = uriUuid.toByteArray(Charsets.UTF_8)

    private val preSize = 32 + 4 + 4 + 8 + 4

    //如果是buildChunkPacket的dataSize刚好是chunkSize，则可以利用cache
    private val buffer = ByteBuffer.allocate(preSize + chunkSize).also {
        it.order(ByteOrder.BIG_ENDIAN)  // 统一使用大端序
    }

    /**
     * 发送给前端的切片协议
     *
     */
    fun buildChunkPacket(
        index: Int,
        totalChunks: Int,
        offset: Long,
        dataSize:Int,
        data: ByteArray,
    ): ByteArray {
        logd { "serverChunk: ${uuidBytes.size} index:$index/$totalChunks, dataSize:$dataSize - $offset" }
        buffer.apply {
            clear()
            put(uuidBytes)               // 32字节
            putInt(index)                // 4字节index
            putInt(totalChunks)                // 4字节total
            putLong(offset)               // 8字节offset
            putInt(dataSize)             // 4字节dataSize
            put(data)                    // 变长数据体
        }
        return buffer.array()
    }
}