package com.au.module_imagecompressed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.core.net.toUri
import androidx.core.view.updatePadding
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.log.logdNoFile
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.databinding.CropSquareLayoutBinding
import com.au.module_simplepermission.activity.ActivityForResult
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCrop.EXTRA_OUTPUT_URI
import com.yalantis.ucrop.UCropActivity
import com.yalantis.ucrop.UCropFragment
import com.yalantis.ucrop.UCropFragmentCallback
import java.io.File

/**
 * 方形图片裁剪页。
 */
class CropSquareImageFragment : BindingFragment<CropSquareLayoutBinding>(), UCropFragmentCallback {
    companion object {
        /**
         * 裁剪缓存目录名。
         */
        const val DIR_CROP = "ucrop"

        /**
         * 裁剪结果 Uri 的 Intent key。
         */
        const val RESULT_KEY_CROPPED_IMAGE = EXTRA_OUTPUT_URI

        /**
         * 裁剪成功结果码。
         */
        const val RESULT_OK = Activity.RESULT_OK

        /**
         * 裁剪失败结果码。
         */
        const val RESULT_ERROR = UCrop.RESULT_ERROR

        private const val KEY_SRC_URI = "srcUri"

        /**
         * 启动方形图片裁剪页并通过 ActivityResult 回传结果。
         */
        fun startCropForResult(
            context: Context,
            activityResult: ActivityForResult,
            srcUri: Uri,
            activityResultCallback: ActivityResultCallback<ActivityResult>
        ) {
            val bundle = Bundle().apply {
                putString(KEY_SRC_URI, srcUri.toString())
            }

            FragmentShellActivity.start(
                context,
                CropSquareImageFragment::class.java,
                arguments = bundle,
                activityResult = activityResult,
                activityResultCallback = activityResultCallback
            )
        }
    }

    private var uCropFragment: UCropFragment? = null

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive { statusBarHeight, _ ->
            binding.root.updatePadding(top = statusBarHeight)
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.cancelTv.onClick {
            requireActivity().finishAfterTransition()
        }
        binding.usePhotoTv.onClick {
            uCropFragment?.cropAndSaveImage()
        }

        binding.fcvHost.post {
            binding.fcvHost.layoutParams = binding.fcvHost.layoutParams.also {
                it.height = binding.fcvHost.width
            }
            replaceUcropFragment()
        }
    }

    private fun replaceUcropFragment() {
        val dir = File(Globals.goodCacheDir, DIR_CROP)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val destFile = File(dir, "tmp" + System.currentTimeMillis() + ".jpg")
        if (!destFile.exists()) {
            destFile.createNewFile()
        }
        val destUri = Uri.fromFile(destFile)
        val srcUri = requireArguments().getString(KEY_SRC_URI)!!
        val uCrop = UCrop.of(
            srcUri.toUri(),
            destUri,
            arrayListOf(srcUri)
        )
        uCrop.withOptions(UCrop.Options().also {
            it.withAspectRatio(1f, 1f)
            it.setHideBottomControls(true)
            it.setCompressionQuality(85)
            it.setShowCropFrame(false)
            it.setShowCropGrid(false)
            it.isCropDragSmoothToCenter(false)
            it.isDragCropImages(true)
            it.setAllowedGestures(UCropActivity.SCALE, UCropActivity.NONE, UCropActivity.SCALE)
            it.setRootViewBackgroundColor(Color.BLACK)
        })
        val uCropFragment = uCrop.fragment
        this.uCropFragment = uCropFragment
        childFragmentManager.beginTransaction()
            .replace(binding.fcv.id, uCropFragment)
            .commitNow()
        // uCrop 默认留 16dp 内边距，这里让裁剪区域贴满外层 20dp 方形框。
        uCropFragment.view?.findViewById<View>(com.yalantis.ucrop.R.id.image_view_crop)?.setPadding(0, 0, 0, 0)
        uCropFragment.view?.findViewById<View>(com.yalantis.ucrop.R.id.view_overlay)?.setPadding(0, 0, 0, 0)
    }

    override fun loadingProgress(showLoader: Boolean) {
        logdNoFile { "loading progress $showLoader" }
    }

    override fun onCropFinish(result: UCropFragment.UCropResult) {
        logdNoFile { "on crop finish ${result.mResultCode} ${result.mResultData}" }
        if (result.mResultCode == UCrop.RESULT_ERROR) {
            requireActivity().setResult(RESULT_ERROR, Intent())
        } else {
            requireActivity().setResult(RESULT_OK, result.mResultData)
        }
        requireActivity().finishAfterTransition()
    }
}
