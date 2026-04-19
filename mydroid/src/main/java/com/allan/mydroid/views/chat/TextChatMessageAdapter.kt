package com.allan.mydroid.views.chat

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.toColorInt
import com.allan.mydroid.databinding.HolderTextChatMessageBinding
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.IViewTypeBean
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import kotlin.random.Random

data class TextChatMessageBean(val text: String, val ip: String, val host: String) : IViewTypeBean

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
}

class TextChatMessageHolder(
    binding: HolderTextChatMessageBinding,
    private val selfChecker: (TextChatMessageBean) -> Boolean,
    private val colorProvider: (String) -> Int,
) : BindViewHolder<TextChatMessageBean, HolderTextChatMessageBinding>(binding) {

    override fun bindData(bean: TextChatMessageBean) {
        super.bindData(bean)
        val isSelf = selfChecker(bean)
        val contentLp = binding.contentHost.layoutParams as FrameLayout.LayoutParams

        contentLp.gravity = if (isSelf) Gravity.END else Gravity.START
        contentLp.marginStart = if (isSelf) 50.dp else 0
        contentLp.marginEnd = if (isSelf) 0 else 50.dp
        binding.contentHost.layoutParams = contentLp

        binding.iconView.background = ViewBackgroundBuilder()
            .setBackground(colorProvider(bean.ip))
            .setCornerRadius(12f.dp)
            .build()
        binding.ipHostTv.text = if (bean.host.isBlank()) {
            "${bean.ip}:-"
        } else {
            "${bean.ip}:${bean.host}"
        }
        binding.messageTv.text = bean.text

        binding.contentHost.background = if (isSelf) {
            ViewBackgroundBuilder()
                .setBackground("#DCF8C6".toColorInt())
                .setCornerRadius(16f.dp)
                .build()
        } else {
            null
        }
    }
}
