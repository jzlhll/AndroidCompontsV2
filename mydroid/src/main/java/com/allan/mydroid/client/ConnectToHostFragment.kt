package com.allan.mydroid.client

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.client.chat.ConnectToHostChatScreen
import com.allan.mydroid.client.chat.ConnectToHostChatViewModel
import com.allan.mydroid.client.receive.ConnectToHostReceiveScreen
import com.allan.mydroid.client.receive.ConnectToHostReceiveViewModel
import com.allan.mydroid.client.send.ConnectToHostSendScreen
import com.allan.mydroid.client.send.ConnectToHostSendViewModel
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.ui.ComposeViewFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.milliseconds

/** 发现后的会话入口，视图重建复用 ViewModel，活动传输期间暂停空闲提示。 */
class ConnectToHostFragment : ComposeViewFragment() {
    private val endpoint by lazy {
        HostEndpoint(arguments?.getString("ip") ?: "", arguments?.getInt("port") ?: 0, 0,
            MyDroidMode.entries.getOrElse(arguments?.getInt("mode") ?: 0) { MyDroidMode.None })
    }
    private val sendModel: ConnectToHostSendViewModel by viewModel { parametersOf(endpoint) }
    private val receiveModel: ConnectToHostReceiveViewModel by viewModel { parametersOf(endpoint) }
    private val chatModel: ConnectToHostChatViewModel by viewModel { parametersOf(endpoint) }
    private var showIdle by mutableStateOf(false)
    private var showExit by mutableStateOf(false)
    private var leaving by mutableStateOf(false)
    private var lastActivity = SystemClock.elapsedRealtime()

    override val customBackAction: () -> Boolean = {
        if (!leaving) {
            if (hasTransfer()) showExit = true else findNavController().popBackStack()
        }
        false
    }

    private fun hasTransfer(): Boolean = when (endpoint.mode) {
        MyDroidMode.Receiver -> sendModel.uiState.value.busy
        MyDroidMode.Send -> receiveModel.uiState.value.downloadTasks.any { it.active }
        else -> false
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.keepScreenOn = true
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                lastActivity = SystemClock.elapsedRealtime()
                if (endpoint.mode == MyDroidMode.Send) receiveModel.refreshLocalFiles()
                var messageCount = if (endpoint.mode == MyDroidMode.TextChat) chatModel.uiState.value.messages.size else 0
                while (true) {
                    delay(1000.milliseconds)
                    val count = if (endpoint.mode == MyDroidMode.TextChat) chatModel.uiState.value.messages.size else 0
                    if (hasTransfer() || count != messageCount) {
                        lastActivity = SystemClock.elapsedRealtime()
                        showIdle = false
                    } else if (!showExit && SystemClock.elapsedRealtime() - lastActivity >= 180000) {
                        showIdle = true
                    }
                    messageCount = count
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        Box(Modifier.fillMaxSize().background(Color.White).pointerInteropFilter {
            lastActivity = SystemClock.elapsedRealtime()
            false
        }) {
            when (endpoint.mode) {
                MyDroidMode.Receiver -> ConnectToHostSendScreen(sendModel, endpoint, stringResource(R.string.connect_to_host_send_title))
                MyDroidMode.Send -> ConnectToHostReceiveScreen(receiveModel, endpoint, stringResource(R.string.connect_to_host_receive_title))
                MyDroidMode.TextChat -> ConnectToHostChatScreen(chatModel, endpoint, stringResource(R.string.connect_to_host_chat_title))
                MyDroidMode.None -> TransferMessageDialog(stringResource(R.string.connect_to_host_mode_none)) {
                    findNavController().popBackStack()
                }
            }
        }
        if (showIdle || showExit) {
            val upload = showExit && endpoint.mode == MyDroidMode.Receiver
            val message = when {
                leaving -> R.string.transfer_stopping
                upload -> R.string.transfer_exit_upload
                showExit -> R.string.transfer_exit_download
                else -> R.string.transfer_idle
            }
            AlertDialog(
                onDismissRequest = { if (!leaving) { showIdle = false; showExit = false; lastActivity = SystemClock.elapsedRealtime() } },
                text = { Text(stringResource(message), style = ComposeTypography.Font14sp) },
                confirmButton = { TextButton(enabled = !leaving, onClick = {
                    leaving = true
                    viewLifecycleOwner.lifecycleScope.launch {
                        if (upload) sendModel.stopAndAwait()
                        findNavController().popBackStack()
                    }
                }) { Text(stringResource(R.string.transfer_back), style = ComposeTypography.Font14sp) } },
                dismissButton = { TextButton(enabled = !leaving, onClick = {
                    showIdle = false
                    showExit = false
                    lastActivity = SystemClock.elapsedRealtime()
                }) { Text(stringResource(R.string.transfer_continue), style = ComposeTypography.Font14sp) } }
            )
        }
    }
}
