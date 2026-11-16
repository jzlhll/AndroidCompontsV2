package com.au.module_imagecompressed

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RelativeLayout
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.log.logd
import com.au.module_android.utils.getScreenFullSize
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_imagecompressed.databinding.CropCircleLayoutBinding
import com.au.module_simplepermission.activity.ActivityForResult
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCrop.EXTRA_OUTPUT_URI
import com.yalantis.ucrop.UCropFragment
import com.yalantis.ucrop.UCropFragmentCallback
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/** 圆形图片裁剪页，初始化与裁剪回调随当前 View 生命周期结束。 */
class CropCircleImageFragment : BindingFragment<CropCircleLayoutBinding>(), UCropFragmentCallback {
    companion object {
        const val DIR_CROP = "ucrop"
        const val RESULT_KEY_CROPPED_IMAGE = EXTRA_OUTPUT_URI

        const val RESULT_OK = 0
        const val RESULT_ERROR = -1

        fun startCropForResult(
            context: Context,
            activityResult: ActivityForResult,
            srcUri: Uri,
            activityResultCallback: ActivityResultCallback<ActivityResult>
        ) {
            // 1、构造可用的裁剪数据源
            val bundle = Bundle().apply {
                this.putString("srcUri", srcUri.toString())
            }

            FragmentShellActivity.start(context,
                CropCircleImageFragment::class.java,
                arguments = bundle,
                activityResult = activityResult,
                activityResultCallback = activityResultCallback)
        }
    }

    private var uCropFragment:UCropFragment? = null

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.back.onClick {
            requireActivity().finishAfterTransition()
        }
        binding.saveBtn.onClick {
            uCropFragment?.cropAndSaveImage()
        }

        val screenSize = requireActivity().getScreenFullSize()
        if (screenSize.second.toFloat() / screenSize.first < 1.5f) {
            binding.saveBtn.gone()
            binding.save2Btn.visible()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val owner = viewLifecycleOwner
        val container = binding.fcv
        owner.lifecycleScope.launch {
            // 保留布局后的初始化时机，View 销毁时同时移除尚未执行的回调。
            suspendCancellableCoroutine<Unit> { continuation ->
                val callback = Runnable { continuation.resume(Unit) }
                container.post(callback)
                continuation.invokeOnCancellation { container.removeCallbacks(callback) }
            }
            // 页面进入后台或已保存状态时等待恢复，不提交过期的 Fragment 事务。
            owner.lifecycle.withResumed {
                resizeScreen()
                replaceUcropFragment()
            }
        }
    }

    override fun onDestroyView() {
        // UCrop 的异步任务会直接调用 callback，不能置 null；隔离旧实例的迟到结果。
        uCropFragment?.setCallback(object : UCropFragmentCallback {
            override fun loadingProgress(showLoader: Boolean) {}
            override fun onCropFinish(result: UCropFragment.UCropResult) {}
        })
        uCropFragment = null
        super.onDestroyView()
    }

    private fun resizeScreen() {
        if (binding.fcvHost.width > binding.fcvHost.height) {
            val oneSideDelta = (binding.fcvHost.width - binding.fcvHost.height) / 2
            binding.fcvHost.layoutParams = binding.fcvHost.layoutParams.also {
                it as RelativeLayout.LayoutParams
                it.marginStart = oneSideDelta
                it.marginEnd = oneSideDelta
            }
        }
    }

    private fun replaceUcropFragment() {
        val dir = File(Globals.goodCacheDir.absolutePath + "/ucrop")
        if (!dir.exists()) {
            dir.mkdir()
        }
        val destFile = File(Globals.goodCacheDir.absolutePath + "/ucrop", "tmp" + System.currentTimeMillis() + ".png")
        if (!destFile.exists()) {
            destFile.createNewFile()
        }
        val destUri = Uri.fromFile(destFile)
        val srcUri = requireArguments().getString("srcUri")!!
        val arrayList = ArrayList<String>()
        srcUri.let { arrayList.add(it) }
        val uCrop : UCrop = UCrop.of(
            srcUri.toUri(),
            destUri,
            arrayList
        )
        //uCrop.setImageEngine(MyUCropImageEngine())
        uCrop.withOptions(UCrop.Options().also {
            it.withAspectRatio(1f, 1f)
            it.setCircleDimmedLayer(true)
            it.setHideBottomControls(true)
            it.setCompressionQuality(85)
            it.setShowCropFrame(true)
            //it.setToolbarTitleSize(20)
            it.setShowCropGrid(true)
            it.setRootViewBackgroundColor(requireContext().getColor(com.au.module_androidcolor.R.color.windowBackground))
            //it.setToolbarTitle("Crop") //later: String
        })
        val uCropFragment = uCrop.fragment
        this.uCropFragment = uCropFragment
        childFragmentManager.beginTransaction()
            .replace(binding.fcv.id, uCropFragment)
            .commit()
    }

    override fun loadingProgress(showLoader: Boolean) {
        logd { "loading progress $showLoader" }
    }

    override fun onCropFinish(result: UCropFragment.UCropResult) {
        if (view == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return
        val host = activity ?: return
        if (host.isFinishing || host.isDestroyed) return
        logd { "on crop finish ${result.mResultCode} ${result.mResultData}" }
        if (result.mResultCode == UCrop.RESULT_ERROR) {
            host.setResult(RESULT_ERROR, Intent())
        } else {
            host.setResult(RESULT_OK, result.mResultData)
        }
        host.finishAfterTransition()
    }
}
