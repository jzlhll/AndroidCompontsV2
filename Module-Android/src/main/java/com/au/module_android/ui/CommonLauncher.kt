package com.au.module_android.ui

import android.content.Context
import androidx.fragment.app.Fragment

fun Class<out Fragment>.start(context: Context, shellActivityClass:Class<out FragmentShellActivity> = FragmentShellActivity::class.java) {
    when (shellActivityClass) {
        FragmentShellOrientationActivity::class.java -> {
            FragmentShellOrientationActivity.start(context, this)
        }
        FragmentShellTranslucentActivity::class.java -> {
            FragmentShellOrientationActivity.start(context, this)
        }
        else -> {
            FragmentShellActivity.start(context, this)
        }
    }
}