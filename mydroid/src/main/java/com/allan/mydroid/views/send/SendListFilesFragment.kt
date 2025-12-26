package com.allan.mydroid.views.send

import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.FragmentSendFilesBinding
import com.allan.mydroid.databinding.MydroidSendClientBinding
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.NetworkObserverObj
import com.allan.mydroid.utils.BlurViewEx
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_android.ui.base.ImmersiveMode
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.logd
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.transparentStatusBar
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_android.utilsmedia.MediaTypeUtil
import com.au.module_androidcolor.R
import com.bumptech.glide.request.target.Target

class SendListFilesFragment : AbsLiveFragment<FragmentSendFilesBinding>() {
    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.PaddingNavigationBar
    }

    private val common by unsafeLazy {
        object : SendListSelectorCommon(true) {
            override fun rcv() = binding.rcv
            override fun empty() = null
            override fun onItemClick(bean: ShareInBean?, mode: String) {
                if (mode == CLICK_MODE_ROOT && bean != null) {
                    logd { "click on icon $bean" }

                    val isImg = MediaTypeUtil.isUriImage(bean.mimeType)
                    val isVideo = MediaTypeUtil.isUriVideo(bean.mimeType)
                    if (isImg || isVideo) {
                        showBigIcon(bean, isVideo)
                    }
                }
            }
        }
    }

    private var hasSetBigImageClick = false

    private fun showBigIcon(bean: ShareInBean, isVideo: Boolean) {
        if (!hasSetBigImageClick) {
            hasSetBigImageClick = true
            binding.blurView.onClick {
                binding.blurView.gone()
                binding.bigImage.gone()
                binding.iconPlay.gone()
            }
            binding.bigImage.setImageDrawable(null)
            BlurViewEx(binding.blurView, 0).setBlur(binding.root, 96f)
        }

        binding.blurView.visible()
        binding.bigImage.visible()
        if (isVideo) {
            binding.iconPlay.visible()
        } else {
            binding.iconPlay.gone()
        }

        binding.bigImage.glideSetAny(bean.uri) {
            it.override(Target.SIZE_ORIGINAL)
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)

        binding.adHost.setColor(Globals.getColor(R.color.color_normal_block0))
        binding.adHost.startAnimation()

        requireActivity().transparentStatusBar(statusBarTextDark = false) { insets, statusBarsHeight, _ ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP->
                toolbarLP.topMargin = statusBarsHeight
                binding.toolbar.layoutParams = toolbarLP
            }
            insets
        }

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }

        clientLiveDataInit()

        val fmt = getString(com.allan.mydroid.R.string.not_close_window)
        binding.descTitle.text = String.format(fmt, "")

        MyDroidConst.networkStatusData.observe(this) { netSt->
            if (netSt !is NetworkObserverObj.NetworkStatus.Connected) {
                binding.descTitle.setText(com.allan.mydroid.R.string.connect_wifi_or_hotspot)
            } else {
                val fmt = getString(com.allan.mydroid.R.string.not_close_window)
                val info = netSt.ipInfo
                if (info.httpPort == null) {
                    binding.descTitle.text = info.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.descTitle.text = String.format(getString(com.allan.mydroid.R.string.lan_access_fmt), info.ip, "" + info.httpPort)
                } else {
                    binding.descTitle.text = String.format(fmt, info.ip + ":" + info.httpPort)
                }
            }
        }

        common.onCreated()
    }

    private fun clientLiveDataInit() {
        MyDroidConst.clientListLiveData.observe(this) { clientList ->
            for (clientBinding in sendClientBindings) {
                clientBinding.root.gone()
            }

            clientList.forEachIndexed { index, clientInfo ->
                logdNoFile { "client List[$index] = $clientInfo" }
                val item = clientItem(index)
                item.title.text = clientInfo.clientName
                item.icon.background = ViewBackgroundBuilder()
                    .setBackground(clientInfo.color.toColorInt())
                    .setCornerRadius(32f.dp)
                    .build()
                if (!item.root.isAttachedToWindow) {
                    binding.clientsHost.addView(item.root)
                }
                item.root.visible()
            }
        }
    }

    override fun onStart() {
        MyDroidConst.currentDroidMode = MyDroidMode.Send
        super.onStart()
        lifecycleScope.launchOnThread {
            common.reload()
        }
    }

    private val sendClientBindings = mutableListOf<MydroidSendClientBinding>()
    private fun clientItem(index:Int) : MydroidSendClientBinding {
        var binding = sendClientBindings.getOrNull(index)
        if (binding != null) {
            return binding
        }

        binding = MydroidSendClientBinding.inflate(layoutInflater)
        sendClientBindings.add(binding)
        return binding
    }
}