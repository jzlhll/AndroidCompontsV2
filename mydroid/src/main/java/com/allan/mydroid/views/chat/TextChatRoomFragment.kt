package com.allan.mydroid.views.chat

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.mydroid.R
import com.allan.mydroid.databinding.FragmentTextChatRoomBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.au.module_android.utils.ImeHelper
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_android.utils.dp
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.ui.bindings.BindingFragment
import com.au.module_androidui.ui.views.YourToolbarInfo
import org.koin.android.ext.android.get
import kotlin.math.max
import kotlin.math.min

class TextChatRoomFragment : BindingFragment<FragmentTextChatRoomBinding>() {
    private val messageList = mutableListOf<TextChatMessageBean>()
    private val messageAdapter = TextChatMessageAdapter()

    private var selfIp = ""
    private var selfHost = "-"
    private var currentImeOffset = 0

    override fun isAutoHideIme(): Boolean {
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().changeBarsColor(statusBarTextDark = false)
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initInputAction()
        initImeAssist()
        observeSelfAddress()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }

        binding.root.post {
            val navigationBarHeight = requireActivity().currentStatusBarAndNavBarHeight()?.second ?: 0
            updateRootBottomPadding(navigationBarHeight)
            updateRecyclerBottomPadding()
        }
    }

    // 初始化消息列表。
    private fun initRecyclerView() {
        binding.chatRcv.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.chatRcv.adapter = messageAdapter
    }

    // 监听输入框发送动作。
    private fun initInputAction() {
        binding.inputEdit.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEND || isEnterAction(event)) {
                sendCurrentText()
                true
            } else {
                false
            }
        }
    }

    // 同步当前房间自己的地址信息。
    private fun observeSelfAddress() {
        launchRepeatOnStarted(get<GlobalNetworkMonitor>().networkInfoFlow) { networkInfo ->
            selfIp = networkInfo?.ip.orEmpty()
            selfHost = networkInfo?.httpPort?.toString()
                ?: networkInfo?.wsPort?.toString()
                ?: "-"
            binding.title.text = if (selfIp.isEmpty()) {
                getString(R.string.connect_wifi_or_hotspot)
            } else {
                "$selfIp:$selfHost"
            }
            messageAdapter.updateSelfIp(selfIp)
        }
    }

    // 利用输入法监听同步底部输入区和列表滚动。
    private fun initImeAssist() {
        ImeHelper.assist(requireActivity()).setOnImeListener { imeOffset, _, _, navigationBarHeight ->
            updateRootBottomPadding(navigationBarHeight)
            currentImeOffset = max(0, imeOffset - navigationBarHeight)
            binding.inputHost.translationY = min(0f, -imeOffset.toFloat() + navigationBarHeight)
            updateRecyclerBottomPadding()
            scrollToBottom(false)
        }
    }

    // 发送当前输入框内容。
    private fun sendCurrentText() {
        val text = binding.inputEdit.text?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            return
        }

        messageList.add(
            TextChatMessageBean(
                text = text,
                ip = selfIp.ifEmpty { "0.0.0.0" },
                host = selfHost.ifEmpty { "-" },
            )
        )
        messageAdapter.submitList(messageList.toList(), false)
        binding.inputEdit.setText("")
        scrollToBottom(true)
    }

    // 更新根布局的底部安全区。
    private fun updateRootBottomPadding(navigationBarHeight: Int) {
        binding.root.updatePadding(bottom = navigationBarHeight)
    }

    // 给列表预留输入区高度，避免消息被遮挡。
    private fun updateRecyclerBottomPadding() {
        binding.chatRcv.updatePadding(
            bottom = binding.inputHost.height + binding.root.paddingBottom + currentImeOffset + 12.dp,
        )
    }

    // 将列表滚动到最后一条消息。
    private fun scrollToBottom(smooth: Boolean) {
        val targetPosition = messageAdapter.itemCount - 1
        if (targetPosition < 0) {
            return
        }
        binding.chatRcv.post {
            if (smooth) {
                binding.chatRcv.smoothScrollToPosition(targetPosition)
            } else {
                binding.chatRcv.scrollToPosition(targetPosition)
            }
        }
    }

    // 判断是否为回车发送动作。
    private fun isEnterAction(event: KeyEvent?): Boolean {
        return event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
    }

    override fun immersiveMode(): ImmersiveMode {
        return ImmersiveMode.FullImmersive { statusBarsHeight, navBarHeight ->
            binding.toolbar.layoutParams.asOrNull<ConstraintLayout.LayoutParams>()?.let { toolbarLP ->
                toolbarLP.topMargin = statusBarsHeight
                binding.toolbar.layoutParams = toolbarLP
            }
            binding.root.updatePadding(bottom = navBarHeight)
        }
    }

    override fun toolbarInfo(): YourToolbarInfo? {
        return null
    }
}
