package com.au.module_imagecompressed

import android.content.Context
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.au.module_android.utils.ignoreError
import com.au.module_android.utilsmedia.myParse
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_simplepermission.BaseCameraPermissionHelp
import com.au.module_simplepermission.ICameraFileProviderSupply
import java.io.File

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
    fun safeRunTakePicMust(context: Context,
                                   needLubanCompress:Boolean = true,
                                   errorToastBlock:()->Unit = {ToastBuilder().setOnTop().setIcon("info").setMessage("需要camera权限.").toast() },
                                   callback: (mode:String, uriWrap: PickUriWrap?)->Unit) : Boolean{
        val ret = safeRunTakePic({createdTmpFile->
            if (createdTmpFile != null) {
                val createdTmpUri = createdTmpFile.toUri()
                if (needLubanCompress) {
                    //luban压缩
                    LubanCompress().setResultCallback { srcPath, resultPath, isSuc ->
                        val afterCompressPath = if(isSuc) resultPath else srcPath
                        if (afterCompressPath != null) {
                            ignoreError {
                                val afterCompressFile = File(afterCompressPath)
                                val cvtUri = imageFileConvertToWrap(afterCompressFile)
                                callback("takePicResultLubanCompressed", cvtUri)
                            }
                        } else {
                            callback("takePicResultLubanCompressedError", null)
                        }
                    }.compress(context, createdTmpUri) //必须是file的scheme。那个FileProvider提供的则不行。
                } else {
                    //不压缩
                    val cvtUri = imageFileConvertToWrap(createdTmpFile)
                    callback("takePicResultDirect", cvtUri)
                }
            } else {
                callback("takePicNoResult", null)
            }
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
        beLimitedSize = false,
        beCopied = true)
}