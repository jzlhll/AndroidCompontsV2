package com.au.module_simplepermission

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment

/**
 * @author allan
 * @date :2024/10/23 16:39
 * @description:
 */
class MultiUriPickerContractResult(
    fragment: Fragment,
    var max:Int,
    val resultContract: ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>
)
    : IContractResult<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>(fragment, resultContract) {
    private var allCallback:((Array<Uri>)->Unit)? = null

    fun setCurrentMaxItems(max:Int) : MultiUriPickerContractResult {
        require(max > 0) {"max must > 0"}
        this.max = max
        (resultContract as? CompatMultiPickVisualMedia)?.setCurrentMaxItems(max)
        return this
    }

    private val resultCallback:(List<@JvmSuppressWildcards Uri>)->Unit = { result->
        allCallback?.invoke(result.toTypedArray())
    }

    /**
     * 可以使用。但推荐使用oneByOne。
     */
    fun launchByAll(type: PickerType, option: ActivityOptionsCompat?, callback:(Array<Uri>)->Unit) {
        this.allCallback = callback
        launchCommon(type, option)
    }

    private fun launchCommon(type: PickerType, option: ActivityOptionsCompat?) {
        setResultCallback {
            resultCallback(it)
        }

        val intent = when (type) {
            PickerType.IMAGE -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            PickerType.IMAGE_AND_VIDEO -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            PickerType.VIDEO -> PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        }

        launcher.launch(intent, option)
    }

    override fun start(
        input: PickVisualMediaRequest,
        callback: ActivityResultCallback<List<@JvmSuppressWildcards Uri>>?
    ) {
        //do nothing.
    }
}