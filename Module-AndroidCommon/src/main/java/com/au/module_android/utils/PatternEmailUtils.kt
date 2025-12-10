package com.au.module_android.utils

import android.text.TextUtils
import com.au.module_android.Globals
import java.util.regex.Pattern

/**
 * 正则匹配的规则
 */
class PatternEmailUtils {
    fun match(email: String?): Boolean {
        try {
            if (TextUtils.isEmpty(email)) {
                return false
            } else {
                val splits: Array<String?> =
                    email!!.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                if (splits.size != 2) {
                    return false
                }

                if (splits[0]!!.isEmpty() || splits[0]!!.length > 64 || splits[0]!!.trim { it <= ' ' }
                        .isEmpty()) {
                    return false
                }
                if (splits[1]!!.isEmpty() || splits[1]!!.length > 255 || splits[1]!!.trim { it <= ' ' }
                        .isEmpty()) {
                    return false
                }

                val emailPattern = "^.+@.+\\.[A-Za-z]+$"
                val pattern = Pattern.compile(emailPattern)
                val matcher = pattern.matcher(email)
                return matcher.matches()
            }
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * 检查密码规则
     * @return 0: 合法; 1: 密码长度不足8个字符; 2: 缺少大写字母; 3: 缺少小写字母; 4: 缺少数字; 5: 缺少特殊字符
     */
    fun isValidPassword(password:String) : Int {
        ignoreError {
            // 最少8个字符
            if (password.length < 8) return 1

            // 至少包含一个大写字母
            val uppercaseRegex = ".*[A-Z]+.*".toRegex()
            if (!uppercaseRegex.matches(password)) return 2

            // 至少包含一个小写字母
            val lowercaseRegex = ".*[a-z]+.*".toRegex()
            if (!lowercaseRegex.matches(password)) return 3

            // 至少包含一个数字
            val numberRegex = ".*[0-9]+.*".toRegex()
            if (!numberRegex.matches(password)) return 4

            // 至少包含一个特殊字符
            val specialCharRegex = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]+.*".toRegex()
            if (!specialCharRegex.matches(password)) return 5
        }

        return 0
    }
}
