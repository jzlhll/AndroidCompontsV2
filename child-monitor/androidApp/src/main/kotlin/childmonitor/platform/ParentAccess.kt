package childmonitor.platform

import childmonitor.TAG
import com.au.module_android.log.logdNoFile
import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.Path.Companion.toPath

data class ParentAccessState(val ready: Boolean = false, val enabled: Boolean = false, val unlocked: Boolean = false, val failed: Boolean = false)

/** 家长操作锁只保存加盐摘要；离开应用撤销本轮前台授权，旧验证结果不能重新解锁。 */
class ParentAccess(context: Context, private val scope: CoroutineScope) {
    private val store = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = {
        File(context.filesDir, "parent-access.preferences_pb").absolutePath.toPath()
    })
    private val saltKey = stringPreferencesKey("salt")
    private val hashKey = stringPreferencesKey("hash")
    private val attemptsKey = intPreferencesKey("attempts")
    private val blockedKey = longPreferencesKey("blocked_until")
    private val mutex = Mutex()
    private val mutableStateFlow = MutableStateFlow(ParentAccessState())
    val stateFlow = mutableStateFlow.asStateFlow()
    private val authorizationLock = Any()
    private var generation = 0L
    init { reload() }
    fun isAuthorized() = stateFlow.value.let { it.ready && (!it.enabled || it.unlocked) }
    fun reload() { scope.launch {
        try {
            val values = store.data.first()
            mutableStateFlow.value = ParentAccessState(ready = true, enabled = values[hashKey] != null)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableStateFlow.value = ParentAccessState(failed = true) }
    } }
    fun lock() = synchronized(authorizationLock) {
        generation++
        logdNoFile(tag = TAG) { "parent access locked generation=$generation" }
        mutableStateFlow.value = stateFlow.value.copy(unlocked = false)
    }
    fun unlockWithDeviceCredential() = synchronized(authorizationLock) {
        if (stateFlow.value.ready) {
            mutableStateFlow.value = stateFlow.value.copy(unlocked = true, failed = false)
            logdNoFile(tag = TAG) { "parent device credential authorized generation=$generation" }
        }
    }
    suspend fun verify(pin: String): Boolean {
        val token = synchronized(authorizationLock) { generation }
        return mutex.withLock {
            if (!pin.matches(Regex("[0-9]{6}"))) return@withLock false
            val values = store.data.first()
            val now = System.currentTimeMillis()
            val blockedUntil = values[blockedKey] ?: 0
            // 系统时间回拨时最多继续锁定一分钟，避免永久锁死。
            if (now < blockedUntil && blockedUntil - now <= 60_000) {
                logdNoFile(tag = TAG) { "parent verify blocked generation=$token remainingMs=${blockedUntil - now}" }
                return@withLock false
            }
            val expected = values[hashKey] ?: return@withLock false
            val salt = checkNotNull(values[saltKey])
            val actual = withContext(Dispatchers.Default) { digest(pin, Base64.decode(salt, Base64.NO_WRAP)) }
            val correct = MessageDigest.isEqual(Base64.decode(expected, Base64.NO_WRAP), actual)
            store.edit { record ->
                val attempts = if (correct) 0 else (record[attemptsKey] ?: 0) + 1
                record[attemptsKey] = if (attempts >= 5) 0 else attempts
                record[blockedKey] = if (attempts >= 5) now + 60_000 else 0
            }
            synchronized(authorizationLock) {
                logdNoFile(tag = TAG) { "parent verify result generation=$generation requestGeneration=$token correct=$correct cooldownStarted=${!correct && (values[attemptsKey] ?: 0) + 1 >= 5} authorized=${correct && token == generation}" }
                if (!correct || token != generation) false
                else {
                    mutableStateFlow.value = stateFlow.value.copy(unlocked = true, failed = false)
                    true
                }
            }
        }
    }
    suspend fun configure(pin: String?) = mutex.withLock {
        val token = synchronized(authorizationLock) { check(isAuthorized()); generation }
        require(pin == null || pin.matches(Regex("[0-9]{6}")))
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = if (pin == null) null else withContext(Dispatchers.Default) { digest(pin, salt) }
        // 持久化提交后必须同步内存状态；页面退出只能撤销授权，不能留下过期的 PIN 配置。
        withContext(NonCancellable) {
            store.edit {
                it.clear()
                if (hash != null) {
                    it[saltKey] = Base64.encodeToString(salt, Base64.NO_WRAP)
                    it[hashKey] = Base64.encodeToString(hash, Base64.NO_WRAP)
                }
            }
            synchronized(authorizationLock) {
                mutableStateFlow.value = ParentAccessState(ready = true, enabled = pin != null, unlocked = token == generation)
                logdNoFile(tag = TAG) { "parent configure committed generation=$generation enabled=${pin != null} authorized=${token == generation}" }
            }
        }
    }
    private fun digest(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
