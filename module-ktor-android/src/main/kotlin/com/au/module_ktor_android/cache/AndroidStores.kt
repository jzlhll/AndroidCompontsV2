package com.au.module_ktor_android.cache

import com.au.module_ktor_base.cache.ApiCacheStore
import com.au.module_ktor_base.cache.CachedResponse
import com.au.module_ktor_base.cache.NamespacedCookiesStorage
import com.au.module_ktor_base.config.StorageNamespace
import com.au.module_ktor_base.transfer.storageOperation
import io.ktor.http.Cookie
import io.ktor.http.CookieEncoding
import io.ktor.http.Url
import io.ktor.http.renderSetCookieHeader
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie as OkHttpCookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private val storageLocks = ConcurrentHashMap<String, Mutex>()

/** 普通响应的限容持久缓存；清理前由所属会话取消并等待旧请求退出。 */
class AndroidApiCacheStore(
    directory: File,
    override val namespace: StorageNamespace,
    private val maxSizeBytes: Long = 32L * 1024 * 1024,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : ApiCacheStore {
    private val directory = File(namespaceDirectory(directory, namespace), "api")
    private val mutex = storageLocks.getOrPut(this.directory.absolutePath) { Mutex() }

    init { require(maxSizeBytes > 16) }

    override suspend fun read(key: String): CachedResponse? = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation {
            val file = entry(key)
            if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return@storageOperation null
            if (file.length() > maxSizeBytes) { Files.delete(file.toPath()); return@storageOperation null }
            DataInputStream(file.inputStream().buffered()).use { input ->
                val expiry = input.readLong()
                val size = input.readInt()
                if (expiry <= clockMillis() || size < 0 || size.toLong() + 12 != file.length()) {
                    Files.delete(file.toPath())
                    null
                } else {
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    file.setLastModified(clockMillis())
                    CachedResponse(bytes, expiry)
                }
            }
        } }
    }

    override suspend fun write(key: String, value: CachedResponse) = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation {
            require(value.bytes.size.toLong() + 12 <= maxSizeBytes) { "Cache entry exceeds storage capacity" }
            if (value.expiresAtMillis <= clockMillis()) return@storageOperation
            atomicWrite(entry(key).toPath()) { target ->
                DataOutputStream(Files.newOutputStream(target).buffered()).use { output ->
                    output.writeLong(value.expiresAtMillis)
                    output.writeInt(value.bytes.size)
                    output.write(value.bytes)
                }
            }
            val files = directory.listFiles { file -> file.name.matches(Regex("[a-f0-9]{64}\\.entry")) }.orEmpty()
            var size = files.sumOf { it.length() }
            files.sortedBy { it.lastModified() }.forEach { file ->
                if (size > maxSizeBytes) {
                    val length = file.length()
                    Files.deleteIfExists(file.toPath())
                    size -= length
                }
            }
        } }
    }

    override suspend fun remove(key: String) = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation { Files.deleteIfExists(entry(key).toPath()); Unit } }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation {
            directory.listFiles { file -> file.name.matches(Regex("[a-f0-9]{64}\\.entry")) }.orEmpty().forEach { Files.deleteIfExists(it.toPath()) }
        } }
    }

    private fun entry(key: String): File = File(directory, "${storageDigest(key)}.entry")
}

