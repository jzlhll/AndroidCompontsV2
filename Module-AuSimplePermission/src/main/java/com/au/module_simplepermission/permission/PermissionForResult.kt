package com.au.module_simplepermission.permission

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.au.module_simplepermission.hasPermission

internal class PermissionForResult(cxt: Any,
                                   permission: String) :
    IOnePermissionResult(permission, cxt, ActivityResultContracts.RequestPermission()) {

    override fun safeRun(block: () -> Unit, notGivePermissionBlock: (() -> Unit)?, option: ActivityOptionsCompat?) {
        if(toContext().hasPermission(permission)) {
            block.invoke()
        } else {
            setResultCallback {
                if(it) block.invoke() else notGivePermissionBlock?.invoke()
            }
            launcher.launch(permission, option)
        }
    }
}