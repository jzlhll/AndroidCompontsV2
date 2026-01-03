package com.au.module_android.utils

import android.content.Context
import android.content.pm.PackageManager
/**
 * 清单文件中的 meta 数据获取
 */
class MetaUtil private constructor() {
    companion object {
        fun getMetaDataString(context: Context, metaDataName: String, defaultValue: String? = null): String? {
            return try {
                val appInfo = context.applicationContext.packageManager.getApplicationInfo(
                    context.applicationContext.packageName, PackageManager.GET_META_DATA
                )
                appInfo.metaData.getString(metaDataName, defaultValue) ?: defaultValue
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                defaultValue
            }
        }

        fun getMetaDataInt(context: Context, metaDataName: String, defaultValue: Int = 0): Int {
            return try {
                val appInfo = context.applicationContext.packageManager.getApplicationInfo(
                    context.applicationContext.packageName, PackageManager.GET_META_DATA
                )
                val value = appInfo.metaData.getInt(metaDataName, defaultValue)
                value ?: defaultValue
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                defaultValue
            }
        }

        fun getMetaDataBoolean(context: Context, metaDataName: String, defaultValue: Boolean = false): Boolean {
            return try {
                val appInfo = context.applicationContext.packageManager.getApplicationInfo(
                    context.applicationContext.packageName, PackageManager.GET_META_DATA
                )
                appInfo.metaData.getBoolean(metaDataName, defaultValue)
            } catch (e: PackageManager.NameNotFoundException) {
                e.printStackTrace()
                defaultValue
            }
        }

        fun getMetaDataFloat(context: Context, metaDataName: String, defaultValue: Float = 0.0f): Float {
            return try {
                getMetaData(context.applicationContext, metaDataName, defaultValue) ?: defaultValue
            } catch (e: java.lang.Exception) {
                defaultValue
            }
        }

        fun <T> getMetaData(context: Context, metaDataName: String?, defaultValue: T? = null): T? {
            return try {
                getMetaData(context.applicationContext, metaDataName, defaultValue)
            } catch (e: Exception) {
                defaultValue
            }
        }
    }
}