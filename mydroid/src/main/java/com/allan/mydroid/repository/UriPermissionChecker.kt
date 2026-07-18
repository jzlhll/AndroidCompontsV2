package com.allan.mydroid.repository

import android.content.Intent
import android.net.Uri
import com.allan.mydroid.CHECK_URI_PERMISSION
import com.allan.mydroid.PICKER_NEED_PERMISSION
import com.allan.mydroid.beansinner.FROM_LOCAL
import com.allan.mydroid.beansinner.FROM_PICKER
import com.allan.mydroid.beansinner.FROM_SHARE_IN
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_android.Globals

class UriPermissionChecker {
    fun isHostThisUri(shareInBean: ShareInBean): Boolean {
        if (!CHECK_URI_PERMISSION) {
            return true
        }

        if (shareInBean.from == FROM_LOCAL || shareInBean.from == FROM_SHARE_IN) {
            return true
        }

        if (shareInBean.from == FROM_PICKER && !PICKER_NEED_PERMISSION) {
            return true
        }

        appHostPermissions().forEach { uri ->
            if (uri == shareInBean.uri) {
                return true
            }
        }
        return false
    }

    fun takeHostPermission(uri: Uri) {
        if (CHECK_URI_PERMISSION) {
            try {
                Globals.app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun appHostPermissions(): List<Uri> {
        val list = Globals.app.contentResolver.persistedUriPermissions
        return list.map { it.uri }
    }
}
