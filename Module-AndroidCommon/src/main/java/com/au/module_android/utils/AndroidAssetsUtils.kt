package com.au.module_android.utils

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

/**
 * assets工具
 */
class AndroidAssetsUtils {
    companion object {
        /**
         * 从raw问件中读取文件
         *
         * @param context
         * @param rawResId
         * @return
         */
        fun getFromRaw(context: Context, rawResId: Int): String? {
            try {
                val inputReader = InputStreamReader(context.resources.openRawResource(rawResId))
                val bufReader = BufferedReader(inputReader)
                var line: String? = ""
                var Result: String? = ""
                while (bufReader.readLine().also { line = it } != null) Result += line
                return Result
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        /**
         * 从Assets中读取文本
         */
        fun getStringFromAssets(context: Context, fileName: String): String? {
            try {
                val inputReader = InputStreamReader(
                    context.resources.assets.open(fileName)
                )
                val bufReader = BufferedReader(inputReader)
                var line: String? = ""
                var Result: String? = ""
                while (bufReader.readLine().also { line = it } != null) Result += line
                return Result
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        /**
         * 从Assets中读取图片
         */
        fun getImageFromAssets(context: Context, fileName: String): Bitmap? {
            var image: Bitmap? = null
            val am = context.resources.assets
            try {
                val fs = am.open(fileName)
                image = BitmapFactory.decodeStream(fs)
                fs.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return image
        }

        /**
         * 可以获取drawable资源的uri
         *
         * @param context Context
         * @param id      DrawableRes
         * @return String
         */
        fun getResourcesUri(context: Context, @DrawableRes id: Int): String {
            val resources = context.resources
            return ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                    resources.getResourcePackageName(id) + "/" +
                    resources.getResourceTypeName(id) + "/" +
                    resources.getResourceEntryName(id)
        }

        /**
         * 可以获取drawable资源的uri
         *
         * @param context Context
         * @param id      DrawableRes
         * @return Uri
         */
        fun getResourcesUri(@DrawableRes id: Int, context: Context): Uri {
            return Uri.parse(getResourcesUri(context, id))
        }
    }
}