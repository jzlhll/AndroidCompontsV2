package com.allan.mydroid.views.chat

import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import com.allan.mydroid.beans.wsdata.TextChatMessageBean
import com.allan.mydroid.databinding.HolderTextChatMessageBinding
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

class TextChatMessageAdapter : BindRcvAdapter<TextChatMessageBean, TextChatMessageHolder>() {
    private val colorPalette = listOf(
        "#3D7C42",
        "#FF9E80",
        "#6A3188",
        "#895DF8",
        "#CEBE55",
        "#5CCE99",
        "#CE626E",
        "#71A3CE",
    )
    private val ipColorCache = linkedMapOf<String, Int>()

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

    // 获取并缓存指定ip的头像颜色。
    private fun getIconColor(ip: String): Int {
        return ipColorCache.getOrPut(ip) {
            colorPalette[Random.nextInt(colorPalette.size)].toColorInt()
        }
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

        val iconColor = if (bean.iconColor.isBlank()) {
            colorProvider(bean.ip)
        } else {
            bean.iconColor.toColorInt()
        }
        binding.iconView.background = ViewBackgroundBuilder()
            .setBackground(iconColor)
            .setCornerRadius(11f.dp)
            .build()
        val timeText = timeFormat.format(Date(bean.timestamp))
        binding.ipHostTv.text = if (bean.host.isBlank()) {
            "${bean.ip}:-  $timeText"
        } else {
            "${bean.ip}:${bean.host}  $timeText"
        }
        binding.messageBubbleTv.text = bean.text
        binding.messageBubbleTv.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            if (isSelf) 15f else 16f
        )

        binding.messageBubbleTv.background = if (isSelf) {
            ViewBackgroundBuilder()
                .setBackground("#DCF8C6".toColorInt())
                .setCornerRadius(16f.dp)
                .build()
        } else {
            ViewBackgroundBuilder()
                .setBackground("#f0f0f0".toColorInt())
                .setCornerRadius(16f.dp)
                .build()
        }
    }
}
