package com.allan.androidlearning.refresh

import android.content.Context
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding
import com.allan.androidlearning.databinding.ItemSelectViewBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.click.onClick
import com.au.module_androidui.selectlist.SimpleItem
import com.au.module_androidui.selectlist.SimpleListFragment
import com.au.module_androidui.ui.FragmentShellActivity

@EntryFrgName
class SmartRefreshLayoutTestFragment(
) : SimpleListFragment<SmartRefreshLayoutTestFragment.Item>() {
    class Item(override val itemName: String, override val onItemClick: () -> Unit) : SimpleItem()

    private val _items = listOf<Item>(
        Item("常规可以拉动主界面并显示刷新按钮") {
            FragmentShellActivity.start(requireActivity(), RefreshRcvFragment::class.java)
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
        return ItemSelectViewBinding.inflate(LayoutInflater.from(context))
    }

    override fun bindItemView(vb: ViewBinding, item: Item) {
        val binding = vb as ItemSelectViewBinding
        binding.btn.text = item.itemName
        binding.btn.onClick {
            item.onItemClick.invoke()
        }
    }

}