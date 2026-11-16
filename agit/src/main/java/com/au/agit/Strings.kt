package com.au.agit

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.au.agit.data.AGitErrorCode
import com.au.module_android.Globals

/** 异步业务反馈遵循应用语言；已登记的错误按固定定义追加编号。 */
fun appString(@StringRes id: Int, vararg args: Any): String {
    val message = Globals.getString(id, *args)
    return AGitErrorCode.fromResource(id)?.format(message) ?: message
}

@Composable
fun errorStringResource(error: AGitErrorCode, vararg args: Any): String =
    error.format(stringResource(error.messageResId, *args))
