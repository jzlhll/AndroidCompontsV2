package com.au.module_imagecompressed

import android.net.Uri
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utilsmedia.myParse
import com.au.module_simplepermission.CompatMultiPickVisualMedia
import com.au.module_simplepermission.IContractResult
import com.au.module_simplepermission.PickerType

/**
 * @author allan
 * @date :2024/10/23 16:39
 * @description:
 */
class MultiPhotoPickerContractResult(
    private val fragment: Fragment,
    var max:Int,
    val resultContract: ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>)
    : IContractResult<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>(fragment, resultContract) {

    private var oneByOneCallback:((PickUriWrap)->Unit)? = null
    private var allCallback:((Array<PickUriWrap>)->Unit)? = null

    val paramsBuilder = PickerMediaParams.Builder().asCopyAndStingy()

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

        if (cutUriList.isEmpty()) {
            if (allCallback != null) {
                allCallback?.invoke(arrayOf())
            }
        } else {
            fragment.lifecycleScope.launchOnThread {
                val cr = fragment.requireContext().contentResolver
                val totalNum = cutUriList.size
                val params = paramsBuilder.build()
                val imageEngine = params.compressEngine

                val isAllCallback = allCallback != null
                val allResults = mutableListOf<PickUriWrap>()

                cutUriList.forEach { uri->
                    //2. check if copy
                    val parsedInfo = uri.myParse(cr)
                    val isImage = parsedInfo.isUriImage()
                    if (isImage) {
                        val isNeedCompress = (imageEngine != null && parsedInfo.fileLength < params.limitImageSize * 2)
                        val isNeedCopy = params.alwaysCopyImage

                        if (isNeedCompress) {
                            val compressedFile = imageEngine.compress(fragment.requireContext(), uri)
                            val uriParsedInfoCompress = compressedFile.myParse()
                            val uriWrap = PickUriWrap(uriParsedInfoCompress, totalNum, isImage=true, beCopied = true)

                            callback(isAllCallback, uriWrap, allResults, totalNum)
                        } else if (isNeedCopy) {
                            val file = uri.copyToCacheFile(fragment.requireContext())
                            val copyInfo = file.myParse()
                            val uriWrap = PickUriWrap(copyInfo, totalNum, isImage=true, beCopied = true)

                            callback(isAllCallback, uriWrap, allResults, totalNum)
                        } else {
                            val uriWrap = PickUriWrap(parsedInfo, totalNum, isImage=true, beCopied = false)
                            callback(isAllCallback, uriWrap, allResults, totalNum)
                        }
                    } else {
                        //val isNeedCompress = false
                        val isNeedCopy = params.alwaysCopyVideo
                        if (isNeedCopy) {
                            val file = uri.copyToCacheFile(fragment.requireContext())
                            val copyInfo = file.myParse()
                            val uriWrap = PickUriWrap(copyInfo, totalNum, isImage=true, beCopied = true)

                            callback(isAllCallback, uriWrap, allResults, totalNum)
                        } else {
                            val uriWrap = PickUriWrap(parsedInfo, totalNum, isImage=true, beCopied = false)
                            callback(isAllCallback, uriWrap, allResults, totalNum)
                        }
                    }
                }
            }
        }
    }

    private fun callback(isAllCallback: Boolean, uriWrap: PickUriWrap, allResults: MutableList<PickUriWrap>, totalNum:Int) {
        fragment.lifecycleScope.launchOnUi {
            if(!isAllCallback)
                oneByOneCallback?.invoke(uriWrap)
            else {
                allResults.add(uriWrap)
                if (allResults.size == totalNum) {
                    allCallback?.invoke(allResults.toTypedArray())
                }
            }
        }
    }

    /**
     * 推荐使用
     */
    fun launchOneByOne(type: PickerType, option: ActivityOptionsCompat?, oneByOneCallback:(PickUriWrap)->Unit) {
        this.oneByOneCallback = oneByOneCallback
        this.allCallback = null
        launchCommon(type, option)
    }

    /**
     * 可以使用。但推荐使用oneByOne。
     */
    fun launchByAll(type: PickerType, option: ActivityOptionsCompat?, callback:(Array<PickUriWrap>)->Unit) {
        this.allCallback = callback
        this.oneByOneCallback = null
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