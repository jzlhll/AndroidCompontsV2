package com.allan.androidlearning.picwall

import android.R.attr.direction
import android.os.Bundle
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.androidlearning.databinding.FragmentPicWallBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.log.logdNoFile
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.dp
import com.au.module_androidui.toast.ToastUtil
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_simplepermission.PermissionMediaType
import com.au.module_simplepermission.createMediaPermissionForResult
import eightbitlab.com.blurview.BlurView
import kotlinx.coroutines.launch

@EntryFrgName
class PicWallFragment : BindingFragment<FragmentPicWallBinding>() {
    private val mViewModel by viewModels<PicWallViewModel>()

    private val mediaUtil = createMediaPermissionForResult(arrayOf(PermissionMediaType.IMAGE, PermissionMediaType.VIDEO, PermissionMediaType.AUDIO))

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive()
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        requireActivity().changeBarsColor(false, false)

//        测试了：blurView，圆角，模糊半径，过时的替代颜色
//        BlurViewEx3(binding.blurView, 16, 3f).setBlur(binding.blurTarget, "#ccffffff".toColorInt())
//        BlurViewEx3(binding.blurView2, 0, 12f).setBlur(binding.blurTarget, "#ccffffff".toColorInt())

        //测试了：渐变模糊，applyNoise推荐false，否则会有明显的分界线
        BlurViewEx3(binding.blurView, 0, 16f).setProgressiveBlur(binding.blurTarget,  BlurView.GRADIENT_TOP_TO_BOTTOM,
            applyNoise = false,
            "#ccffffff".toColorInt(), "#00ffffff".toColorInt()) //good
        BlurViewEx3(binding.blurView2, 0, 16f).setProgressiveBlur(binding.blurTarget, BlurView.GRADIENT_BOTTOM_TO_TOP,
            applyNoise = true,
            "#ccffffff".toColorInt(), "#00ffffff".toColorInt()) //一般

        // 收集状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.localMediaState.collectStatusState(
                    onSuccess = { data ->
                        // TODO: 处理成功数据
                        logdNoFile { "view model loaded data.size: ${data.size}" }
                        binding.infiniteCanvasView.setFrameImageList(data)
                    }
                )
            }
        }

        mediaUtil.safeRun(notGivePermissionBlock = {
            ToastUtil.toastOnTop("请先授权媒体权限")
        }) {
            // 触发本地图片加载
            mViewModel.dispatch(PicWallViewModel.RequestLocalAction(requireActivity()))
        }
    }

}