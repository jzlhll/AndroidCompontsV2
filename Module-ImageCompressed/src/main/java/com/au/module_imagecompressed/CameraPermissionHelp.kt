package com.au.module_imagecompressed

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_android.utilsmedia.myParse
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_imagecompressed.compressor.BestImageCompressor
import com.au.module_simplepermission.BaseCameraPermissionHelp
import com.au.module_simplepermission.ICameraFileProviderSupply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

/**
 * 封装了请求camera的一系列动作。
 * 使用规则：直接在fragment或者Activity中全局变量申明。使用的时候，只有safeRun调用即可。
 */
class CameraPermissionHelp : BaseCameraPermissionHelp {
    constructor(f: Fragment, supplier: ICameraFileProviderSupply) : super(f, supplier)
    constructor(fa: FragmentActivity, supplier:ICameraFileProviderSupply) : super(fa, supplier)

    /**
     * 相比safeRunTakePic，是必定callback有回调的。适用于H5WebView请求必须有回调回去的场景
     * @param  callback 一定有回调。null就是失败或者就是没有拍照回来。
     * @return 返回true表示拍照无法弹出授权。返回false则一定是能弹窗或者直接拍照去了。
     */
    fun safeRunTakePicMust(compress:Boolean = true,
                           qualityType:String = "default",
                           errorToastBlock:()->Unit = {ToastBuilder().setOnTop().setIcon("info").setMessage("需要camera权限.").toast() },
                           callback: (mode:String, uriWrap: PickUriWrap?)->Unit) : Boolean{
        val ret = safeRunTakePic({createdTmpFile->
            if (createdTmpFile != null) {
                if (compress) {
                    Globals.backgroundScope.launch {
                        logdNoFile { "createdTempFile $createdTmpFile size: ${createdTmpFile.length()}" }
                        val compressedFile = BestImageCompressor.systemCompressFile(createdTmpFile, BestImageCompressor.Config(qualityType = qualityType))
                        logdNoFile { "compressedFile $compressedFile size: ${compressedFile?.length()}" }
                        if (compressedFile != null) {
                            //需要再次从压缩文件覆盖createdTmpFile
                            if (false) {
                                 createdTmpFile.delete()
                                 logdNoFile { "compressedFile delete $compressedFile and move to compressed" }
                                 Files.move(compressedFile.toPath(), createdTmpFile.toPath())
                            }

                            withContext(Dispatchers.Main) {
                                val cvtUri = imageFileConvertToWrap(createdTmpFile)
                                callback("takePicAndCompressed", cvtUri)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val cvtUri = imageFileConvertToWrap(createdTmpFile)
                                callback("takePicAndCompressFailUseOrig", cvtUri)
                            }
                        }
                    }
                } else {
                    val cvtUri = imageFileConvertToWrap(createdTmpFile)
                    callback("takePicResultDirect", cvtUri)
                }
            } else {
                callback("takePicNoResult", null)
            }
            null
        }, notGivePermissionBlock = {
            errorToastBlock()
            callback("notGivePermission", null)
        })
        if (ret == -2) {
            errorToastBlock()
            callback("permissionRejectDirect", null)
        }

        return ret == -2
    }

    //我的cacheDir或者fileDir下的文件来转成UriWrap。
    private fun imageFileConvertToWrap(file:File) = PickUriWrap(file.myParse(),
        1,
        isImage = true,
        beCopied = true)
}