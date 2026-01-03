package com.au.module_android.utilsandroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

fun toAppSetting(context: Context) {
    val intent = Intent()
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (9 <= Build.VERSION.SDK_INT) {
        intent.action = "android.settings.APPLICATION_DETAILS_SETTINGS"
        intent.data = Uri.fromParts("package", context.packageName, null)
    } else {
        intent.action = Intent.ACTION_VIEW
        intent.setClassName("com.android.settings", "com.android.settings.InstalledAppDetails")
        intent.putExtra("com.android.settings.ApplicationPkgName", context.packageName)
    }
    context.startActivity(intent)
}
