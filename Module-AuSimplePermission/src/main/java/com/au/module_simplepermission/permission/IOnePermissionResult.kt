package com.au.module_simplepermission.permission

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import com.au.module_simplepermission.IContractResult

abstract class IOnePermissionResult(val permission:String,
                                    cxt: Any,
                                    contract: ActivityResultContract<String, Boolean>)
    : IContractResult<String, Boolean>(cxt, contract) {

    abstract fun safeRun(notGivePermissionBlock:(()->Unit)? = null, option: ActivityOptionsCompat? = null, block:()->Unit)

    @Deprecated("call safeRun()")
    override fun start(input: String, callback: ActivityResultCallback<Boolean>?) {
        throw IllegalAccessException("not support please call safeRun.")
    }
}