package com.au.module_android.utils

import android.content.Context
import android.os.Environment
import androidx.annotation.WorkerThread
import com.au.module_android.Globals.app
import java.io.File

/**
 * 获取缓存大小
 */
suspend fun getAppCacheSize(): String {
    return withIOThread {
        val cacheDirSz = app.cacheDir.getDirSize()
        val isHasSd = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        val externalCacheDirSz = if(isHasSd) app.externalCacheDir.getDirSize() else 0
        getSizeFormat(cacheDirSz + externalCacheDirSz)
    }
}

/**
 * 清除综合的缓存，不包含数据库
 *
 */
@WorkerThread
suspend fun clearAppCache() {
    withIOThread {
        ignoreError {
            app.cacheDir?.deleteAll()
        }
        ignoreError {
            if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED)
                app.externalCacheDir?.deleteAll()
        }
    }
}

/**
 * * 清除本应用所有数据库(/data/data/com.xxx.xxx/databases) * *
 *
 * @param context
 */
fun cleanDatabases(context: Context) {
    File(
        "/data/data/"
                + context.packageName + "/databases"
    ).deleteAll()
}

/**
 * * 清除本应用SharedPreference(/data/data/com.xxx.xxx/shared_prefs) *
 *
 * @param context
 */
fun cleanSharedPreference(context: Context) {
    File(
        "/data/data/"
                + context.packageName + "/shared_prefs"
    ).deleteAll()
}

/**
 * * 按名字清除本应用数据库 * *
 *
 * @param context
 * @param dbName
 */
fun cleanDatabaseByName(context: Context, dbName: String?) {
    context.deleteDatabase(dbName)
}