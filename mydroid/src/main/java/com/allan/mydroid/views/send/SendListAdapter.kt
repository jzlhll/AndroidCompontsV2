package com.allan.mydroid.views.send

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.allan.mydroid.beansinner.UriRealInfoEx
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
