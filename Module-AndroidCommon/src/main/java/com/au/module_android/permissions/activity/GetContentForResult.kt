package com.au.module_android.permissions.activity

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_android.permissions.IContractResult

/**
 * 当初始化完成这个对象后，请在onCreate里面调用 函数（onCreate）即可
 *
 * GetMultipleContents主要用于选择内容（如图片、文件），但返回的Uri权限是临时的，应用进程结束后会失效
 * OpenMultipleDocuments专门用于文档操作，系统会自动授予持久化权限，适合需要长期访问的场景
 */
class GetContentForResult(owner:Any) : IContractResult<Array<String>, List<@JvmSuppressWildcards Uri>>(owner,
    ActivityResultContracts.OpenMultipleDocuments()) {

    override fun start(
        input: Array<String>,
        callback: ActivityResultCallback<List<@JvmSuppressWildcards Uri>>?
    ) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }
}