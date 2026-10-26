package com.allan.mydroid.views.chat

import android.os.Bundle
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.toColorInt
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.navigation.fragment.findNavController
import com.allan.mydroid.AppGlobals
import com.allan.mydroid.R
import com.allan.mydroid.api.MyDroidMode
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.databinding.FragmentTextChatRoomBinding
import com.allan.mydroid.databinding.MydroidSendClientBinding
import com.allan.mydroid.network.GlobalNetworkMonitorObj
import com.allan.mydroid.state.GlobalClientListFlowsObj
import com.allan.mydroid.state.GlobalTextChatObj
import com.allan.mydroid.views.AbsLiveFragment
import com.au.module_android.Globals
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
import eightbitlab.com.blurview.BlurView3Util
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import kotlin.math.max
import kotlin.math.min

class TextChatRoomFragment : AbsLiveFragment<FragmentTextChatRoomBinding>() {
    companion object {
        private const val SELF_ICON_COLOR = "#6A1B9A"
    }

    private val clientListState: GlobalClientListFlowsObj by inject()
    private val textChatStore: GlobalTextChatObj by inject()

    private val messageList = mutableListOf<TextChatMessageBean>()
    private val messageAdapter = TextChatMessageAdapter()
    private val clipBoardHelp = ClipBoardHelp()
    private val sendClientBindings = mutableListOf<MydroidSendClientBinding>()

    private var selfIp = ""
    private var currentImeOffset = 0
    private val inputMinHeight by lazy { 44.dp }
    private val inputMaxHeight by lazy { 120.dp }

    override fun isAutoHideIme(): Boolean {
        return true
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
        initClientsBlur()
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        observeTextChatMessage()

        val currentView = binding.root
        binding.root.post {
            if (view !== currentView) return@post
            val navigationBarHeight = requireActivity().currentStatusBarAndNavBarHeight()?.second ?: 0
            updateRootBottomPadding(navigationBarHeight)
            updateRecyclerBottomPadding()
        }
    }

    override fun getMode() = MyDroidMode.TextChat

    // 初始化消息列表。
    private fun initRecyclerView() {
        binding.chatRcv.layoutManager = LinearLayoutManager(requireContext())
        binding.chatRcv.adapter = messageAdapter
        textChatStore.loadHistory()

    }

    // 监听输入框发送动作。
    private fun initInputAction() {
        binding.inputEdit.background = ViewBackgroundBuilder()
            .setBackground(resources.getColor(R.color.text_chat_input_bg, null))
            .setStroke(1f.dp, resources.getColor(R.color.text_chat_input_border, null))
            .setCornerRadius(20f.dp)
            .build()
        binding.sendBtn.isEnabled = false
        binding.sendBtn.alpha = 0.45f
        binding.sendBtn.setOnClickListener {
            sendCurrentText()
        }
        binding.inputEdit.doAfterTextChanged { text ->
            val canSend = !text.isNullOrBlank()
            binding.sendBtn.isEnabled = canSend
            binding.sendBtn.alpha = if (canSend) 1f else 0.45f
            updateInputEditHeight()
            updateRecyclerBottomPadding()
        }
        val currentView = binding.root
        binding.inputEdit.post {
            if (view !== currentView) return@post
            updateInputEditHeight()
            updateRecyclerBottomPadding()
        }
    }

    // 同步当前房间自己的地址信息。
    private fun observeSelfAddress() {
        launchRepeatOnStarted(get<GlobalNetworkMonitorObj>().networkInfoFlow) { networkInfo ->
            selfIp = networkInfo?.ip.orEmpty()
            binding.hostAddressTv.text = if (selfIp.isEmpty()) {
                Globals.app.getString(R.string.connect_wifi_or_hotspot)
            } else {
                val port = networkInfo?.httpPort
                if (port == null) selfIp else "$selfIp:$port"
            }
            messageAdapter.updateSelfIp(selfIp)
        }
    }

