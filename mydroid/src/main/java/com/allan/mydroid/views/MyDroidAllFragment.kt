package com.allan.mydroid.views

import android.os.Bundle
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.CHECK_NEED_ALL_MANAGER
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.bt.BleIpScanner
import com.allan.mydroid.bt.DiscoveredHost
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.views.chat.TextChatRoomFragment
import com.allan.mydroid.client.ConnectToHostFragment
import com.allan.mydroid.views.compose.MyDroidAllScreen
import com.allan.mydroid.views.compose.MyDroidAllUiState
import com.allan.mydroid.views.receiver.ReceiveFromH5Fragment
import com.allan.mydroid.views.send.SendListSelectorFragment
import com.allan.mydroid.views.send.SendListSelectorFragment.Companion.parseShareImportIntent
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.dialogs.ConfirmCenterDialog
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androiduiex.ui.ComposeViewFragment
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_simplepermission.gotoMgrAll
import com.au.module_simplepermission.ifGotoMgrAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get

class MyDroidAllFragment : ComposeViewFragment() {
    private var mIp: String? = null
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
                    FragmentShellActivity.start(requireActivity(), ReceiveFromH5Fragment::class.java)
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
                            SendListSelectorFragment.start(requireActivity(), false)
                        }
                    }
                } else {
                    runCheckIp {
                        SendListSelectorFragment.start(requireActivity(), false)
                    }
                }
            },
            onTextChat = {
                runCheckIp {
                    FragmentShellActivity.start(requireActivity(), TextChatRoomFragment::class.java)
                }
            },
            discoveredHosts = discoveredHostsState.value,
            scanning = scanningState.value,
            localIp = mIp,
            onStartSearch = { bleIpScanner.startScan() },
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

    /**
     * 点击 host 后先 GET /get-mode 拿到 host 当前模式，再进入 ConnectToHostFragment。
     * 请求失败时触发一次重扫 + toastOnTop 提示信息可能过期。
     * 异步回来后判断 isAdded，避免 Fragment 已 detach 后调用 requireActivity() 崩溃。
     */
    private fun fetchModeAndNavigate(host: DiscoveredHost) {
        viewLifecycleOwner.lifecycleScope.launchOnIOThread {
            val baseUrl = "http://${host.ip}:${host.port}"
            val mode = try {
                ClientApi.fetchMode(baseUrl)
            } catch (e: Exception) {
                loge { "fetchMode failed: ${e.message}" }
                withContext(Dispatchers.Main) { if (isAdded) showToastAndRescan() }
                return@launchOnIOThread
            }
            withContext(Dispatchers.Main) { if (isAdded) navigateToConnect(host, mode) }
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
        FragmentShellActivity.start(requireActivity(), ConnectToHostFragment::class.java, args)
    }

    override fun onResume() {
        super.onResume()
        parseShareImportIntent(this)
    }

    override fun onPause() {
        super.onPause()
        bleIpScanner.stopScan()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bleIpScanner.stopScan()
    }
}
