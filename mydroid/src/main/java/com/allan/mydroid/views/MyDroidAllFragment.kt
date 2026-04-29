package com.allan.mydroid.views

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.CHECK_NEED_ALL_MANAGER
import com.allan.mydroid.R
import com.allan.mydroid.databinding.FragmentMyDroidAllBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.allan.mydroid.views.chat.TextChatRoomFragment
import com.allan.mydroid.views.receiver.ReceiveFromH5Fragment
import com.allan.mydroid.views.receiver.nativefiles.ReceiveFromPhoneNativeFragment
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.allan.mydroid.views.send.SendListSelectorFragment.Companion.parseShareImportIntent
import com.au.module_android.click.onClick
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import com.au.module_simplepermission.gotoMgrAll
import com.au.module_simplepermission.ifGotoMgrAll
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MyDroidAllFragment : BindingFragment<FragmentMyDroidAllBinding>() {
    private var mIp:String? = null

    private fun uploadMyIp() {
        lifecycleScope.launch {
            val curIp = mIp
            if (!curIp.isNullOrEmpty()) {
                binding.title.text = curIp
            } else {
                binding.title.setText(R.string.connect_wifi_or_hotspot)
            }
        }
    }

    fun runCheckIp(workBlock:()->Unit) {
        if (!mIp.isNullOrEmpty()) {
            workBlock()
        } else {
            ToastBuilder().setMessage(getString(R.string.connect_wifi_or_hotspot))
                .setOnTop()
                .toast()
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        launchRepeatOnStarted{
            get<GlobalNetworkMonitor>().networkInfoFlow.collect {
                mIp = it?.ip
                uploadMyIp()
            }
        }

        binding.receiveFileLogicBtn.onClick {
            runCheckIp {
                FragmentShellActivity.start(requireActivity(), ReceiveFromH5Fragment::class.java)
            }
        }
        binding.nativeReceiveFileLogicBtn.onClick {
            runCheckIp {
                FragmentShellActivity.start(requireActivity(), ReceiveFromPhoneNativeFragment::class.java)
            }
        }
        binding.sendFileLogicBtn.onClick {
            if (CHECK_NEED_ALL_MANAGER) {
                if (ifGotoMgrAll {
                        ConfirmCenterDialog.show(childFragmentManager,
                            getString(R.string.app_management_permission),
                            getString(R.string.global_permission_prompt),
                            "OK") {
                            gotoMgrAll(requireActivity())
                            it.dismissAllowingStateLoss()
                        }
                    }) {
                    runCheckIp {
                        SendListSelectorFragment.start(requireActivity(), false)
                    }
                }
            } else {
                runCheckIp {
                    SendListSelectorFragment.start(requireActivity(), false)
                }
            }
        }
        binding.textChatLogicBtn.onClick {
            runCheckIp {
                FragmentShellActivity.start(requireActivity(), TextChatRoomFragment::class.java)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        parseShareImportIntent(this)
    }

    override fun toolbarInfo(): YourToolbarInfo? {
        return YourToolbarInfo.Defaults(getString(R.string.app_name))
    }
}