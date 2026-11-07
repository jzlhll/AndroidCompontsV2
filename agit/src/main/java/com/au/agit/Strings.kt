package com.au.agit

import androidx.annotation.StringRes
import com.au.module_android.Globals

/** 异步业务反馈使用应用 Context，界面文案直接使用 stringResource。 */
fun appString(@StringRes id: Int, vararg args: Any): String = Globals.app.getString(id, *args)
