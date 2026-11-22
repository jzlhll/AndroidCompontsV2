package com.allan.mydroid.client

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.client.chat.ConnectToHostChatScreen
import com.allan.mydroid.client.chat.ConnectToHostChatSettings
import com.allan.mydroid.client.chat.ConnectToHostChatViewModel
import com.allan.mydroid.client.receive.ConnectToHostReceiveScreen
import com.allan.mydroid.client.receive.ConnectToHostReceiveViewModel
import com.allan.mydroid.client.send.ConnectToHostSendScreen
import com.allan.mydroid.client.send.ConnectToHostSendViewModel
import com.au.module_androiduiex.styles.ComposeColors
import com.au.module_androiduiex.styles.ComposeTypography
import com.au.module_androiduiex.ui.ComposeViewFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** 发现后的会话入口，视图重建复用 ViewModel，活动传输期间暂停空闲提示。 */
class ConnectToHostFragment : ComposeViewFragment() {
    companion object {
        private const val KEY_CHAT_SETTINGS_VISIBLE = "chat_settings_visible"
        private val chatSettingsSlideSpec = tween<Float>(
            durationMillis = 300,
            easing = Easing(DecelerateInterpolator()::getInterpolation),
        )
    }

    private enum class ChatSettingsState { Closed, Open, Closing }

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
    private var chatSettingsState by mutableStateOf(ChatSettingsState.Closed)
    private var lastActivity = SystemClock.elapsedRealtime()

    override val customBackAction: () -> Boolean = {
        if (chatSettingsState == ChatSettingsState.Open) {
            closeChatSettings()
        } else if (chatSettingsState == ChatSettingsState.Closed && !leaving) {
            if (hasTransfer()) showExit = true else findNavController().popBackStack()
        }
        false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean(KEY_CHAT_SETTINGS_VISIBLE) == true) {
            chatSettingsState = ChatSettingsState.Open
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_CHAT_SETTINGS_VISIBLE, chatSettingsState == ChatSettingsState.Open)
    }

    private fun closeChatSettings() {
        if (chatSettingsState != ChatSettingsState.Open) return
        chatSettingsState = ChatSettingsState.Closing
        lastActivity = SystemClock.elapsedRealtime()
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
                val liveMessageCountFlow = if (endpoint.mode == MyDroidMode.TextChat) chatModel.liveMessageCountFlow else null
                var messageCount = liveMessageCountFlow?.value ?: 0L
                while (true) {
                    delay(1000.milliseconds)
                    val count = liveMessageCountFlow?.value ?: 0L
                    if (hasTransfer() || count != messageCount) {
                        lastActivity = SystemClock.elapsedRealtime()
                        showIdle = false
                    } else if (!showExit && chatSettingsState == ChatSettingsState.Closed &&
                        SystemClock.elapsedRealtime() - lastActivity >= 180000
                    ) {
                        showIdle = true
                    }
                    messageCount = count
                }
            }
        }
    }

    @Composable
    override fun ScreenContent() {
        TransferTheme {
            Box(Modifier.fillMaxSize().background(ComposeColors.BackgroundBg).pointerInteropFilter {
                lastActivity = SystemClock.elapsedRealtime()
                false
            }) {
                when (endpoint.mode) {
                    MyDroidMode.Receiver -> ConnectToHostSendScreen(sendModel, endpoint, stringResource(R.string.connect_to_host_send_title))
                    MyDroidMode.Send -> ConnectToHostReceiveScreen(receiveModel, endpoint, stringResource(R.string.connect_to_host_receive_title))
                    MyDroidMode.TextChat -> ChatContent()
                    MyDroidMode.InstallApk,
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
                    }) { Text(stringResource(R.string.transfer_back), style = ComposeTypography.Font14sp, color = LocalContentColor.current) } },
                    dismissButton = { TextButton(enabled = !leaving, onClick = {
                        showIdle = false
                        showExit = false
                        lastActivity = SystemClock.elapsedRealtime()
                    }) { Text(stringResource(R.string.transfer_continue), style = ComposeTypography.Font14sp, color = LocalContentColor.current) } }
                )
            }
        }
    }

    @Composable
    private fun ChatContent() {
        val settingsState = chatSettingsState
        val targetProgress = if (settingsState == ChatSettingsState.Open) 1f else 0f
        val progress = remember { Animatable(targetProgress) }
        val settingsActive = settingsState != ChatSettingsState.Closed

        LaunchedEffect(settingsState) {
            if (progress.value != targetProgress) {
                progress.animateTo(targetProgress, chatSettingsSlideSpec)
            }
            if (settingsState == ChatSettingsState.Closing) {
                chatSettingsState = ChatSettingsState.Closed
            }
        }

        Box(Modifier.fillMaxSize().clipToBounds()) {
            ConnectToHostChatScreen(
                chatModel,
                endpoint,
                titleText = stringResource(R.string.connect_to_host_chat_title),
                settingsVisible = settingsActive,
                onOpenSettings = {
                    showIdle = false
                    chatSettingsState = ChatSettingsState.Open
                },
                modifier = Modifier.chatPageOffset { -0.5f * progress.value },
            )
            if (settingsActive) {
                ConnectToHostChatSettings(
                    onClose = ::closeChatSettings,
                    modifier = Modifier.chatPageOffset { 1f - progress.value },
                )
            }
        }
    }
}

// 在布局阶段移动页面，使 AndroidView 的视图位置和触摸坐标同步。
private fun Modifier.chatPageOffset(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val page = measurable.measure(constraints)
    layout(page.width, page.height) {
        page.place((page.width * fraction()).roundToInt(), 0)
    }
}
