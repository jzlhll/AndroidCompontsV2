package com.allan.mydroid.views.send

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.allan.mydroid.beansinner.UriRealInfoEx
import com.allan.mydroid.databinding.HolderMydroidSendlistItemBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistItemNoBinding
import com.allan.mydroid.databinding.HolderMydroidSendlistTitleBinding
import com.allan.mydroid.globals.getIcon
import com.au.module_android.click.onClick
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class IconTitle(@DrawableRes val icon:Int, val title:String)

private const val VIEW_TYPE_ICON_TEXT = 1
private const val VIEW_TYPE_URI_INFO = 2

class SendListAdapter(val noButtons:Boolean,
                      val itemClick: (UriRealInfoEx?, mode:String) -> Unit)
        : BindRcvAdapter<Any, BindViewHolder<Any, *>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<Any, *> {
        if (viewType == VIEW_TYPE_ICON_TEXT) {
            return SendTitleHolder(create(parent))
        } else {
            if (noButtons) {
                return SendNoBtnHolder(create(parent), itemClick)
            }
            return SendHolder(create(parent), itemClick)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (this.datas[position]) {
            is IconTitle -> VIEW_TYPE_ICON_TEXT
            is UriRealInfoEx -> VIEW_TYPE_URI_INFO
            else -> throw IllegalArgumentException("unknown type")
        }
    }
}

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
    private val targetAlpha = 0.65f

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

        binding.root.alpha = if (bean.isChecked) 1f else targetAlpha
    }
}

class SendNoBtnHolder(binding: HolderMydroidSendlistItemNoBinding, val itemClick: (UriRealInfoEx?, String) -> Unit)
    : BindViewHolder<Any, HolderMydroidSendlistItemNoBinding>(binding) {
    private val targetAlpha = 0.5f

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