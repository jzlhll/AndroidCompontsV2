package com.au.module_android.permissions.activity

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_android.permissions.IContractResult

/**
 * 调用后将弹出让你选择文件夹的目的
 */
class SelectSysDirForResult(owner:Any) : IContractResult<Uri?, Uri?>(owner,
    ActivityResultContracts.OpenDocumentTree()) {
    override fun start(input: Uri?, callback: ActivityResultCallback<Uri?>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }

}