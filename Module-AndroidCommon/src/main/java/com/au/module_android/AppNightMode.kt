package com.au.module_android

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

enum class AppNightMode(val delegateMode: Int) {
    FollowSystem(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    Light(AppCompatDelegate.MODE_NIGHT_NO),
    Dark(AppCompatDelegate.MODE_NIGHT_YES),
}

private const val APPEARANCE_PREFERENCES = "app_appearance"
private const val NIGHT_MODE_KEY = "night_mode"

/** 传统 View 应用的夜间选择；纯 Compose 应用使用自己的外观偏好。 */
fun appNightMode(context: Context): AppNightMode {
    val name = context.getSharedPreferences(APPEARANCE_PREFERENCES, Context.MODE_PRIVATE)
        .getString(NIGHT_MODE_KEY, null)
    return AppNightMode.entries.firstOrNull { it.name == name } ?: AppNightMode.FollowSystem
}

/** 在第一个 Activity 创建前恢复，后续重建由 AppCompat 处理。 */
fun restoreAppNightMode(context: Context) {
    AppCompatDelegate.setDefaultNightMode(appNightMode(context).delegateMode)
}

fun setAppNightMode(context: Context, mode: AppNightMode) {
    context.getSharedPreferences(APPEARANCE_PREFERENCES, Context.MODE_PRIVATE).edit()
        .putString(NIGHT_MODE_KEY, mode.name).apply()
    AppCompatDelegate.setDefaultNightMode(mode.delegateMode)
}

/** 使用当前界面 Context 判断实际的资源模式。 */
fun Context.isNightMode(): Boolean =
    (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
