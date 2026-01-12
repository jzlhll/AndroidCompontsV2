package com.allan.mydroid.views.send

import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.FragmentSendFilesBinding
import com.allan.mydroid.databinding.MydroidSendClientBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.utils.BlurViewEx
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_android.click.onClick
import com.au.module_android.glide.glideSetAny
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchOnThread
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_android.log.logd
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utils.visible
import com.au.module_android.utilsmedia.ExtensionMimeUtil
import com.au.module_androidcolor.R
import com.bumptech.glide.request.target.Target
import org.koin.android.ext.android.get

class SendListFilesFragment : AbsLiveFragment<FragmentSendFilesBinding>() {
    private val common by unsafeLazy {
        object : SendListSelectorCommon(true) {
            override fun rcv() = binding.rcv
            override fun empty() = null
            override fun onItemClick(bean: ShareInBean?, mode: String) {
                if (mode == CLICK_MODE_ROOT && bean != null) {
                    logd { "click on icon $bean" }

                    val isImg = ExtensionMimeUtil.isUriImage(bean.mimeType)
                    val isVideo = ExtensionMimeUtil.isUriVideo(bean.mimeType)
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

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive { statusBarsHeight, navBarHeight ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP->
                toolbarLP.topMargin = statusBarsHeight
                binding.toolbar.layoutParams = toolbarLP
            }
            binding.root.updatePadding(bottom = navBarHeight)
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)

        binding.adHost.setColor(Globals.getColor(R.color.color_normal_block0))
        binding.adHost.startAnimation()

        requireActivity().changeBarsColor(statusBarTextDark = false)

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }

        clientLiveDataInit()

        val fmt = getString(com.allan.mydroid.R.string.not_close_window)
        binding.descTitle.text = String.format(fmt, "")

        launchRepeatOnStarted(get<GlobalNetworkMonitor>().networkInfoFlow) { netInfo->
            if (netInfo == null) {
                binding.descTitle.setText(com.allan.mydroid.R.string.connect_wifi_or_hotspot)
            } else {
                val fmt = getString(com.allan.mydroid.R.string.not_close_window)
                if (netInfo.httpPort == null) {
                    binding.descTitle.text = netInfo.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.descTitle.text = String.format(getString(com.allan.mydroid.R.string.lan_access_fmt), netInfo.ip, "" + netInfo.httpPort)
                } else {
                    binding.descTitle.text = String.format(fmt, netInfo.ip + ":" + netInfo.httpPort)
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