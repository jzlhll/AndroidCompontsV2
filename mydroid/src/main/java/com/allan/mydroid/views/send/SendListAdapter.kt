package com.allan.mydroid.views.send

import android.view.ViewGroup
import androidx.annotation.DrawableRes
import com.allan.mydroid.beansinner.ShareInBean
import com.au.module_nested.recyclerview.BindRcvAdapter
import com.au.module_nested.recyclerview.IMultiViewTypeBean
import com.au.module_nested.recyclerview.viewholder.BindViewHolder

class IconTitle(@DrawableRes val icon:Int, val title:String, override val viewType: Int = VIEW_TYPE_ICON_TEXT) : IMultiViewTypeBean

private const val VIEW_TYPE_ICON_TEXT = 1
const val VIEW_TYPE_URI_INFO = 2

const val CLICK_MODE_ICON = "icon"
const val CLICK_MODE_DELETE = "delete"
const val CLICK_MODE_ROOT = "root"

class SendListAdapter(val click: (ShareInBean?, mode:String) -> Unit)
        : BindRcvAdapter<IMultiViewTypeBean, BindViewHolder<IMultiViewTypeBean, *>>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindViewHolder<IMultiViewTypeBean, *> {
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
}