/** 使用 OkHttp 的 Cookie 域名及路径校验；会话 Cookie 仅留在当前实例。 */
class AndroidCookiesStorage(
    directory: File,
    override val namespace: StorageNamespace,
    private val maxCookies: Int = 300,
    private val maxSizeBytes: Int = 256 * 1024,
    private val clockMillis: () -> Long = System::currentTimeMillis,
) : NamespacedCookiesStorage {
    private val file = File(namespaceDirectory(directory, namespace), "cookies.json")
    private val mutex = storageLocks.getOrPut(file.absolutePath) { Mutex() }
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredCookie.serializer())
    private val sessionCookies = mutableListOf<StoredCookie>()
    private val closed = AtomicBoolean(false)

    init { require(maxCookies > 0 && maxSizeBytes >= 2) }

    override suspend fun get(requestUrl: Url): List<Cookie> = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation {
            check(!closed.get()) { "Cookie storage is closed" }
            val url = requestUrl.toString().toHttpUrl()
            (load() + sessionCookies).filter { it.expiresAt > clockMillis() && it.toOkHttp().matches(url) }
                .sortedByDescending { it.path.length }.map { it.toKtor() }
        } }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation {
            check(!closed.get()) { "Cookie storage is closed" }
            val parsed = OkHttpCookie.parse(requestUrl.toString().toHttpUrl(), renderSetCookieHeader(cookie)) ?: return@storageOperation
            val record = StoredCookie.from(parsed)
            val persistent = load().toMutableList()
            persistent.removeAll { it.sameKey(record) || it.expiresAt <= clockMillis() }
            sessionCookies.removeAll { it.sameKey(record) || it.expiresAt <= clockMillis() }
            if (record.expiresAt > clockMillis()) {
                if (parsed.persistent) persistent += record else sessionCookies += record
            }
            while (persistent.size + sessionCookies.size > maxCookies) {
                if (persistent.isNotEmpty()) persistent.removeAt(0) else sessionCookies.removeAt(0)
            }
            while (json.encodeToString(serializer, persistent + sessionCookies).encodeToByteArray().size > maxSizeBytes &&
                (persistent.isNotEmpty() || sessionCookies.isNotEmpty())) {
                if (persistent.isNotEmpty()) persistent.removeAt(0) else sessionCookies.removeAt(0)
            }
            val encoded = json.encodeToString(serializer, persistent).encodeToByteArray()
            atomicWrite(file.toPath()) { Files.write(it, encoded) }
        } }
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        mutex.withLock { storageOperation { sessionCookies.clear(); Files.deleteIfExists(file.toPath()); Unit } }
    }

    override fun close() { closed.set(true) }

    private fun load(): List<StoredCookie> {
        if (!Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS)) return emptyList()
        check(file.length() <= maxSizeBytes) { "Cookie storage exceeds capacity" }
        return json.decodeFromString(serializer, file.readText()).filter { it.expiresAt > clockMillis() }.takeLast(maxCookies)
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val domain: String,
    val path: String,
    val hostOnly: Boolean,
    val secure: Boolean,
    val httpOnly: Boolean,
    val expiresAt: Long,
) {
    fun sameKey(other: StoredCookie): Boolean = name == other.name && domain == other.domain && path == other.path

    fun toOkHttp(): OkHttpCookie = OkHttpCookie.Builder().apply {
        name(name)
        value(value)
        if (hostOnly) hostOnlyDomain(domain) else domain(domain)
        path(path)
        expiresAt(expiresAt)
        if (secure) secure()
        if (httpOnly) httpOnly()
    }.build()

    fun toKtor(): Cookie = Cookie(name, value, CookieEncoding.RAW, expires = GMTDate(expiresAt), domain = domain, path = path, secure = secure, httpOnly = httpOnly)

    companion object {
        fun from(cookie: OkHttpCookie): StoredCookie = StoredCookie(
            cookie.name, cookie.value, cookie.domain, cookie.path, cookie.hostOnly, cookie.secure, cookie.httpOnly, cookie.expiresAt,
        )
    }
}

fun namespaceDirectory(directory: File, namespace: StorageNamespace): File =
    File(directory, storageDigest("${namespace.serviceId.length}:${namespace.serviceId}${namespace.principalId.length}:${namespace.principalId}"))

private fun storageDigest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }

private fun atomicWrite(target: Path, write: (Path) -> Unit) {
    Files.createDirectories(target.parent)
    val temporary = Files.createTempFile(target.parent, ".ktor-store-", ".tmp")
    var failure: Throwable? = null
    try {
        write(temporary)
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (error: Throwable) {
        failure = error
        throw error
    } finally {
        try { Files.deleteIfExists(temporary) } catch (cleanup: Throwable) {
            if (failure == null) throw cleanup else failure.addSuppressed(cleanup)
        }
    }
}
