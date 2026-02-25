package com.au.module_okhttp.creator

import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.withIOThread
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.*
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 下载文件
 */
suspend fun OkHttpClient.downloadFile(
    url: String,
    dirPath: String,
    fileName: String,
    byteArraySize: Int = 1024,
    deleteFileIfNoSuccess: Boolean = true,
    useTempFile: Boolean = false,
): File? {
    return downloadFile(
        Request.Builder()
            .url(url)
            .build(),
        dirPath,
        fileName,
        byteArraySize,
        deleteFileIfNoSuccess,
        useTempFile,
    )
}

/**
 * 下载文件
 */
suspend fun OkHttpClient.downloadFile(
    url: String,
    dirPath: String,
    fileName: String,
    byteArraySize: Int = 1024,
    deleteFileIfNoSuccess: Boolean = true,
    useTempFile: Boolean = false,
    progressListener: (
        downloadLen: Long,
        totalLen: Long,
        progress: Float
    ) -> Unit = { _, _, _ -> },
): File? {
    return downloadFile(
        Request.Builder()
            .url(url)
            .build(),
        dirPath,
        fileName,
        byteArraySize,
        deleteFileIfNoSuccess,
        useTempFile,
        progressListener
    )
}

/**
 * OkHttpClient 扩展函数：协程版文件下载
 * @param request 下载请求（包含URL等信息）
 * @param dirPath 文件保存目录路径
 * @param fileName 保存的文件名
 * @param byteArraySize 读写缓冲区大小（默认1024字节）
 * @param deleteFileIfNoSuccess 下载失败时是否删除文件（默认true）
 * @param useTempFile 是否使用临时文件下载（下载中为.tmp后缀，成功后改名，默认false）
 * @return 成功返回下载后的File，失败返回null
 */
suspend fun OkHttpClient.downloadFile(
    request: Request,
    dirPath: String,
    fileName: String,
    byteArraySize: Int = 8192,
    deleteFileIfNoSuccess: Boolean = true,
    useTempFile: Boolean = false,
): File? = withIOThread {
    //1. 检查目录是否存在
    val dir = File(dirPath)
    if (!dir.exists() && !dir.mkdirs()) {
        throw IllegalArgumentException("无法创建下载目录: $dirPath")
    }

    // 2. 处理临时文件/最终文件路径
    val finalFile = File(dir, fileName)
    val targetFile = if (useTempFile) {
        File(dir, "$fileName.tmp") // 临时文件后缀.tmp
    } else {
        finalFile
    }

    // 3. 协程可取消封装：将OkHttp异步回调转为挂起函数
    return@withIOThread suspendCancellableCoroutine { continuation ->
        // 发起OkHttp异步请求
        val call = newCall(request)

        // 协程取消时，中断OkHttp请求并清理文件
        continuation.invokeOnCancellation {
            if (!call.isCanceled()) {
                call.cancel()
            }
            if (deleteFileIfNoSuccess && targetFile.exists()) {
                targetFile.delete()
            }
        }

        // 执行异步下载
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // 下载失败：清理文件 + 恢复协程异常
                handleDownloadFailure(
                    targetFile = targetFile,
                    deleteFileIfNoSuccess = deleteFileIfNoSuccess,
                    exception = e,
                    continuation = continuation
                )
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                var inputStream: InputStream? = null
                var outputStream: FileOutputStream? = null
                try {
                    // 4. 响应校验
                    val responseBody = response.body
                    if (!response.isSuccessful) {
                        throw java.io.IOException("下载失败，响应码: ${response.code}")
                    }

                    // 6. 读取流并写入文件
                    inputStream = responseBody.byteStream()
                    outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(byteArraySize)
                    var readLen: Int
                    while (inputStream.read(buffer).also { readLen = it } != -1) {
                        // 检查协程是否已取消，若取消则终止下载
                        if (continuation.isCancelled) {
                            throw java.io.IOException("下载已被取消")
                        }
                        outputStream.write(buffer, 0, readLen)
                    }
                    outputStream.flush()

                    // 8. 下载成功：处理临时文件重命名
                    val resultFile = if (useTempFile) {
                        // 删除已存在的最终文件（避免重命名失败）
                        if (finalFile.exists()) finalFile.delete()
                        // 临时文件重命名为最终文件（原子操作）
                        if (targetFile.renameTo(finalFile)) {
                            finalFile
                        } else {
                            throw java.io.IOException("rename failed: ${targetFile.name} -> ${finalFile.name}")
                        }
                    } else {
                        targetFile
                    }

                    // 恢复协程，返回结果文件
                    continuation.resume(resultFile)
                } catch (e: Exception) {
                    // 下载过程中异常：清理文件 + 恢复协程异常
                    handleDownloadFailure(
                        targetFile = targetFile,
                        deleteFileIfNoSuccess = deleteFileIfNoSuccess,
                        exception = e,
                        continuation = continuation
                    )
                } finally {
                    // 9. 确保流资源释放
                    inputStream?.close()
                    outputStream?.close()
                    response.close()
                }
            }
        })
    }
}

/**
 * OkHttpClient 扩展函数：协程版文件下载
 * @param request 下载请求（包含URL等信息）
 * @param dirPath 文件保存目录路径
 * @param fileName 保存的文件名
 * @param byteArraySize 读写缓冲区大小（默认1024字节）
 * @param deleteFileIfNoSuccess 下载失败时是否删除文件（默认true）
 * @param useTempFile 是否使用临时文件下载（下载中为.tmp后缀，成功后改名，默认false）
 * @param progressListener 下载进度监听（已下载长度、总长度、进度[0-1]）
 * @return 成功返回下载后的File，失败返回null
 */
