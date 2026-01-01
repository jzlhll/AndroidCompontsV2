package com.au.module_simplepermission.activity

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_simplepermission.IContractResult

/**
 * 当初始化完成这个对象后，请在onCreate里面调用 函数（onCreate）即可
 */
class SystemTakePictureForResult(owner:Any) : IContractResult<Uri, Boolean>(owner, ActivityResultContracts.TakePicture()) {
    /**
     * 启动activity
     */
    override fun start(input:Uri, callback: ActivityResultCallback<Boolean>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }
}