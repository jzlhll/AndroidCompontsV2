package com.allan.mydroid.views.transferserver

import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.databinding.FragmentMyDroidTransferServerBinding
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.globals.NetworkObserverObj
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_android.json.toJsonString
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.transparentStatusBar

class MyDroidTransferServerFragment : AbsLiveFragment<FragmentMyDroidTransferServerBinding>() {

    override fun isPaddingStatusBar() = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().transparentStatusBar(statusBarTextDark = false) { insets, statusBarsHeight, _ ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP->
                toolbarLP.topMargin = statusBarsHeight
                binding.toolbar.layoutParams = toolbarLP
            }
            insets
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)
        binding.adHost.setColor(Globals.getColor(com.au.module_androidcolor.R.color.color_normal_block0))
        binding.adHost.startAnimation()

        MyDroidConst.networkStatusData.observe(this) { netSt->
            if (netSt !is NetworkObserverObj.NetworkStatus.Connected) {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            } else {
                val info = netSt.ipInfo
                if (info.httpPort == null) {
                    binding.title.text = info.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.title.text = String.format(getString(R.string.lan_access_fmt), info.ip, "" + info.httpPort)
                } else {
                    binding.title.text = info.ip + ":" + info.httpPort
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