suspend fun OkHttpClient.downloadFile(
    request: Request,
    dirPath: String,
    fileName: String,
    byteArraySize: Int = 8192,
    deleteFileIfNoSuccess: Boolean = true,
    useTempFile: Boolean = false,
    progressListener: (downloadLen: Long, totalLen: Long, progress: Float) -> Unit = { _, _, _ -> }
): File? = withIOThread {
    //1. 检查目录是否存在
    val dir = File(dirPath)
    if (!dir.exists()) {
        try {
            // 使用NIO的线程安全方法创建目录（多级目录）
            val path = Paths.get(dirPath)
            Files.createDirectories(path) // 核心：原子性处理，目录已存在时静默返回
        } catch (e: Exception) {
            if (!dir.exists()) {
                // 仅在真正创建失败时抛出异常（如权限不足、路径非法等）
                throw IllegalArgumentException("无法创建下载目录: $dirPath，原因：${e.message}", e)
            }
        }
    }

    // 2. 处理临时文件/最终文件路径
    val finalFile = File(dir, fileName)
    val targetFile = if (useTempFile) {
        File(dir, "$fileName.tmp") // 临时文件后缀.tmp
    } else {
        finalFile
    }

    // 3. 协程可取消封装：将OkHttp异步回调转为挂起函数
    return@withIOThread suspendCancellableCoroutine { continuation ->
        // 发起OkHttp异步请求
        val call = newCall(request)

        // 协程取消时，中断OkHttp请求并清理文件
        continuation.invokeOnCancellation {
            if (!call.isCanceled()) {
                call.cancel()
            }
            if (deleteFileIfNoSuccess && targetFile.exists()) {
                targetFile.delete()
            }
        }

        // 执行异步下载
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // 下载失败：清理文件 + 恢复协程异常
                handleDownloadFailure(
                    targetFile = targetFile,
                    deleteFileIfNoSuccess = deleteFileIfNoSuccess,
                    exception = e,
                    continuation = continuation
                )
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                var inputStream: InputStream? = null
                var outputStream: FileOutputStream? = null
                try {
                    // 4. 响应校验
                    val responseBody = response.body
                    if (!response.isSuccessful) {
                        throw java.io.IOException("下载失败，响应码: ${response.code}")
                    }

                    // 5. 获取文件总长度（可能为-1，比如chunked编码）
                    val totalLen = responseBody.contentLength()
                    var downloadLen = 0L

                    // 6. 读取流并写入文件
                    inputStream = responseBody.byteStream()
                    outputStream = FileOutputStream(targetFile)
                    val buffer = ByteArray(byteArraySize)
                    var readLen: Int
                    while (inputStream.read(buffer).also { readLen = it } != -1) {
                        // 检查协程是否已取消，若取消则终止下载
                        if (continuation.isCancelled) {
                            throw java.io.IOException("下载已被取消")
                        }
                        outputStream.write(buffer, 0, readLen)
                        downloadLen += readLen

                        // 7. 计算并回调进度
                        val progress = if (totalLen <= 0) 0f else downloadLen.toFloat() / totalLen
                        progressListener(downloadLen, totalLen, progress.coerceIn(0f, 1f))
                    }
                    outputStream.flush()

                    // 8. 下载成功：处理临时文件重命名
                    val resultFile = if (useTempFile) {
                        // 删除已存在的最终文件（避免重命名失败）
                        if (finalFile.exists()) finalFile.delete()
                        // 临时文件重命名为最终文件（原子操作）
                        if (targetFile.renameTo(finalFile)) {
                            finalFile
                        } else {
                            throw java.io.IOException("重命名临时文件失败: ${targetFile.name} -> ${finalFile.name}")
                        }
                    } else {
                        targetFile
                    }

                    // 恢复协程，返回结果文件
                    continuation.resume(resultFile)
                } catch (e: Exception) {
                    // 下载过程中异常：清理文件 + 恢复协程异常
                    handleDownloadFailure(
                        targetFile = targetFile,
                        deleteFileIfNoSuccess = deleteFileIfNoSuccess,
                        exception = e,
                        continuation = continuation
                    )
                } finally {
                    // 9. 确保流资源释放
                    inputStream?.close()
                    outputStream?.close()
                    response.close()
                }
            }
        })
    }
}

/**
 * 处理下载失败的通用逻辑
 */
private fun handleDownloadFailure(
    targetFile: File,
    deleteFileIfNoSuccess: Boolean,
    exception: Exception,
    continuation: kotlin.coroutines.Continuation<File?>
) {
    // 失败时删除文件
    if (deleteFileIfNoSuccess && targetFile.exists()) {
        targetFile.delete()
    }
    // 恢复协程异常（外部可捕获）
    continuation.resumeWithException(exception)
}

//设置了这个拦截可以直接在响应里面读取，否则会读取完网络数据才有响应
open class ProgressResponseBody(
    private val responseBody: ResponseBody,
    val progressListener: Function2<Long, Long, Unit>
) : ResponseBody() {
    private val bufferedSource: BufferedSource by unsafeLazy { source(responseBody.source()).buffer() }

    override
    fun contentType(): MediaType? {
        return responseBody.contentType()
    }

    override
    fun contentLength(): Long {
        return responseBody.contentLength()
    }

    override
    fun source(): BufferedSource {
        return bufferedSource
    }

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L
            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                progressListener.invoke(totalBytesRead, contentLength())
                return bytesRead
            }
        }
    }
}