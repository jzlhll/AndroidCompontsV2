package com.au.module_simplepermission.activity

import android.content.Context
import android.content.Intent
import android.hardware.Camera
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import com.au.module_simplepermission.IContractResult

/**
 * 当初始化完成这个对象后，请在onCreate里面调用 函数（onCreate）即可
 */
class SystemTakeVideoFaceForResult(owner:Any,
                                   isFront:Boolean = false,
                                   maxSec:Int = 60,
                                   isLowQuality:Boolean = true)
        : IContractResult<Uri, Boolean>(owner, CaptureFaceVideo(isFront, maxSec, isLowQuality)) {
    /**
     * 启动activity
     */
    override fun start(input:Uri, callback: ActivityResultCallback<Boolean>?) {
        callback?.let { setResultCallback(it) }
        launcher.launch(input)
    }
}

class CaptureFaceVideo(private val isFront:Boolean = false, private val maxSec:Int = 60, private val isLowQuality:Boolean = true)
        : ActivityResultContracts.CaptureVideo() {
    override fun createIntent(context: Context, input: Uri): Intent {
        val intent: Intent = super.createIntent(context, input)
        intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, maxSec)
        //intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, if(isLowQuality) 0.5f else 1f)
        if(isFront) {
            intent.putExtra("android.intent.extras.CAMERA_FACING", Camera.CameraInfo.CAMERA_FACING_FRONT)
            intent.putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
            intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
        }
        return intent
    }
}