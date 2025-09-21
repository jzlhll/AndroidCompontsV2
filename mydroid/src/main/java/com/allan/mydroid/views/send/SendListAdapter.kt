package com.allan.mydroid.views.send

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class IconTitle(@DrawableRes val icon:Int, val title:String)

private const val VIEW_TYPE_ICON_TEXT = 1
private const val VIEW_TYPE_URI_INFO = 2

const val CLICK_MODE_ICON = "icon"
const val CLICK_MODE_DELETE = "delete"
const val CLICK_MODE_ROOT = "root"

class SendListAdapter(val click: (ShareInBean?, mode:String) -> Unit)
        : BindRcvAdapter<Any, BindViewHolder<Any, *>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<Any, *> {
        return when (viewType) {
            VIEW_TYPE_ICON_TEXT -> {
                SendTitleHolder(create(parent))
            }

            VIEW_TYPE_URI_INFO -> {
                SendHolder(create(parent), rootClick = {
                    click(it, CLICK_MODE_ROOT)
                }, iconClick = {
                    click(it, CLICK_MODE_ICON)
                }, deleteClick = {
                    click(it, CLICK_MODE_DELETE)
                })
            }

            else -> throw IllegalArgumentException("unknown type")
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = this.datas[position]
        return when (data) {
            is IconTitle -> VIEW_TYPE_ICON_TEXT
            is ShareInBean -> VIEW_TYPE_URI_INFO
            else -> throw IllegalArgumentException("unknown type")
        }
    }
}
