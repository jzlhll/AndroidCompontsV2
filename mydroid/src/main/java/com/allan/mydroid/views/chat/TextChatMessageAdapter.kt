package com.allan.mydroid.views.chat

import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.allan.mydroid.R
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.databinding.HolderTextChatMessageBinding
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TextChatMessageAdapter : BindRcvAdapter<TextChatMessageBean, TextChatMessageHolder>() {
    private var selfIp = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TextChatMessageHolder {
        return TextChatMessageHolder(
            create(parent),
            selfChecker = { bean -> isSelfMessage(bean) },
            colorProvider = { ip -> getIconColor(ip) },
            onMessageLongClick = onMessageLongClick,
        )
    }

    fun updateSelfIp(ip: String) {
        if (selfIp == ip) {
            return
        }
        selfIp = ip
        if (itemCount > 0) {
            notifyDataSetChanged()
        }
    }

    // 判断消息是否来自当前设备。
    private fun isSelfMessage(bean: TextChatMessageBean): Boolean {
        return selfIp.isNotEmpty() && bean.ip == selfIp
    }

    // 根据 ip 哈希取头像颜色，与 HTML 端保持一致。
    private fun getIconColor(ip: String): Int {
        return getIconColorByIp(ip).toColorInt()
    }

    var onMessageLongClick: (String) -> Unit = {}
}

class TextChatMessageHolder(
    binding: HolderTextChatMessageBinding,
    private val selfChecker: (TextChatMessageBean) -> Boolean,
    private val colorProvider: (String) -> Int,
    private val onMessageLongClick: (String) -> Unit,
) : BindViewHolder<TextChatMessageBean, HolderTextChatMessageBinding>(binding) {
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    init {
        binding.messageBubbleTv.setOnLongClickListener {
            val text = currentData?.text
            if (text.isNullOrEmpty()) {
                false
            } else {
                onMessageLongClick(text)
                true
            }
        }
    }

    override fun bindData(bean: TextChatMessageBean) {
        super.bindData(bean)
        val isSelf = selfChecker(bean)
        val contentLp = binding.contentHost.layoutParams as FrameLayout.LayoutParams

        contentLp.gravity = if (isSelf) Gravity.END else Gravity.START
        contentLp.marginStart = if (isSelf) 50.dp else 0
        contentLp.marginEnd = if (isSelf) 0 else 50.dp
        binding.contentHost.layoutParams = contentLp
        binding.contentHost.gravity = if (isSelf) Gravity.END else Gravity.START

        if (isSelf) {
            // host 自己的消息：🌟
            binding.iconView.background = null
            binding.iconView.text = "🌟"
            binding.iconView.layoutParams = binding.iconView.layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        } else {
            // 客户端接入的消息：随机色圆圈
            val iconColor = if (bean.iconColor.isBlank()) {
                colorProvider(bean.ip)
            } else {
                bean.iconColor.toColorInt()
            }
            binding.iconView.text = ""
            binding.iconView.background = ViewBackgroundBuilder()
                .setBackground(iconColor)
                .setCornerRadius(11f.dp)
                .build()
            binding.iconView.layoutParams = binding.iconView.layoutParams.apply {
                width = 20.dp
                height = 20.dp
            }
        }
        val timeText = timeFormat.format(Date(bean.timestamp))
        binding.ipHostTv.text = bean.ip
        binding.ipHostTv.background = ViewBackgroundBuilder()
            .setBackground(0xFFF0F0F0.toInt())
            .setCornerRadius(4f.dp)
            .build()
        binding.ipHostTv.setPadding(4.dp, 2.dp, 4.dp, 2.dp)
        binding.timeTv.text = timeText
        binding.messageBubbleTv.text = bean.text
        binding.messageBubbleTv.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (isSelf) 15f else 16f
        )
        val context = binding.root.context
        val bubbleColorRes = if (isSelf) {
            R.color.text_chat_bubble_self_bg
        } else {
            R.color.text_chat_bubble_other_bg
        }
        val textColorRes = if (isSelf) {
            R.color.text_chat_bubble_self_text
        } else {
            R.color.text_chat_bubble_other_text
        }
        binding.messageBubbleTv.setTextColor(ContextCompat.getColor(context, textColorRes))

        binding.messageBubbleTv.background = ViewBackgroundBuilder()
            .setBackground(ContextCompat.getColor(context, bubbleColorRes))
            .setCornerRadius(16f.dp)
            .build()
    }
}