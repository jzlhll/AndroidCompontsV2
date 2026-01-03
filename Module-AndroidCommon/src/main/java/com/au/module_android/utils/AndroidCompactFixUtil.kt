package com.au.module_android.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle

/**
 * 兼容android13+的广播注册，避免报错
 */
fun Context.registerReceiverFix(receiver: BroadcastReceiver, filter: IntentFilter,
                                receiverSystemOrOtherApp:Boolean = true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(
            receiver, filter,
            if (receiverSystemOrOtherApp) Context.RECEIVER_EXPORTED else Context.RECEIVER_NOT_EXPORTED
        )
    } else {
        registerReceiver(receiver, filter)
    }
}

fun Context.startActivityFix(intent: Intent, opts:Bundle? = null) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(intent, opts)
    } catch (_:Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 10 或更高版本
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            // Android 10 以下版本
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent, opts)
    }
}