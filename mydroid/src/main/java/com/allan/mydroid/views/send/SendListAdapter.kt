package com.allan.mydroid.views.send

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class IconTitle(@DrawableRes val icon:Int, val title:String)

private const val VIEW_TYPE_ICON_TEXT = 1
private const val VIEW_TYPE_URI_INFO = 2
private const val VIEW_TYPE_URI_INFO_NO_BTN = 3

class SendListAdapter(val hasDeleteBtn: Boolean,
                      val click: (ShareInBean?, mode:String) -> Unit)
        : BindRcvAdapter<Any, BindViewHolder<Any, *>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<Any, *> {
        return when (viewType) {
            VIEW_TYPE_ICON_TEXT -> {
                SendTitleHolder(create(parent))
            }

            VIEW_TYPE_URI_INFO -> {
                SendHolder(create(parent), click)
            }

            VIEW_TYPE_URI_INFO_NO_BTN -> {
                SendNoBtnHolder(create(parent), click)
            }

            else -> throw IllegalArgumentException("unknown type")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = this.datas[position]
        return when (data) {
            is IconTitle -> VIEW_TYPE_ICON_TEXT
            is ShareInBean -> if(hasDeleteBtn && !data.isLocalReceiver) VIEW_TYPE_URI_INFO else VIEW_TYPE_URI_INFO_NO_BTN
            else -> throw IllegalArgumentException("unknown type")
        }
    }
}
