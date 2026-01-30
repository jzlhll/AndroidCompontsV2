package com.allan.androidlearning.picwall

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.allan.androidlearning.databinding.FragmentPicWallBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.log.logdNoFile
import com.au.module_android.postToMainHandler
import com.au.module_android.simpleflow.collectStatusState
import com.au.module_android.utils.AndroidSBlurUtil
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.dp
import com.au.module_android.utils.dpFloat
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import kotlinx.coroutines.launch

@EntryFrgName(backgroundColor = "#ff00ff")
class PicWallFragment : BindingFragment<FragmentPicWallBinding>() {
    private val mViewModel by viewModels<PicWallViewModel>()
    
    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive()
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        requireActivity().changeBarsColor(false, false)
        binding.liquidGlassView.applyRenderEffectTo(binding.infiniteCanvasView)
        binding.liquidGlassView.setupFullCapsule()

        // 收集状态
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mViewModel.localMediaState.collectStatusState(
                    onSuccess = { data ->
                        // TODO: 处理成功数据
                        logdNoFile { "view model loaded data.size: ${data.size}" }
                        binding.infiniteCanvasView.setFrameImageList(data)
                        postToMainHandler {
                            AndroidSBlurUtil.applyBlurEffect(binding.liquidGlassView, 50.dpFloat, 50.dpFloat)
                        }
                    }
                )
            }
        }

        // 触发本地图片加载
        mViewModel.dispatch(PicWallViewModel.RequestLocalAction(requireActivity()))
    }

}