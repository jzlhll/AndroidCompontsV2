package com.au.module_simplepermission.permission

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import com.au.module_simplepermission.hasPermission

internal class PermissionsForResult(cxt:Any,
                                    permissions: Array<String>)
        : IMultiPermissionsResult(permissions, cxt, ActivityResultContracts.RequestMultiplePermissions()) {

    override fun safeRun(notGivePermissionBlock: (() -> Unit)?, option: ActivityOptionsCompat?, block: () -> Unit) {
        if (toContext().hasPermission(*permissions)) {
            block.invoke()
        } else {
            setResultCallback {
                var hasPermission = false
                for (entry in it) {
                    if (!entry.value) {
                        hasPermission = false
                        break
                    } else {
                        hasPermission = true
                    }
                }
                if(hasPermission) block.invoke() else notGivePermissionBlock?.invoke()
            }
            launcher.launch(permissions, option)
        }
    }
}