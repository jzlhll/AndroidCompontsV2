package com.allan.mydroid.views

import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewbinding.ViewBinding
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.bt.BleIpAdvertiser
import com.allan.mydroid.globals.GlobalDroidServerObj
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.au.module_android.Globals
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.dialogs.ConfirmBottomSingleDialog
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

abstract class AbsLiveFragment<VB: ViewBinding> : BindingFragment<VB>() {
    override var customBackActionEnable = false


    /** 子类返回当前 live 服务模式。 */
    abstract fun getMode(): MyDroidMode
    companion object {
        fun showExitDialogLater() {
            Globals.mainHandler.postDelayed({
                Globals.topActivity.asOrNull<AppCompatActivity>()?.let { ac->
                    ConfirmBottomSingleDialog.Companion.show(ac.supportFragmentManager,
                        ac.getString(R.string.tips),
                        ac.getString(R.string.inactivity_message),
                        ac.getString(R.string.action_confirm),
                    ) {
                        it.dismissAllowingStateLoss()
                    }
                }
            }, 1500)
        }
    }

    /**
     * host 端 BLE IP 广播辅助类。BtPermissionHelp 必须在 Fragment 成员变量初始化阶段构造,
     * 这里随 AbsLiveFragment 成员变量初始化一并完成。三个对等子页面共用。
     */
    protected val bleIpAdvertiser = BleIpAdvertiser(this)

    /** 子类可关闭广播(默认开); 当前三个子类都是 host 端, 默认 true。 */
    protected open val shouldAdvertiseIp: Boolean = true

    val whenIpNullShowExitDialog: Boolean = true
    val autoExistLongTimeInActive: Boolean = true

    var waitDialog:ConfirmBottomSingleDialog? = null

    private val networkMonitor : GlobalNetworkMonitorObj by inject()
    private val globalDroidServer : GlobalDroidServerObj by inject()
    private val livePageToken = Any()

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        globalDroidServer.enterLivePage(livePageToken, getMode())
    }

    @CallSuper
    override fun onBindingCreated(savedInstanceState: Bundle?) {
        if (autoExistLongTimeInActive) {
            launchRepeatOnStarted {
                globalDroidServer.aliveStoppedFlow.collect {
                    findNavController().popBackStack()
                    showExitDialogLater()
                }
            }
        }

        if (whenIpNullShowExitDialog) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    networkMonitor.networkInfoFlow.collect { networkInfo->
                        val ip = networkInfo?.ip
                        if (ip.isNullOrEmpty()) {
                            if (waitDialog == null) {
                                ConfirmBottomSingleDialog.show(childFragmentManager, getString(R.string.tips),
                                    getString(R.string.exit_with_wifi_reminder),
                                    "OK",
                                    true) { d->
                                    waitDialog?.dismissAllowingStateLoss()
                                    waitDialog = null
                                    findNavController().popBackStack()
                                }.also { d->
                                    d.isCancelable = false
                                    waitDialog = d
                                }
                            }
                        } else {
                            waitDialog?.dismissAllowingStateLoss()
                            waitDialog = null
                        }
                    }
                }
            }
        }

        if (shouldAdvertiseIp) {
            bleIpAdvertiser.start()
        }
    }

    @CallSuper
    override fun onDestroyView() {
        super.onDestroyView()
        if (shouldAdvertiseIp) {
            bleIpAdvertiser.stop()
        }
    }

    final override fun onDestroy() {
        globalDroidServer.leaveLivePage(livePageToken)
        super.onDestroy()
    }
}
