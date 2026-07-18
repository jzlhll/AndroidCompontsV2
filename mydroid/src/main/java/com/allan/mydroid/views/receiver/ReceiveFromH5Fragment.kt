package com.allan.mydroid.views.receiver

import android.graphics.Color
import android.os.Bundle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.databinding.FragmentReceiveFromH5Binding
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.state.GlobalClientListFlowsObj
import com.allan.mydroid.state.GlobalServerRuntimeObj
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
import com.au.module_gson.toGsonString
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.ToolbarMenuManager
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_android.log.logdNoFile
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.unsafeLazy
import com.au.module_android.utilsmedia.getExternalFreeSpace
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class ReceiveFromH5Fragment : AbsLiveFragment<FragmentReceiveFromH5Binding>() {
    private val serverRuntimeState: GlobalServerRuntimeObj by inject()
    private val clientListState: GlobalClientListFlowsObj by inject()

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
                        Bundle().apply { putBoolean("isActivityMode", true) }
                    )
                }
            }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().changeBarsColor(statusBarTextDark = false)
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)

        menuMgr.showMenu()

        binding.adHost.setColor(Globals.getColor(com.au.module_androidcolor.R.color.color_normal_block0))
        binding.adHost.startAnimation()

        val fmt = getString(R.string.not_close_window)
        val leftStr = getString(R.string.storage_remaining)
        binding.descTitle.text = String.format(fmt, leftStr + getExternalFreeSpace(requireActivity()))

        launchRepeatOnStarted(get<GlobalNetworkMonitorObj>().networkInfoFlow) { netInfo->
            if (netInfo == null) {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            } else {
                if (netInfo.httpPort == null) {
                    binding.title.text = netInfo.ip
                } else if (serverRuntimeState.serverIsOpenFlow.value) {
                    binding.title.text = String.format(getString(R.string.lan_access_fmt), netInfo.ip, "" + netInfo.httpPort)
                } else {
                    binding.title.text = netInfo.ip + ":" + netInfo.httpPort
                }
            }
        }

        launchRepeatOnStarted(clientListState.clientListFlow) { clientList->
            logdNoFile {
                ">>client List:" + clientList.toGsonString()
            }
        }

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }
    }

    override fun getMode() = MyDroidMode.Receiver
}