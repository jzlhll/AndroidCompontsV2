package com.allan.mydroid.client

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.client.api.ClientApi
import com.allan.mydroid.client.chat.ConnectToHostChatScreen
import com.allan.mydroid.client.chat.ConnectToHostChatViewModel
import com.allan.mydroid.client.receive.ConnectToHostReceiveScreen
import com.allan.mydroid.client.receive.ConnectToHostReceiveViewModel
import com.allan.mydroid.client.send.ConnectToHostSendScreen
import com.allan.mydroid.client.send.ConnectToHostSendViewModel
import com.au.module_android.log.loge
import com.au.module_android.utils.launchOnIOThread
import com.au.module_androidui.dialogs.ConfirmBottomSingleDialog
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.ui.ComposeViewFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/**
 * client 端点击已发现 host 后进入的页面。按 Bundle 中 mode 路由到三模式 Compose Screen：
 * - mode Receiver → ConnectToHostSendScreen（client 发文件到 host）
 * - mode Send → ConnectToHostReceiveScreen（client 从 host 下载文件）
 * - mode TextChat → ConnectToHostChatScreen（client 与 host 聊天）
 *
 * 异步获取 wsPort 后构造 HostEndpoint；KEEP_SCREEN_ON；3 分钟无操作自动退出。
 *
 * 入参: Bundle 中带 `ip`(String) / `port`(Int httpPort) / `mode`(Int ordinal)。
 */
class ConnectToHostFragment : ComposeViewFragment() {

    private val ip by lazy { arguments?.getString("ip") ?: "" }
    private val httpPort by lazy { arguments?.getInt("port") ?: 0 }
    private val modeOrdinal by lazy { arguments?.getInt("mode") ?: 0 }

    private var endpointState by mutableStateOf<HostEndpoint?>(null)
    private var errorState by mutableStateOf<Throwable?>(null)

    private var inactivityJob: Job? = null
    private var exitDialog: ConfirmBottomSingleDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            requireActivity().setTurnScreenOn(true)
        }
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fetchEndpoint()
    }

    private fun fetchEndpoint() {
        viewLifecycleOwner.lifecycleScope.launchOnIOThread {
            try {
                val baseUrl = "http://$ip:$httpPort"
                val result = ClientApi.fetchWsIpPort(baseUrl)
                val mode = MyDroidMode.entries.getOrElse(modeOrdinal) { MyDroidMode.None }
                endpointState = HostEndpoint(ip, httpPort, result.port, mode)
                startInactivityTimer()
            } catch (e: Exception) {
                loge { "fetchWsIpPort failed: ${e.message}" }
                errorState = e
            }
        }
    }

    private fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(INACTIVITY_TIMEOUT)
            showInactivityDialog()
        }
    }

    private fun resetInactivityTimer() {
        if (endpointState != null) {
            startInactivityTimer()
        }
    }

    private fun showInactivityDialog() {
        if (exitDialog != null) return
        exitDialog = ConfirmBottomSingleDialog.show(
            childFragmentManager,
            getString(R.string.tips),
            getString(R.string.inactivity_message),
            getString(R.string.action_confirm),
            true
        ) { d ->
            d.dismissAllowingStateLoss()
            exitDialog = null
            requireActivity().finishAfterTransition()
        }.also { it.isCancelable = false }
    }

    @Composable
    override fun ScreenContent() {
        val ep = endpointState
        val err = errorState
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .pointerInteropFilter {
                    resetInactivityTimer()
                    false
                },
        ) {
            when {
                err != null -> ErrorView(err.message ?: "") {
                    requireActivity().finishAfterTransition()
                }
                ep == null -> LoadingView()
                else -> when (ep.mode) {
                    MyDroidMode.Receiver -> {
                        val vm = viewModel<ConnectToHostSendViewModel>(
                            factory = viewModelFactory { initializer { ConnectToHostSendViewModel(ep) } }
                        )
                        ConnectToHostSendScreen(vm, ep, getString(R.string.connect_to_host_send_title))
                    }
                    MyDroidMode.Send -> {
                        val vm = viewModel<ConnectToHostReceiveViewModel>(
                            factory = viewModelFactory { initializer { ConnectToHostReceiveViewModel(ep) } }
                        )
                        ConnectToHostReceiveScreen(vm, ep, getString(R.string.connect_to_host_receive_title))
                    }
                    MyDroidMode.TextChat -> {
                        val vm = viewModel<ConnectToHostChatViewModel>(
                            factory = viewModelFactory { initializer { ConnectToHostChatViewModel(ep) } }
                        )
                        ConnectToHostChatScreen(vm, ep, getString(R.string.connect_to_host_chat_title))
                    }
                    MyDroidMode.None -> NoneView()
                }
            }
        }
    }

    @Composable
    private fun LoadingView() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            BasicText(
                text = stringResource(R.string.connect_to_host_loading),
                style = ComposeTypography.Font14sp.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }

    @Composable
    private fun ErrorView(message: String, onBack: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = stringResource(R.string.connect_to_host_failed),
                style = ComposeTypography.Font20M.copy(textAlign = TextAlign.Center),
            )
            BasicText(
                text = message,
                style = ComposeTypography.Font14sp.copy(textAlign = TextAlign.Center),
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                Text(text = stringResource(R.string.action_confirm))
            }
        }
    }

    @Composable
    private fun NoneView() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            BasicText(
                text = stringResource(R.string.connect_to_host_mode_none),
                style = ComposeTypography.Font16.copy(textAlign = TextAlign.Center),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        inactivityJob?.cancel()
        inactivityJob = null
        exitDialog?.dismissAllowingStateLoss()
        exitDialog = null
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        private val INACTIVITY_TIMEOUT = 3.minutes
    }
}
