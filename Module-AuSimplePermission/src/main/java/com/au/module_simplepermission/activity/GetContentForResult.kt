package com.au.module_simplepermission.activity

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_simplepermission.IContractResult

/**
 * 当初始化完成这个对象后，请在onCreate里面调用 函数（onCreate）即可
 *
 * GetMultipleContents 的单选版本 参考[GetMultipleContentsForResult]
 */
class GetContentForResult(owner:Any) : IContractResult<String, Uri?>(owner,
    ActivityResultContracts.GetContent()) {

    override fun start(input: String, callback: ActivityResultCallback<Uri?>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }
}