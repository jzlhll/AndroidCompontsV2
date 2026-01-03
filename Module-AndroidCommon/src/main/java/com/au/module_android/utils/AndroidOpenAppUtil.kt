package com.au.module_android.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import com.au.module_android.Globals
import java.net.URLDecoder

/**
 *
 * 未知activity，打开一个packageName的应用。
 * 如果是android11需要添加可见性：在androidManifest中申明:
 * <code>
 *
 *  <queries>
 *     <!-- Specific apps you interact with, eg: -->
 *     <package android:name="com.example.store" />
 *     <package android:name="com.example.service" />
 *
 *     <!--
 *     Specific intents you query for,
 *     eg: for a custom share UI
 *     -->
 *     <intent>
 *     <action android:name="android.intent.action.SEND" />
 *     <data android:mimeType="image/jpeg" />
 *     </intent>
 *  </queries>
 *
 * </code>
 */
fun openApp(context: Context, packageName: String) : Boolean{
    try {
        val pm = context.packageManager
        val pi: PackageInfo = pm.getPackageInfo(packageName, 0)
        val resolveIntent = Intent(Intent.ACTION_MAIN, null)
        resolveIntent.addCategory(Intent.CATEGORY_LAUNCHER)
        resolveIntent.setPackage(pi.packageName)

        val apps: List<ResolveInfo> = pm.queryIntentActivities(resolveIntent, 0)

        val ri = apps.iterator().next()
        val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setComponent(cn)
        context.startActivityFix(intent)
        return true
    } catch (e:Exception) {
        e.printStackTrace()
        return false
    }
}

fun openApp2(context: Context, packageName: String) : Boolean{
    try {
        val appIntent = getAppIntent(context, packageName) ?: return false
        val className = appIntent.component?.className ?: return false
        val cn = ComponentName(packageName, className)
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.setComponent(cn)
        context.startActivityFix(intent)
        return true
    } catch (e:Exception) {
        e.printStackTrace()
        return false
    }
}

fun openUrlByBrowser(url: String, context: Context) {
    if (url.isNotBlank()) {
        ignoreError {
            val intent = Intent()
            intent.setAction("android.intent.action.VIEW")
            val cvtUrl = URLDecoder.decode(url, "utf-8")
            intent.setData(Uri.parse(cvtUrl))
            context.startActivityFix(intent)
        }
    }
}

/**
 * 获取打开其他app的intent
 */
fun getAppIntent(context:Context, packageName: String): Intent? {
    return context.packageManager.getLaunchIntentForPackage(packageName)
}

fun openAppActivity(context: Context, packageName: String, activityName:String) : Boolean{
    try {
        val intent = Intent()
        intent.setComponent(ComponentName(packageName, activityName))
        context.startActivityFix(intent)
        return true
    } catch (e:Exception) {
        e.printStackTrace()
        return false
    }
}

