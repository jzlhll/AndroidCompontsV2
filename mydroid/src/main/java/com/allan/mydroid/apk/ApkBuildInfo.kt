package com.allan.mydroid.apk

import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class ApkBuildInfo(
    val applicationId: String,
    val versionName: String?,
    val versionCode: Long,
    val variantName: String?,
    val sha256: String,
) {
    companion object {
        private val sha256Pattern = Regex("[a-fA-F0-9]{64}")

        fun fromJson(json: JSONObject): ApkBuildInfo {
            val applicationId = json.opt("applicationId") as? String
            val versionName = json.opt("versionName") as? String
            val rawVersionCode = json.opt("versionCode") as? Number
            val versionCode = rawVersionCode?.takeIf { it is Int || it is Long }?.toLong()
            val variantName = json.opt("variantName") as? String
            val sha256 = json.opt("sha256") as? String
            require(!applicationId.isNullOrBlank() && versionName != null && versionCode != null &&
                !variantName.isNullOrBlank() && sha256 != null && sha256.matches(sha256Pattern)
            ) { "Invalid APK build information" }
            return ApkBuildInfo(applicationId, versionName, versionCode, variantName, sha256.lowercase())
        }
    }
}

data class ReceivedApk(val file: File, val buildInfo: ApkBuildInfo)

fun File.readApkSha256(checkActive: () -> Unit = {}): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            checkActive()
            val count = input.read(buffer)
            if (count == -1) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
