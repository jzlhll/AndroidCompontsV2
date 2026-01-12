package com.allan.mydroid.views.transferserver

import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.databinding.FragmentMyDroidTransferServerBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_gson.toJsonString
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.changeBarsColor
import org.koin.android.ext.android.get

class MyDroidTransferServerFragment : AbsLiveFragment<FragmentMyDroidTransferServerBinding>() {

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive { statusBarHeight, navBarHeight ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP->
                toolbarLP.topMargin = statusBarHeight
                binding.toolbar.layoutParams = toolbarLP
            }

            binding.root.updatePadding(bottom = navBarHeight)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().changeBarsColor(statusBarTextDark = false)
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)
        binding.adHost.setColor(Globals.getColor(com.au.module_androidcolor.R.color.color_normal_block0))
        binding.adHost.startAnimation()

        launchRepeatOnStarted(get<GlobalNetworkMonitor>().networkInfoFlow) { netInfo->
            if (netInfo == null) {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            } else {
                if (netInfo.httpPort == null) {
                    binding.title.text = netInfo.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.title.text = String.format(getString(R.string.lan_access_fmt), netInfo.ip, "" + netInfo.httpPort)
                } else {
                    binding.title.text = netInfo.ip + ":" + netInfo.httpPort
                }
            }
        }

        MyDroidConst.clientListLiveData.observe(this) { clientList->
            logdNoFile {
                ">>client List:" + clientList.toJsonString()
            }
        }

        initLater()
    }

    override fun onStart() {
        MyDroidConst.currentDroidMode = MyDroidMode.Middle
        super.onStart()
    }

    private fun initLater() {
        Globals.mainHandler.post {
            binding.toolbar.setNavigationOnClickListener {
                requireActivity().finishAfterTransition()
            }
        }
    }

}