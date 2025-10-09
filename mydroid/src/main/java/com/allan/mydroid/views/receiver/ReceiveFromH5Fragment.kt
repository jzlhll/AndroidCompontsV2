package com.allan.mydroid.views.receiver

import android.graphics.Color
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import com.allan.mydroid.R
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.databinding.FragmentReceiveFromH5Binding
import com.allan.mydroid.globals.NetworkObserverObj
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_android.json.toJsonString
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.ToolbarMenuManager
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.logdNoFile
import com.au.module_android.utils.transparentStatusBar
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utilsmedia.getExternalFreeSpace

class ReceiveFromH5Fragment : AbsLiveFragment<FragmentReceiveFromH5Binding>() {

    private val menuMgr by unsafeLazy {
        ToolbarMenuManager(
            this, binding.toolbar,
            R.menu.menu_more,
            Color.WHITE
        ) { menuItem ->
            when (menuItem.itemId) {
                R.id.more -> {
                    FragmentShellActivity.start(requireActivity(),
                        ReceiveFromH5FileListFragment::class.java,
                        bundleOf("isActivityMode" to true)
                    )
                }
            }
        }
    }

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

        menuMgr.showMenu()

        binding.adHost.setColor(Globals.getColor(com.au.module_androidcolor.R.color.color_normal_block0))
        binding.adHost.startAnimation()

        val fmt = getString(R.string.not_close_window)
        val leftStr = getString(R.string.storage_remaining)
        binding.descTitle.text = String.format(fmt, leftStr + getExternalFreeSpace(requireActivity()))

        MyDroidConst.networkStatusData.observe(this) { netSt->
            if (netSt !is NetworkObserverObj.NetworkStatus.Connected) {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            } else {
                val ipInfo = netSt.ipInfo
                if (ipInfo.httpPort == null) {
                    binding.title.text = ipInfo.ip
                } else if (MyDroidConst.serverIsOpen) {
                    binding.title.text = String.format(getString(R.string.lan_access_fmt), ipInfo.ip, "" + ipInfo.httpPort)
                } else {
                    binding.title.text = ipInfo.ip + ":" + ipInfo.httpPort
                }
            }
        }

        MyDroidConst.clientListLiveData.observe(this) { clientList->
            logdNoFile {
                ">>client List:" + clientList.toJsonString()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }
    }

    override fun onStart() {
        MyDroidConst.currentDroidMode = MyDroidMode.Receiver
        super.onStart()
    }
}