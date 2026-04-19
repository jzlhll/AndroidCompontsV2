package com.allan.mydroid.views.chat

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.allan.mydroid.AppGlobals
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.databinding.FragmentTextChatRoomBinding
import com.allan.mydroid.databinding.MydroidSendClientBinding
import com.allan.mydroid.globals.GlobalNetworkMonitor
import com.allan.mydroid.globals.MyDroidConst
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.clipboard.ClipBoardHelp
import com.au.module_android.utils.ImeHelper
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.asOrNull
import com.au.module_android.utils.changeBarsColor
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_android.utils.dp
import com.au.module_android.utils.gone
import com.au.module_android.utils.launchRepeatOnStarted
import com.au.module_android.utils.visible
import com.au.module_androidui.ui.base.ImmersiveMode
import com.au.module_androidui.toast.ToastBuilder
import com.au.module_androidui.ui.views.YourToolbarInfo
import org.koin.android.ext.android.get
import kotlin.math.max
import kotlin.math.min

class TextChatRoomFragment : AbsLiveFragment<FragmentTextChatRoomBinding>() {
    companion object {
        private const val SELF_ICON_COLOR = "#6A1B9A"
    }

    private val messageList = mutableListOf<TextChatMessageBean>()
    private val messageAdapter = TextChatMessageAdapter()
    private val clipBoardHelp = ClipBoardHelp()
    private val sendClientBindings = mutableListOf<MydroidSendClientBinding>()

    private var selfIp = ""
    private var selfHost = "-"
    private var currentImeOffset = 0
    private val inputMinHeight by lazy { 40.dp }
    private val inputMaxHeight by lazy { 120.dp }

    override fun isAutoHideIme(): Boolean {
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().changeBarsColor(statusBarTextDark = false)
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        super.onBindingCreated(savedInstanceState)
        messageAdapter.onMessageLongClick = {
            copyMessageText(it)
        }
        initRecyclerView()
        initInputAction()
        initImeAssist()
        observeSelfAddress()
        observeClientList()
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().finishAfterTransition()
        }
        observeTextChatMessage()

