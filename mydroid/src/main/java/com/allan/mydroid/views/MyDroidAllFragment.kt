package com.allan.mydroid.views

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.CHECK_NEED_ALL_MANAGER
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.bt.BleIpScanner
import com.allan.mydroid.bt.DiscoveredHost
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.views.compose.MyDroidAllScreen
import com.allan.mydroid.views.compose.MyDroidAllUiState
import com.au.module_android.log.loge
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androiduiex.ui.ComposeViewFragment
import com.au.module_simplepermission.gotoMgrAll
import com.au.module_simplepermission.ifGotoMgrAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import org.koin.android.ext.android.get

class MyDroidAllFragment : ComposeViewFragment() {
    override var customBackActionEnable = false

    private var mIp: String? = null
    private var connectJob: Job? = null
    private val ipState = mutableStateOf<String?>(null)
    private val networkInitializedState = mutableStateOf(false)

    private val bleIpScanner = BleIpScanner(this)
    private val discoveredHostsState = mutableStateOf<List<DiscoveredHost>>(emptyList())
    private val scanningState = mutableStateOf(false)

    private fun runCheckIp(workBlock: () -> Unit) {
        if (!mIp.isNullOrEmpty()) {
            workBlock()
        } else {
            ToastBuilder().setMessage(getString(R.string.connect_wifi_or_hotspot))
                .setOnTop()
                .toast()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        launchRepeatOnStarted {
            get<GlobalNetworkMonitorObj>().networkFlow.collect { status ->
                when (status) {
                    is GlobalNetworkMonitorObj.NetworkStatus.Uninitialized -> {
                        networkInitializedState.value = false
                        mIp = null
                        ipState.value = null
                    }
                    is GlobalNetworkMonitorObj.NetworkStatus.Disconnected -> {
                        networkInitializedState.value = true
                        mIp = null
                        ipState.value = null
                    }
                    is GlobalNetworkMonitorObj.NetworkStatus.Connected -> {
                        networkInitializedState.value = true
                        mIp = status.ip
                        ipState.value = status.ip
                    }
                }
            }
        }

        launchRepeatOnStarted(bleIpScanner.discoveredFlow) { hosts ->
            discoveredHostsState.value = hosts
        }
        launchRepeatOnStarted(bleIpScanner.scanningFlow) { scanning ->
            scanningState.value = scanning
        }
    }

    @Composable
    override fun ScreenContent() {
        MyDroidAllScreen(
            uiState = MyDroidAllUiState(ipState.value, networkInitializedState.value),
            onReceiveFile = {
                runCheckIp {
                    findNavController().navigate(R.id.actionMyDroidAllToReceive)
                }
            },
            onSendFile = {
                if (CHECK_NEED_ALL_MANAGER) {
                    if (ifGotoMgrAll {
                            ConfirmCenterDialog.show(
                                childFragmentManager,
                                getString(R.string.app_management_permission),
                                getString(R.string.global_permission_prompt),
                                "OK"
                            ) {
                                gotoMgrAll(requireActivity())
                                it.dismissAllowingStateLoss()
                            }
                        }
                    ) {
                        runCheckIp {
                            findNavController().navigate(R.id.actionMyDroidAllToSendSelector)
                        }
                    }
                } else {
                    runCheckIp {
                        findNavController().navigate(R.id.actionMyDroidAllToSendSelector)
                    }
                }
            },
            onTextChat = {
                runCheckIp {
                    findNavController().navigate(R.id.actionMyDroidAllToTextChat)
                }
            },
            discoveredHosts = discoveredHostsState.value,
            scanning = scanningState.value,
            localIp = mIp,
            onStartSearch = { bleIpScanner.startScan() },
            onTransferRecords = { findNavController().navigate(R.id.localTransferRecordsFragment) },
            onIpClick = { host ->
                if (isSameSubnet(mIp, host.ip)) {
                    fetchModeAndNavigate(host)
                } else {
                    ConfirmCenterDialog.show(
                        childFragmentManager,
                        getString(R.string.tips),
                        getString(R.string.ip_subnet_mismatch_warning),
                        getString(R.string.action_confirm),
                    ) {
                        it.dismissAllowingStateLoss()
                        fetchModeAndNavigate(host)
                    }
                }
            },
        )
    }

    /** 前 3 段全相等视为同网段; 任一为 null 或段数不足 4 视为不一致。 */
    private fun isSameSubnet(localIp: String?, hostIp: String): Boolean {
        val local = localIp?.split('.')
        val remote = hostIp.split('.')
        if (local == null || local.size < 4 || remote.size < 4) return false
        return local[0] == remote[0] && local[1] == remote[1] && local[2] == remote[2]
    }

    /** 点击主机时重新核对模式；页面离开或重复点击不会产生迟到的导航。 */
    private fun fetchModeAndNavigate(host: DiscoveredHost) {
        if (connectJob?.isActive == true) return
        val owner = viewLifecycleOwner
        connectJob = owner.lifecycleScope.launch {
            try {
                val mode = ClientApi.fetchMode("http://${host.ip}:${host.port}")
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navigateToConnect(host, mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loge { "fetch mode failed: ${e.message}" }
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) showToastAndRescan()
            }
        }
    }

    private fun showToastAndRescan() {
        ToastBuilder()
            .setOnTop()
            .setMessage(getString(R.string.host_info_outdated_rescanning))
            .toast()
        bleIpScanner.startScan()
    }

    private fun navigateToConnect(host: DiscoveredHost, mode: MyDroidMode) {
        val args = Bundle().apply {
            putString("ip", host.ip)
            putInt("port", host.port)
            putInt("mode", mode.ordinal)
        }
        findNavController().navigate(R.id.actionMyDroidAllToConnectHost, args)
    }

    override fun onPause() {
        connectJob?.cancel()
        super.onPause()
        bleIpScanner.stopScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bleIpScanner.stopScan()
    }
}
