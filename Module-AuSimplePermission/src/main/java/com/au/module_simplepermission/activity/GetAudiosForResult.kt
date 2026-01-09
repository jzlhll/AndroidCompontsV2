package com.au.module_simplepermission.activity

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_simplepermission.IContractResult

class GetAudiosForResult(owner:Any,
                         val audioMime:String) : IContractResult<String, List<@JvmSuppressWildcards Uri>>(owner,
    ActivityResultContracts.GetMultipleContents()) {

    @Deprecated("Please call another start()")
    override fun start(input: String, callback: ActivityResultCallback<List<@JvmSuppressWildcards Uri>>?) {
        throw RuntimeException("Please call another start()")
    }

    fun start(callback: ActivityResultCallback<List<@JvmSuppressWildcards Uri>>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(audioMime)
    }
}

class GetAudioForResult(owner:Any,
                         val audioMime:String) : IContractResult<String, Uri?>(owner,
    ActivityResultContracts.GetContent()) {

    @Deprecated("Please call another start()")
    override fun start(input: String, callback: ActivityResultCallback<Uri?>?) {
        throw RuntimeException("Please call another start()")
    }

    fun start(callback: ActivityResultCallback<Uri?>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(audioMime)
    }
}