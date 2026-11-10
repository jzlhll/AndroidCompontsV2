package com.au.agit.git

import com.au.agit.R
import com.au.agit.appString
import com.au.agit.model.AGitException
import com.au.agit.model.GitProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** 下载 GitHub 默认分支的源码归档，校验后解压为不含 Git 元数据的普通文件目录。 */
class GitHubArchiveDownloader {
    suspend fun download(project: GitProject, token: CharArray, job: Job, directory: File, onReceivedBytes: (Long) -> Unit) = coroutineScope {
        val connection = AtomicReference<HttpURLConnection?>()
        val download = async(Dispatchers.IO) {
            val parent = directory.toPath().parent ?: throw AGitException(appString(R.string.invalid_archive), "INVALID_ARCHIVE")
            Files.createDirectories(parent)
            val archive = parent.resolve("archive.zip").toFile()
            val repositoryPath = URI(project.url).path.removeSuffix(".git")
            var uri = URI("https://api.github.com/repos$repositoryPath/zipball")
            var redirects = 0
            while (true) {
                job.ensureActive()
                if (uri.scheme != "https" || uri.host !in setOf("api.github.com", "codeload.github.com") ||
                    uri.rawUserInfo != null || uri.port != -1 || uri.fragment != null) {
                    throw AGitException(appString(R.string.archive_download_failed), "ARCHIVE_DOWNLOAD_FAILED")
                }
                val current = uri.toURL().openConnection() as HttpURLConnection
                connection.set(current)
                try {
                    current.instanceFollowRedirects = false
                    current.connectTimeout = 30_000
                    current.readTimeout = 180_000
                    current.setRequestProperty("User-Agent", "AGit")
                    if (uri.host == "api.github.com") {
                        current.setRequestProperty("Authorization", "Bearer ${token.concatToString()}")
                        current.setRequestProperty("Accept", "application/vnd.github+json")
                        current.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
                    }
                    job.ensureActive()
                    val status = current.responseCode
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        val location = current.getHeaderField("Location")
                        if (location.isNullOrBlank() || redirects >= 5) {
                            throw AGitException(appString(R.string.archive_download_failed), "ARCHIVE_DOWNLOAD_FAILED")
                        }
                        uri = uri.resolve(location)
                        redirects += 1
                        continue
                    }
                    when (status) {
                        200 -> Unit
                        401 -> throw AGitException(appString(R.string.auth_failed), "AUTHENTICATION")
                        403 -> throw AGitException(appString(R.string.permission_failed), "PERMISSION")
                        404 -> throw AGitException(appString(R.string.repo_not_found), "NOT_FOUND")
                        else -> throw AGitException(appString(R.string.archive_download_failed), "ARCHIVE_DOWNLOAD_FAILED")
                    }
                    current.inputStream.use { input ->
                        archive.outputStream().use { output ->
                            val buffer = ByteArray(65536)
                            var received = 0L
                            while (true) {
                                job.ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                received += count
                                onReceivedBytes(received)
                            }
                        }
                    }
                    break
                } finally {
                    connection.compareAndSet(current, null)
                    current.disconnect()
                }
            }
            job.ensureActive()
            try {
                extract(archive, directory, job)
            } catch (error: ZipException) {
                throw AGitException(appString(R.string.invalid_archive), "INVALID_ARCHIVE", error)
            }
        }
        try {
            download.await()
        } finally {
            // 父任务取消时先关闭连接，再由结构化并发等待下载线程退出，随后才允许清理目录。
            connection.getAndSet(null)?.disconnect()
        }
    }

    private fun extract(archive: File, directory: File, job: Job) {
        val root = directory.toPath()
        Files.createDirectory(root)
        ZipFile(archive).use { zip ->
            var archiveRoot: String? = null
            val entries = zip.entries()
            if (!entries.hasMoreElements()) throw ZipException("Empty source archive")
            val buffer = ByteArray(65536)
            while (entries.hasMoreElements()) {
                job.ensureActive()
                val entry = entries.nextElement()
                val parts = entry.name.trimEnd('/').split('/')
                if (entry.name.startsWith('/') || '\\' in entry.name || '\u0000' in entry.name ||
                    parts.any { it.isEmpty() || it == "." || it == ".." || it.equals(".git", ignoreCase = true) }) {
                    throw AGitException(appString(R.string.invalid_archive), "INVALID_ARCHIVE")
                }
                if (archiveRoot == null) archiveRoot = parts.first()
                if (parts.first() != archiveRoot || (parts.size == 1 && !entry.isDirectory)) {
                    throw AGitException(appString(R.string.invalid_archive), "INVALID_ARCHIVE")
                }
                if (parts.size == 1) continue
                val target = root.resolve(parts.drop(1).joinToString("/")).normalize()
                if (!target.startsWith(root)) throw AGitException(appString(R.string.invalid_archive), "INVALID_ARCHIVE")
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                    continue
                }
                Files.createDirectories(target.parent)
                val checksum = CRC32()
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                        while (true) {
                            job.ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            checksum.update(buffer, 0, count)
                        }
                    }
                }
                if (entry.crc != checksum.value) throw ZipException("Source archive checksum mismatch")
            }
        }
    }
}
