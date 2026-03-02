package com.allan.androidlearning.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

class MyMockProvider : ContentProvider() {

    companion object {
        private const val TAG = "alland"
        const val AUTHORITY = "com.allan.androidlearning.provider.mock"
        const val PATH_COMMON = "common"
        const val CODE_COMMON = 1

        val CONTENT_URI: Uri = "content://$AUTHORITY/$PATH_COMMON".toUri()
    }

    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, PATH_COMMON, CODE_COMMON)
    }

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate: MyMockProvider 已启动")
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        Log.d(TAG, "query 调用参数: uri=$uri, projection=${projection?.contentToString()}, selection=$selection, selectionArgs=${selectionArgs?.contentToString()}")
        when (uriMatcher.match(uri)) {
            CODE_COMMON -> {
                // 模拟返回 commonState 为 100
                val columns = arrayOf("commonState")
                val cursor = MatrixCursor(columns)
                cursor.addRow(arrayOf(100))
                return cursor
            }
            else -> {
                Log.w(TAG, "未知 URI: $uri")
                return null
            }
        }
    }

    override fun getType(uri: Uri): String? {
        return when (uriMatcher.match(uri)) {
            CODE_COMMON -> "vnd.android.cursor.dir/vnd.$AUTHORITY.$PATH_COMMON"
            else -> null
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // Mock 不实现插入
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // Mock 不实现删除
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // Mock 不实现更新
        return 0
    }
}
