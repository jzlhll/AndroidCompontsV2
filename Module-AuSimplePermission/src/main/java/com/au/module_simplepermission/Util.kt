package com.au.module_simplepermission

import android.content.Context

inline fun <T:Any> ignoreError(
    block: () -> T?
): T? {
    return try {
        block.invoke()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

// 1,存储boolean变量方法
fun spPutBoolean(ctx: Context, key: String, value: Boolean) {
    // name存储文件名称
    ctx.getSharedPreferences("camera_permission", Context.MODE_PRIVATE).edit().putBoolean(key, value).apply()
}

// 2,读取boolean变量方法
fun spGetBoolean(ctx: Context, key: String, defValue: Boolean): Boolean {
    // name存储文件名称
    return ctx.getSharedPreferences("camera_permission", Context.MODE_PRIVATE).getBoolean(key, defValue)
}