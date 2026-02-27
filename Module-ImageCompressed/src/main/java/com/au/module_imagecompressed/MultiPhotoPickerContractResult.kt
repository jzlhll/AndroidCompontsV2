package com.au.module_imagecompressed

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import com.au.module_android.utils.asOrNull
import com.au.module_simplepermission.CompatMultiPickVisualMedia
import com.au.module_simplepermission.IContractResult
import com.au.module_simplepermission.PickerType

/**
 * @author allan
 * @date :2024/10/23 16:39
 * @description:
 */
class MultiPhotoPickerContractResult(
    fragment: Fragment,
    var max:Int,
    val resultContract: ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>)
    : IContractResult<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>(fragment, resultContract) {

    private var allCallback:((List<Uri>)->Unit)? = null

    fun setCurrentMaxItems(max:Int) : MultiPhotoPickerContractResult {
        require(max > 0) {"max must > 0"}
        this.max = max
        resultContract.asOrNull<CompatMultiPickVisualMedia>()?.setCurrentMaxItems(max)
        return this
    }

    private val resultCallback:(List<@JvmSuppressWildcards Uri>)->Unit = { result->
        //1. 选择回来多了，做下cut
        val cutUriList = if (result.size > max) {
            result.subList(0, max)
        } else {
            result
        }

        allCallback?.invoke(cutUriList)
    }

    fun launchByAll(type: PickerType, option: ActivityOptionsCompat?, callback:(List<Uri>)->Unit) {
        this.allCallback = callback
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