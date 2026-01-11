package com.au.module_androidui.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.annotation.AnimRes
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import com.au.module_androidui.R
import com.au.module_simplepermission.activity.ActivityForResult

const val KEY_EXIT_ANIM = "activity_key_exit_anim"
const val KEY_ENTER_ANIM = "activity_key_enter_anim"

fun Context.startActivityFix(intent: Intent, opts:Bundle? = null,
                             @AnimRes enterAnim:Int? = null,
                             @AnimRes exitAnim:Int? = null) {
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (enterAnim != null) intent.putExtra(KEY_ENTER_ANIM, enterAnim)
    if (exitAnim != null) intent.putExtra(KEY_EXIT_ANIM, exitAnim)

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

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && enterAnim != null && this is Activity) {
        this.overridePendingTransition(enterAnim, R.anim.activity_stay)
    }
}

fun ActivityForResult.animStart(
                      context: Context,
                      intent: Intent,
                      enterAnim:Int? = null,
                      exitAnim:Int? = null,
                      option: ActivityOptionsCompat?,
                      callback: ActivityResultCallback<ActivityResult>?) {
    if (enterAnim != null) intent.putExtra(KEY_ENTER_ANIM, enterAnim)
    if (exitAnim != null) intent.putExtra(KEY_EXIT_ANIM, exitAnim)
    start(intent, option, callback)
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && enterAnim != null && context is Activity) {
        context.overridePendingTransition(enterAnim, R.anim.activity_stay)
    }
}

fun Fragment.startActivityFix(intent: Intent, opts:Bundle? = null, @AnimRes enterAnim:Int? = null, @AnimRes exitAnim:Int? = null) {
    requireActivity().startActivityFix(intent, opts, enterAnim, exitAnim)
}

fun Context.startOutActivity(intent: Intent, opts:Bundle? = null, @AnimRes enterAnim:Int? = null, @AnimRes exitAnim:Int? = null) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Android 10 或更高版本
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    } else {
        // Android 10 以下版本
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    if (enterAnim != null) intent.putExtra(KEY_ENTER_ANIM, enterAnim)
    if (exitAnim != null) intent.putExtra(KEY_EXIT_ANIM, exitAnim)

    try {
        startActivity(intent, opts)
    } catch (e:Exception) {
        e.printStackTrace()
    }

    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.TIRAMISU && enterAnim != null && this is Activity) {
        this.overridePendingTransition(enterAnim, R.anim.activity_stay)
    }
}