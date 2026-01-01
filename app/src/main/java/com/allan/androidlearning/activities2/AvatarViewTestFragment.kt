package com.allan.androidlearning.activities2

import android.os.Bundle
import com.allan.androidlearning.databinding.FragmentAvatarViewTestBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.glide.glideSetAny
import com.au.module_androidui.ui.bindings.BindingFragment

@EntryFrgName
class AvatarViewTestFragment : BindingFragment<FragmentAvatarViewTestBinding>() {
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)
        binding.avatarCodeText.setAvatarText("B")
        binding.avatarCodeSrc.setAvatarResource(com.au.module_androidcolor.R.drawable.icon_eye)
        binding.avatarCodeGlide.avatarImage.glideSetAny("https://profile-avatar.csdnimg.cn/default.jpg!1")
    }
}