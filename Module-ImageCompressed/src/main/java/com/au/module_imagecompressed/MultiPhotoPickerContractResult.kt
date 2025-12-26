package com.au.module_imagecompressed

import com.au.module_android.BuildConfig
import com.au.module_android.permissions.IContractResult
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchOnUi
import com.au.module_android.utilsmedia.*
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java.io.File

/**
 * @author allan
 * @date :2024/10/23 16:39
 * @description:
 */
class MultiPhotoPickerContractResult(
    private val fragment: Fragment,
    var max:Int,
    resultContract: ActivityResultContract<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>)
    : IContractResult<PickVisualMediaRequest, List<@JvmSuppressWildcards Uri>>(fragment, resultContract) {

    private var oneByOneCallback:((PickUriWrap)->Unit)? = null
    private var allCallback:((Array<PickUriWrap>)->Unit)? = null

    val paramsBuilder = PickerMediaParams.Builder().asStingy()

    private val logTag = "Picker"

    fun setCurrentMaxItems(max:Int) : MultiPhotoPickerContractResult {
        require(max > 0) {"max must > 0"}
        this.max = max
        resultContract.asOrNull<CompatMultiPickVisualMedia>()?.setCurrentMaxItems(max)
        return this
    }

    @WorkerThread
    private fun lubanCompress(uriWrap: PickUriWrap,
                              isAllCallback: Boolean,
                              totalNum: Int,
                              allResults: MutableList<PickUriWrap>,
                              params: PickerMediaParams) {
        LubanCompress()
            .setResultCallback { srcPath, resultPath, isSuc -> //主线程。Luban内部main handler回调回来的
                val path = resultPath ?: srcPath
                if (path != null) {
                    val pathFile = File(path)
                    val parsedInfo = pathFile.myParse()
                    uriWrap.uriParsedInfo = parsedInfo
                    uriWrap.beLimitedSize = parsedInfo.fileLength > params.targetImageSize
                    uriWrap.beCopied = true

                    if(BuildConfig.DEBUG) Log.d(logTag, "3>luban: $uriWrap")
                }

                if (!isAllCallback) {
                    oneByOneCallback?.invoke(uriWrap)
                } else {
                    allResults.add(uriWrap)
                    if (allResults.size == totalNum) {
                        allCallback?.invoke(allResults.toTypedArray())
                    }
                }
            }
            .compress(fragment.requireContext(), uriWrap.uriParsedInfo.uri, params.ignoreSizeKb)
    }

    private val subCacheDir = "luban_disk_cache"
    private val copyFilePrefix = "copy_"

    private fun ifCopy(
        uri: Uri,
        totalNum: Int,
        cr: ContentResolver,
        params: PickerMediaParams
    ): PickUriWrap {
        val parsedInfo = uri.myParse(cr)
        val fileSize = parsedInfo.fileLength
        val isImage = parsedInfo.isUriImage()
        val limitSize = if(isImage) params.limitImageSize.toLong() else params.limitVideoSize

        if (fileSize > limitSize) {
            return PickUriWrap(parsedInfo, totalNum, isImage, beLimitedSize = true)
        }

        return when (params.copyMode) {
            CopyMode.COPY_NOTHING -> {
                PickUriWrap(parsedInfo, totalNum, isImage)
            }

            CopyMode.COPY_NOTHING_BUT_CVT_HEIC -> {
                if (parsedInfo.isUriHeic()) {
                    val size = longArrayOf(-1L)
                    val copyUri = uri.copyToCacheConvert(cr, URI_COPY_PARAM_HEIC_TO_JPG, subCacheDir, copyFilePrefix, size)
                    PickUriWrap(
                        parsedInfo,
                        totalNum,
                        isImage,
                        beCopied = copyUri != uri)
                } else {
                    PickUriWrap(parsedInfo, totalNum, isImage)
                }
            }

            CopyMode.COPY_CVT_IMAGE_TO_JPG -> {
                if (isImage) {
                    val size = longArrayOf(-1L)
                    val copyUri = uri.copyToCacheConvert(cr, URI_COPY_PARAM_ANY_TO_JPG, subCacheDir, copyFilePrefix, size)
                    val copyParsedInfo = copyUri.myParse(cr)
                    PickUriWrap(
                        copyParsedInfo, totalNum,
                        isImage = true,
                        beCopied = copyUri != uri,
                    )
                } else {
                    PickUriWrap(parsedInfo, totalNum, isImage = false)
                }
            }

            CopyMode.COPY_ALWAYS -> {
                val size = longArrayOf(-1L)
                val copyUri = uri.copyToCacheConvert(cr, URI_COPY_PARAM_HEIC_TO_JPG, subCacheDir, copyFilePrefix, size)
                val copyParsedInfo = copyUri.myParse(cr)
                PickUriWrap(
                    copyParsedInfo, totalNum,
                    isImage = true,
                    beCopied = copyUri != uri)
            }
        }
    }

    private val resultCallback:(List<@JvmSuppressWildcards Uri>)->Unit = { result->
        //自行处理不调用super
        //1. 兼容老版本的限定，选择回来多了，做下cut
        val cutUriList = if (result.size > max) { //兼容老版本无法限制picker数量
            result.subList(0, max)
        } else {
            result
        }

        if (cutUriList.isEmpty()) {
            if (allCallback != null) {
                allCallback?.invoke(arrayOf())
            }
        } else {
            if (BuildConfig.DEBUG) {
                cutUriList.forEach {
                    Log.d(logTag, "1>onActivityResult: $it")
                }
            }

            fragment.lifecycleScope.launchOnThread {
                val cr = fragment.requireContext().contentResolver
                val totalNum = cutUriList.size
                val params = paramsBuilder.build()

                val isAllCallback = allCallback != null
                val allResults = mutableListOf<PickUriWrap>()

                cutUriList.forEach { uri->
                    //2. check if copy
                    val uriWrap = ifCopy(uri, totalNum, cr, params)
                    if(BuildConfig.DEBUG) Log.d(logTag, "2>if Copy: $uriWrap")

                    if (!params.needLuban || !uriWrap.isImage) {
                        //3. 回调
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
                    } else {
                        //3. luban压缩和回调
                        lubanCompress(uriWrap, isAllCallback, totalNum, allResults, params)
                    }
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