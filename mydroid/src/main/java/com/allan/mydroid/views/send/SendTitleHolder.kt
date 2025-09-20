package com.allan.mydroid.views.send

import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.HolderMydroidSendlistItemBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistItemNoBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistTitleBinding
import com.allan.mydroid.globals.getIcon
import com.au.module_android.click.onClick
import com.au.module_android.utils.visible
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

class SendHolder(binding: HolderMydroidSendlistItemBinding,
                 val deleteClick: (ShareInBean?, String) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemBinding>(binding) {
    init {
        binding.deleteBtn.onClick {
            deleteClick(currentData as ShareInBean?, "delete")
        }
    }

    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is ShareInBean) return
        val goodName = bean.name
        binding.icon.setImageResource(getIcon(goodName))
        binding.fileNameTv.text = goodName ?: bean.uri.toString()
        binding.fileSizeAndMD5Tv.text = bean.fileSizeStr
        binding.deleteBtn.visible()
    }
}

class SendNoBtnHolder(binding: HolderMydroidSendlistItemNoBinding, val itemClick: (ShareInBean?, String) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemNoBinding>(binding) {
    init {
        binding.root.onClick {
            itemClick(currentData as ShareInBean?, "")
        }
    }

    override fun bindData(bean: Any) {
        super.bindData(bean)
        if (bean !is ShareInBean) return
        val goodName = bean.name
        binding.icon.setImageResource(getIcon(goodName))
        binding.fileNameTv.text = goodName ?: bean.uri.toString()
        binding.fileSizeAndMD5Tv.text = bean.fileSizeStr
    }
}