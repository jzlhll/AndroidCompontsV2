package com.au.module_imagecompressed

import android.net.Uri
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.au.module_android.Globals
import com.au.module_android.log.logdNoFile
import com.au.module_imagecompressed.compressor.BestImageCompressor
import com.au.module_imagecompressed.compressor.useCompress
import com.au.module_simplepermission.BaseCameraPermissionHelp
import com.au.module_simplepermission.ICameraFileProviderSupply
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                           callback: (mode:String, uri: Uri?)->Unit) : Boolean{
        val ret = safeRunTakePic({createdTmpFile->
            if (createdTmpFile != null) {
                if (compress) {
                    Globals.backgroundScope.launch {
                        logdNoFile { "createdTempFile $createdTmpFile size: ${createdTmpFile.length()}" }
                        val compressedFile = useCompress(realActivity, createdTmpFile.toUri(), BestImageCompressor.Config(qualityType = qualityType))
                        logdNoFile { "compressedFile $compressedFile size: ${compressedFile?.length()}" }
                        if (compressedFile != null) {
                            withContext(Dispatchers.Main) {
                                val cvtUri = createdTmpFile.toUri()
                                callback("takePicAndCompressed", cvtUri)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                val cvtUri = createdTmpFile.toUri()
                                callback("takePicAndCompressFailUseOrig", cvtUri)
                            }
                        }
                    }
                } else {
                    val cvtUri = createdTmpFile.toUri()
                    callback("takePicResultDirect", cvtUri)
                }
            } else {
                callback("takePicNoResult", null)
            }
            null
        }, notGivePermissionBlock = {
            callback("notGivePermission", null)
        })
        if (ret == -2) {
            callback("permissionRejectDirect", null)
        }

        return ret == -2
    }
}