    // 监听从 websocket 进入 app 的文本消息。
    private fun observeTextChatMessage() {
        launchRepeatOnStarted {
            textChatStore.historyFlow.collect { history ->
                messageList.clear()
                messageList.addAll(history)
                messageAdapter.submitList(messageList.toList(), false)
                scrollToBottom(false)
            }
        }
    }

    // 顶部展示已接入的客户端。
    private fun observeClientList() {
        launchRepeatOnStarted(clientListState.clientListFlow) { clientList ->
            for (clientBinding in sendClientBindings) {
                clientBinding.root.gone()
            }

            if (clientList.isEmpty()) {
                binding.clientsHost.gone()
                binding.clientsBlurView.gone()
                updateRecyclerTopPadding()
                return@launchRepeatOnStarted
            }

            binding.clientsHost.visible()
            binding.clientsBlurView.visible()
            clientList.forEachIndexed { index, clientInfo ->
                val item = clientItem(index)
                item.title.text = clientInfo.clientName
                item.title.setTextAppearance(R.style.StyleTextChatClient)
                item.icon.layoutParams = item.icon.layoutParams.apply {
                    width = 18.dp
                    height = 18.dp
                }
                item.icon.background = ViewBackgroundBuilder()
                    .setBackground(getIconColorByIp(clientInfo.clientName).toColorInt())
                    .setCornerRadius(32f.dp)
                    .build()
                if (!item.root.isAttachedToWindow) {
                    binding.clientsHost.addView(item.root)
                }
                item.root.visible()
            }
            val currentView = binding.root
            binding.clientsHost.doOnLayout {
                if (view !== currentView) return@doOnLayout
                updateRecyclerTopPadding()
            }
        }
    }

    // 配置 clientsHost 背景的 backdrop blur。
    private fun initClientsBlur() {
        val density = resources.displayMetrics.density
        BlurView3Util(binding.clientsBlurView, 16, 12f * density).setBlurWithOverlayNoNoise(
            binding.blurTarget,
            resources.getColor(R.color.text_chat_clients_overlay, null),
            resources.getColor(R.color.text_chat_surface, null),
        )
    }

    // 利用输入法监听同步底部输入区和列表滚动。
    private fun initImeAssist() {
        ImeHelper.assist(requireActivity()).setOnImeListener { imeOffset, _, _, navigationBarHeight ->
            if (view == null) return@setOnImeListener
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
            host = "",
            timestamp = System.currentTimeMillis(),
            iconColor = SELF_ICON_COLOR,
        )
        textChatStore.addMessage(bean)
        AppGlobals.globalDroidServer.broadcastTextChatFromApp(bean)
        binding.inputEdit.setText("")
        updateInputEditHeight()
        updateRecyclerBottomPadding()
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
        val currentView = binding.root
        binding.inputEdit.post {
            if (view !== currentView) return@post
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

    // 列表布局已排除输入区和底部安全区，仅补偿键盘位移。
    private fun updateRecyclerBottomPadding() {
        binding.chatRcv.updatePadding(
            bottom = currentImeOffset + 12.dp,
        )
    }

    // 给列表顶部预留接入客户端区域的高度，避免消息被遮挡。
    private fun updateRecyclerTopPadding() {
        val topExtra = if (binding.clientsHost.visibility == View.VISIBLE) {
            binding.clientsHost.height + 8.dp
        } else {
            0
        }
        binding.chatRcv.updatePadding(top = 12.dp + topExtra)
    }

    // 将列表滚动到最后一条消息。
    private fun scrollToBottom(smooth: Boolean) {
        val targetPosition = messageAdapter.itemCount - 1
        if (targetPosition < 0) {
            return
        }
        val currentView = binding.root
        binding.chatRcv.post {
            if (view !== currentView) return@post
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
            requireActivity().changeBarsColor()
        }
    }

    override fun onDestroyView() {
        sendClientBindings.clear()
        currentImeOffset = 0
        super.onDestroyView()
    }

    override fun toolbarInfo(): YourToolbarInfo? {
        return null
    }
}
