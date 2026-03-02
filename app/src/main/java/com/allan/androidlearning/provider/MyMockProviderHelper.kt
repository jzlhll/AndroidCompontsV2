package com.allan.androidlearning.provider

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * 用于从其他项目或模块访问 MyMockProvider 的辅助类。
 *
 * 注意：对于其他应用（Android 11+），必须在 AndroidManifest.xml 中添加以下内容：
 * <queries>
 *     <provider android:authorities="com.allan.androidlearning.provider.mock" />
 * </queries>
 * 或者使用 <queries><package android:name="com.allan.androidlearning" /></queries>
 */
object MyMockProviderHelper {
    private const val TAG = "alland"
    // 与 MyMockProvider.AUTHORITY 保持一致
    private const val AUTHORITY = "com.allan.androidlearning.provider.mock"
    private const val PATH_COMMON = "common"
    
    val CONTENT_URI: Uri = "content://$AUTHORITY/$PATH_COMMON".toUri()

    /**
     * 从 Provider 查询 commonState。
     * @return commonState 的整数值，如果失败则返回 -1。
     */
    fun getCommonState(context: Context): Int {
        var cursor: Cursor? = null
        try {
            Log.d(TAG, "getCommonState: 开始查询 $CONTENT_URI")
            cursor = context.contentResolver.query(
                CONTENT_URI,
                arrayOf("commonState"),
                null,
                null,
                null
            )
            
            if (cursor == null) {
                Log.e(TAG, "getCommonState: Cursor 为空。请检查 Provider 是否安装、导出以及可见性配置（queries 标签）。")
                return -1
            }

            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex("commonState")
                if (index != -1) {
                    val value = cursor.getInt(index)
                    Log.d(TAG, "getCommonState 成功: $value")
                    return value
                } else {
                    Log.e(TAG, "getCommonState: 未找到 'commonState' 列")
                }
            } else {
                Log.e(TAG, "getCommonState: Cursor 为空（无数据）")
            }
        } catch (e: Exception) {
            Log.e(TAG, "getCommonState 错误。请检查权限或可见性。", e)
        } finally {
            cursor?.close()
        }
        return -1
    }
}
