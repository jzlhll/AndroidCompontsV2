package com.au.module_simplepermission.permission

import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat

internal class AlwaysRunPermissionForResult(cxt: Any) :
    IOnePermissionResult("", cxt, ActivityResultContracts.RequestPermission()) {

    override fun safeRun(notGivePermissionBlock: (() -> Unit)?, option: ActivityOptionsCompat?, block: () -> Unit) {
        block()
    }
}