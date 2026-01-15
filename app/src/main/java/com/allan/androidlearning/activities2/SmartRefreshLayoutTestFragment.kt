package com.allan.androidlearning.activities2

import android.content.Context
import android.view.View
import androidx.viewbinding.ViewBinding
import com.au.module_androidui.selectlist.SimpleItem
import com.au.module_androidui.selectlist.SimpleListFragment
import com.au.module_androidui.selectlist.SimpleSelectListFragment


class SmartRefreshLayoutTestFragment(
) : SimpleListFragment<SmartRefreshLayoutTestFragment.Item>() {
    class Item(override val itemName: String, override val onItemClick: () -> Unit) : SimpleItem()

    private val _items = listOf<Item>(
        Item("常规可以拉动主界面并显示刷新按钮") {
        },
        Item("常规可以拉动主界面并显示刷新布局") {
        },
        Item("google界面不动有个运动刷新按钮") {

        },

        Item("titleBar变更图片并刷新(用于个人主页)") {

        },
        )

    override val title: String = "SmartRefreshLayout"
    override val items: List<Item> = _items
    override fun createItemView(
        context: Context,
        value: Item
    ): ViewBinding {
        TODO()
    }

    override fun bindItemView(vb: ViewBinding, item: Item) {
    }

}