package com.allan.mydroid.views.simpletext

import android.R.attr.orientation
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.mydroid.databinding.FragmentMyDroidSimpleTextBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidcolor.R
import org.koin.android.ext.android.get

class MyDroidSimpleTextFragment : AbsLiveFragment<FragmentMyDroidSimpleTextBinding>() {
    private lateinit var adapter: SimpleTextAdapter

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

        val fmt = getString(com.allan.mydroid.R.string.not_close_window)
        binding.descTitle.text = String.format(fmt, "")

        launchRepeatOnStarted(get<GlobalNetworkMonitor>().networkInfoFlow) { netInfo->
            if (netInfo == null) {
                binding.title.setText(com.allan.mydroid.R.string.connect_wifi_or_hotspot)
            } else {
                if (netInfo.httpPort == null) {
                    binding.title.text = netInfo.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.title.text = String.format(getString(com.allan.mydroid.R.string.lan_access_fmt), netInfo.ip, "" + netInfo.httpPort)
                } else {
                    binding.title.text = netInfo.ip + ":" + netInfo.httpPort
                }
            }
        }
        binding.rcv.adapter = SimpleTextAdapter().also { adapter = it }
        binding.rcv.layoutManager = LinearLayoutManager(requireContext()).apply {
            orientation = LinearLayoutManager.VERTICAL
        }
        binding.rcv.itemAnimator = null
    }
}