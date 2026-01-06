package com.au.module_simplepermission.permission

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_simplepermission.IContractResult

/**
 * 这是为了解决 Android 存储访问框架（Storage Access Framework, SAF）的核心问题：在 Android 11（API 30）及以上版本，应用无法再通过 WRITE_EXTERNAL_STORAGE 权限随意写入公共存储空间。取而代之的，是更安全的、由用户控制的授权方式。
 * 目标：获取对某个目录（及其所有子目录）的持久访问权限。
 * 结果：系统返回一个表示该目录的 Uri（通常是一个 DocumentFile 对象），应用此后就可以无需再次询问用户，直接在该目录下创建、读取、写入或删除文件。
 */
class SelectSysDirForResult(owner:Any) : IContractResult<Uri?, Uri?>(owner,
    ActivityResultContracts.OpenDocumentTree()) {
    override fun start(input: Uri?, callback: ActivityResultCallback<Uri?>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }

}