        binding.root.post {
            val navigationBarHeight = requireActivity().currentStatusBarAndNavBarHeight()?.second ?: 0
            updateRootBottomPadding(navigationBarHeight)
            updateRecyclerBottomPadding()
        }
    }

    override fun onStart() {
        MyDroidConst.currentDroidMode = MyDroidMode.TextChat
        super.onStart()
    }

    // 初始化消息列表。
    private fun initRecyclerView() {
        binding.chatRcv.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRcv.adapter = messageAdapter
        if (MyDroidConst.textChatHistory.isNotEmpty()) {
            messageList.addAll(MyDroidConst.textChatHistory)
            messageAdapter.submitList(messageList.toList(), false)
            scrollToBottom(false)
        }
    }

    // 监听输入框发送动作。
    private fun initInputAction() {
        binding.sendBtn.setOnClickListener {
            sendCurrentText()
        }
        binding.inputEdit.doAfterTextChanged {
            updateInputEditHeight()
            updateRecyclerBottomPadding()
        }
        binding.inputEdit.post {
            updateInputEditHeight()
            updateRecyclerBottomPadding()
        }
    }

    // 同步当前房间自己的地址信息。
    private fun observeSelfAddress() {
        launchRepeatOnStarted(get<GlobalNetworkMonitor>().networkInfoFlow) { networkInfo ->
            selfIp = networkInfo?.ip.orEmpty()
            val portStr = networkInfo?.httpPort?.toString()
                ?: networkInfo?.wsPort?.toString()
            selfHost = portStr ?: "-"
            binding.title.text = if (selfIp.isEmpty()) {
                getString(R.string.connect_wifi_or_hotspot)
            } else if (portStr.isNullOrEmpty()) {
                selfIp
            } else {
                "$selfIp:$portStr"
            }
            messageAdapter.updateSelfIp(selfIp)
        }
    }

    // 监听从 websocket 进入 app 的文本消息。
    private fun observeTextChatMessage() {
        MyDroidConst.textChatIncomingData.observeUnStick(this) { bean ->
            appendMessage(bean, false, true)
        }
    }

    // 顶部展示已接入的客户端。
    private fun observeClientList() {
        MyDroidConst.clientListLiveData.observe(this) { clientList ->
            for (clientBinding in sendClientBindings) {
                clientBinding.root.gone()
            }

            if (clientList.isEmpty()) {
                binding.clientsHost.gone()
                return@observe
            }

            binding.clientsHost.visible()
            clientList.forEachIndexed { index, clientInfo ->
                val item = clientItem(index)
                item.title.text = clientInfo.clientName
                item.title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                item.icon.layoutParams = item.icon.layoutParams.apply {
                    width = 18.dp
                    height = 18.dp
                }
                item.icon.background = ViewBackgroundBuilder()
                    .setBackground(clientInfo.color.toColorInt())
                    .setCornerRadius(32f.dp)
                    .build()
                if (!item.root.isAttachedToWindow) {
                    binding.clientsHost.addView(item.root)
                }
                item.root.visible()
            }
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

        val bean = TextChatMessageBean(
            text = text,
            ip = selfIp.ifEmpty { "0.0.0.0" },
            host = selfHost.ifEmpty { "-" },
            timestamp = System.currentTimeMillis(),
            iconColor = SELF_ICON_COLOR,
        )
        appendMessage(bean, true, true)
        AppGlobals.globalDroidServer.websocketServer?.broadcastTextChatFromApp(bean)
        binding.inputEdit.setText("")
        updateInputEditHeight()
        updateRecyclerBottomPadding()
    }

    // 将消息写入本地会话并刷新界面。
    private fun appendMessage(bean: TextChatMessageBean, saveHistory: Boolean, scrollBottom: Boolean) {
        if (saveHistory) {
            MyDroidConst.textChatHistory.add(bean)
        }
        messageList.add(bean)
        messageAdapter.submitList(messageList.toList(), false)
        if (scrollBottom) {
            scrollToBottom(true)
        }
    }

    private fun clientItem(index:Int) : MydroidSendClientBinding {
        var binding = sendClientBindings.getOrNull(index)
        if (binding != null) {
            return binding
        }

        binding = MydroidSendClientBinding.inflate(layoutInflater)
        sendClientBindings.add(binding)
        return binding
    }

    // 复制消息文案到剪贴板。
    private fun copyMessageText(text: String) {
        clipBoardHelp.copyToClipBoard(requireActivity(), text)
        ToastBuilder()
            .setOnTop()
            .setIcon("success")
            .setMessage(getString(R.string.text_chat_copy_success))
            .toast()
    }

    // 更新根布局的底部安全区。
    private fun updateRootBottomPadding(navigationBarHeight: Int) {
        binding.root.updatePadding(bottom = navigationBarHeight)
    }

    // 让输入框随内容增高，到最大高度后启用内部滚动。
    private fun updateInputEditHeight() {
        binding.inputEdit.post {
            val layout = binding.inputEdit.layout
            val desiredHeight = if (layout == null) {
                inputMinHeight
            } else {
                layout.height + binding.inputEdit.compoundPaddingTop + binding.inputEdit.compoundPaddingBottom
            }
            val targetHeight = max(inputMinHeight, min(inputMaxHeight, desiredHeight))
            binding.inputEdit.layoutParams = binding.inputEdit.layoutParams.apply {
                height = targetHeight
            }
            val needScroll = desiredHeight > inputMaxHeight
            binding.inputEdit.isVerticalScrollBarEnabled = needScroll
            binding.inputEdit.overScrollMode = if (needScroll) {
                View.OVER_SCROLL_IF_CONTENT_SCROLLS
            } else {
                View.OVER_SCROLL_NEVER
            }
            if (!needScroll) {
                binding.inputEdit.scrollTo(0, 0)
            }
        }
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
