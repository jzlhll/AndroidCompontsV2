package com.allan.mydroid.apk

import android.content.Context
import android.content.pm.PackageManager
import com.au.module_android.log.logEx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** 查询已安装的 base APK，并缓存同一次安装的摘要。 */
class ApkInstallVerifier(private val context: Context) {
    data class Target(val applicationId: String, val sha256: String)

    enum class Status {
        PackageMissing, DifferentBuild, Unreadable, Verified,
    }

    private data class InstalledApk(val file: File, val lastUpdateTime: Long)

    private data class CachedDigest(val apk: InstalledApk, val sha256: String)

    private var cachedDigest: CachedDigest? = null
    private var readErrorLogged = false

    suspend fun check(target: Target, refreshDigest: Boolean = false): Status = withContext(Dispatchers.IO) {
        if (refreshDigest) cachedDigest = null
        try {
            val apk = installedApk(target.applicationId)
            if (apk == null) {
                cachedDigest = null
                readErrorLogged = false
                return@withContext Status.PackageMissing
            }
            // 只在安装路径或更新时间变化时重算摘要。
            val sha256 = cachedDigest?.takeIf { it.apk == apk }?.sha256 ?: run {
                val actual = apk.file.readApkSha256 { ensureActive() }
                if (installedApk(target.applicationId) != apk) return@withContext Status.Unreadable
                cachedDigest = CachedDigest(apk, actual)
                actual
            }
            readErrorLogged = false
            if (sha256 == target.sha256) Status.Verified else Status.DifferentBuild
        } catch (e: Exception) {
            if (e !is IOException && e !is SecurityException) throw e
            if (!readErrorLogged) logEx(throwable = e) { "Read installed APK failed for ${target.applicationId}" }
            readErrorLogged = true
            Status.Unreadable
        }
    }

    @Suppress("DEPRECATION")
    private fun installedApk(applicationId: String): InstalledApk? {
        val info = try {
            context.packageManager.getPackageInfo(applicationId, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        val source = info.applicationInfo?.sourceDir ?: throw IOException("Missing installed APK path")
        return InstalledApk(File(source), info.lastUpdateTime)
    }
}
