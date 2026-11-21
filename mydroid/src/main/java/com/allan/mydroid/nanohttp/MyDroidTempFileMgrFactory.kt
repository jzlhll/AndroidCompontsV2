package com.allan.mydroid.nanohttp

import fi.iki.elonen.NanoHTTPD.DefaultTempFile
import fi.iki.elonen.NanoHTTPD.TempFile
import fi.iki.elonen.NanoHTTPD.TempFileManager
import fi.iki.elonen.NanoHTTPD.TempFileManagerFactory
import java.io.File
import java.io.IOException

/** 为每个 HTTP 请求保留由 NanoHTTPD 管理的临时文件。 */
class MyDroidTempFileMgrFactory(private val directory: File) : TempFileManagerFactory {
    private var closed = false

    override fun create(): TempFileManager = object : TempFileManager {
        private val tempFiles = mutableListOf<TempFile>()

        override fun clear() {
            for (file in tempFiles) {
                try {
                    file.delete()
                } catch (_: Exception) {
                }
            }
            tempFiles.clear()
        }

        override fun createTempFile(filenameHint: String?): TempFile =
            allocateTempFile().also(tempFiles::add)
    }

    @Synchronized
    fun close() {
        closed = true
    }

    @Synchronized
    private fun allocateTempFile(): TempFile {
        check(!closed) { "Upload server is closed" }
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create request temporary directory")
        }
        return DefaultTempFile(directory)
    }
}
