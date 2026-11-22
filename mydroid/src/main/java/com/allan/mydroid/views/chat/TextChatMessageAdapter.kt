package com.allan.mydroid.views.chat

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.allan.mydroid.R
import com.allan.mydroid.beans.wsdata.getIconColorByIp
import com.allan.mydroid.databinding.HolderTextChatDividerBinding
import com.allan.mydroid.databinding.HolderTextChatMessageBinding
import com.allan.mydroid.state.TextChatRow
import com.au.module_android.utils.ViewBackgroundBuilder
import com.au.module_android.utils.dp
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TextChatMessageAdapter(
    private val onMessageLongClick: (String) -> Unit,
) : BindRcvAdapter<TextChatRow, BindViewHolder<TextChatRow, *>>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = datas[position].id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<TextChatRow, *> {
        return when (viewType) {
            TextChatRow.VIEW_TYPE_MESSAGE -> TextChatMessageHolder(create(parent), onMessageLongClick)
            TextChatRow.VIEW_TYPE_DIVIDER -> TextChatDividerHolder(create(parent))
            else -> error("Unknown text chat view type: $viewType")
        }
    }
}

class TextChatMessageHolder(
    binding: HolderTextChatMessageBinding,
    private val onMessageLongClick: (String) -> Unit,
) : BindViewHolder<TextChatRow, HolderTextChatMessageBinding>(binding) {
    private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.ROOT)

    init {
        binding.messageBubbleTv.setOnLongClickListener {
            val text = (currentData as? TextChatRow.Message)?.bean?.text
            if (text.isNullOrEmpty()) {
                false
            } else {
                onMessageLongClick(text)
                true
            }
        }
    }

    override fun bindData(bean: TextChatRow) {
        super.bindData(bean)
        val row = bean as TextChatRow.Message
        val message = row.bean
        val isMine = row.isMine
        val contentLp = binding.contentHost.layoutParams as FrameLayout.LayoutParams

        contentLp.gravity = if (isMine) Gravity.END else Gravity.START
        contentLp.marginStart = if (isMine) 50.dp else 0
        contentLp.marginEnd = if (isMine) 0 else 50.dp
        binding.contentHost.layoutParams = contentLp
        binding.contentHost.gravity = if (isMine) Gravity.END else Gravity.START

        if (isMine) {
            binding.iconView.background = null
            binding.iconView.text = "🌟"
            binding.iconView.layoutParams = binding.iconView.layoutParams.apply {
                width = ViewGroup.LayoutParams.WRAP_CONTENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
            }
        } else {
            val iconColor = try {
                message.iconColor.takeIf { it.isNotBlank() }?.toColorInt()
            } catch (_: IllegalArgumentException) {
                null
            } ?: getIconColorByIp(message.ip).toColorInt()
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
        binding.ipHostTv.text = message.ip
        binding.timeTv.text = timeFormat.format(Date(row.timestamp))
        binding.messageBubbleTv.text = message.text
        binding.messageBubbleTv.setTextAppearance(
            if (isMine) R.style.StyleTextChatSelfMessage else R.style.StyleTextChatMessage
        )
        val context = binding.root.context
        val bubbleColorRes = if (isMine) {
            R.color.text_chat_bubble_self_bg
        } else {
            R.color.text_chat_bubble_other_bg
        }

        binding.messageBubbleTv.background = ViewBackgroundBuilder()
            .setBackground(ContextCompat.getColor(context, bubbleColorRes))
            .setCornerRadius(16f.dp)
            .build()
    }
}

private class TextChatDividerHolder(
    binding: HolderTextChatDividerBinding,
) : BindViewHolder<TextChatRow, HolderTextChatDividerBinding>(binding) {
    override fun bindData(bean: TextChatRow) {
        super.bindData(bean)
        binding.dividerTv.text = (bean as TextChatRow.Divider).displayText(binding.root.context)
    }
}
