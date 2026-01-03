package com.au.module_android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object GotoMarketUtil {
    // 跳转应用市场
    const val MARKET_GOOGLE_PLAY = "com.android.vending"
    const val MARKET_HUAWEI = "com.huawei.appmarket"
    const val MARKET_XIAOMI = "com.xiaomi.market"
    const val MARKET_MEIZU = "com.meizu.mstore"
    const val MARKET_WANDOUJIA = "com.wandoujia.phoenix2"
    const val MARKET_360 = "com.qihoo.appstore"
    const val MARKET_YYB = "com.tencent.android.qqdownloader"
    const val MARKET_BAIDU = "com.baidu.appsearch"
    fun toMarket(context: Context, appPkg: String, marketPkg: String?): Boolean {
        return try {
            val uri = Uri.parse("market://details?id=$appPkg")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (marketPkg != null && marketPkg.trim { it <= ' ' }.isNotEmpty()) {
                // 如果没给市场的包名，则系统会弹出市场的列表让你进行选择。
                intent.setPackage(marketPkg.trim { it <= ' ' })
            }
            context.startActivity(intent)
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            false
        }
    }
}