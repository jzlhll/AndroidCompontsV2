package com.au.module_android.utils

import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class Md5Util {
    companion object {
        /**
         * 对字符串进行MD5加密
         *
         * @return 计算出的MD5哈希值的十六进制字符串表示，如果计算失败则返回空字符串
         */
        fun md5(str:String): String {
            try {
                val instance: MessageDigest = MessageDigest.getInstance("MD5")
                val digest: ByteArray = instance.digest(str.toByteArray())
                val sb = StringBuilder()
                for (b in digest) {
                    val hexString = Integer.toHexString(b.toInt() and 0xff)
                    if (hexString.length < 2) {
                        sb.append("0")
                    }
                    sb.append(hexString)
                }
                return sb.toString()
            } catch (e: NoSuchAlgorithmException) {
                //do nothing.
                return ""
            }
        }



        fun getFileMD5(filePath: String): String {
            return try {
                FileInputStream(filePath).use { fis ->
                    val messageDigest = MessageDigest.getInstance("MD5")
                    val buffer = ByteArray(4096) // 4KB 缓冲区
                    var bytesRead: Int
                    while (fis.read(buffer).also { bytesRead = it } != -1) {
                        messageDigest.update(buffer, 0, bytesRead)
                    }
                    bytesToHex(messageDigest.digest())
                }
            } catch (e: Exception) {
                when (e) {
                    is NoSuchAlgorithmException,
                    is IOException -> e.printStackTrace()
                }
                ""
            }
        }

        // 更简洁的 bytesToHex 实现（Kotlin 风格）
        private fun bytesToHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}