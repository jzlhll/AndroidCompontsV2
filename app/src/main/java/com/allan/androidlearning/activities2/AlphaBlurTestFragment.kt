package com.allan.androidlearning.activities2

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import com.allan.androidlearning.databinding.ActivityAlphaBlurLayoutBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_android.utils.AndroidSBlurUtil
import com.au.module_android.utils.AndroidSGradientBlurUtil
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible

@EntryFrgName
class AlphaBlurTestFragment : BindingFragment<ActivityAlphaBlurLayoutBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        binding.showBlurBtn.onClick {
            binding.hideIt.visible()
            // 在 Activity 或 Fragment 中
            AndroidSBlurUtil.applyBlurEffect(binding.bottomLL)
        }
        binding.showGradientBlurBtn.onClick {
            binding.hideIt.visible()
            AndroidSGradientBlurUtil.applyGradientBlurEffect(binding.bottomLL, GradientDrawable.Orientation.TOP_BOTTOM)
        }
        binding.hideIt.onClick {
            binding.hideIt.gone()
            // 取消模糊效果
            AndroidSBlurUtil.clearBlurEffect(binding.bottomLL)
            AndroidSGradientBlurUtil.clearBlurEffect(binding.bottomLL)
        }
    }


}