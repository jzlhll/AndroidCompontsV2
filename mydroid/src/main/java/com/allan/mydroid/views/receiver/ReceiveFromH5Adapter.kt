package com.allan.mydroid.views.receiver

import android.view.ViewGroup
import com.allan.mydroid.beansinner.MergedFileInfo
import com.au.module_nested.recyclerview.BindRcvAdapter
import java.io.File

class ReceiveFromH5Adapter(val fullClick:(MergedFileInfo)->Unit, val click:(File)->Unit)
    : BindRcvAdapter<MergedFileInfo, ReceiveFromH5Holder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiveFromH5Holder {
        return ReceiveFromH5Holder(create(parent), fullClick, click)
    }
}