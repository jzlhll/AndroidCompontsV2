package com.au.module_simplepermission

import androidx.lifecycle.LifecycleOwner
import com.au.module_simplepermission.activity.ActivityForResult

/**
 * activity 跳转，返回拿结果。
 */
fun LifecycleOwner.createActivityForResult() : ActivityForResult = ActivityForResult(this)