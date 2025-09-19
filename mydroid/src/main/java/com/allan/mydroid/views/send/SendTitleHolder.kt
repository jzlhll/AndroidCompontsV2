package com.allan.mydroid.views.send

import android.view.ViewGroup
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.databinding.HolderMydroidSendlistItemBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistItemNoBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistTitleBinding
import com.allan.mydroid.globals.getIcon
import com.au.module_android.click.onClick
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class SendTitleHolder(binding: HolderMydroidSendlistTitleBinding)
    : BindViewHolder<Any, HolderMydroidSendlistTitleBinding>(binding) {
    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is IconTitle) return
        binding.icon.setImageResource(bean.icon)
        binding.title.text = bean.title
    }
}

class SendHolder(binding: HolderMydroidSendlistItemBinding, val itemClick: (UriRealInfoEx?, String) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemBinding>(binding) {
    private val targetAlpha = 0.55f

    init {
        binding.checkBox.setOnCheckedChangeListener { _, isChecked ->
            val d = currentData
            if (d !is UriRealInfoEx) return@setOnCheckedChangeListener
            d.isChecked = isChecked
            binding.root.alpha = if (isChecked) 1f else targetAlpha
        }

        binding.root.onClick {
            itemClick(currentData as UriRealInfoEx?, "")
        }

        binding.deleteBtn.onClick {
            itemClick(currentData as UriRealInfoEx?, "delete")
        }
    }

    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is UriRealInfoEx) return
        val goodName = bean.goodName()
        binding.icon.setImageResource(getIcon(bean.goodName()))
        binding.fileNameTv.text = goodName ?: bean.uri.toString()
        binding.fileSizeAndMD5Tv.text = bean.fileSizeStr
        binding.checkBox.isChecked = bean.isChecked
        binding.deleteBtn.visibility = if (bean.hasDeleteButton) ViewGroup.VISIBLE else ViewGroup.GONE

        binding.root.alpha = if (bean.isChecked) 1f else targetAlpha
    }
}

class SendNoBtnHolder(binding: HolderMydroidSendlistItemNoBinding, val itemClick: (UriRealInfoEx?, String) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemNoBinding>(binding) {
    private val targetAlpha = 0.55f

    init {
        binding.root.onClick {
            itemClick(currentData as UriRealInfoEx?, "")
        }
    }

    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is UriRealInfoEx) return
        val goodName = bean.goodName()
        binding.icon.setImageResource(getIcon(bean.goodName()))
        binding.fileNameTv.text = goodName ?: bean.uri.toString()
        binding.fileSizeAndMD5Tv.text = bean.fileSizeStr

        binding.root.alpha = if (bean.isChecked) 1f else targetAlpha
    }
}