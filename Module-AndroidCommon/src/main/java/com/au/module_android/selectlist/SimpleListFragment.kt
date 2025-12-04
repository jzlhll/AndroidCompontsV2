package com.au.module_android.selectlist

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.viewbinding.ViewBinding
import com.au.module_android.ui.FragmentShellActivity
import com.au.module_android.ui.views.ToolbarInfo
import com.au.module_android.ui.views.ViewToolbarFragment

/**
 * 选择一项。
 */
abstract class SimpleListFragment<B: SimpleItem> : ViewToolbarFragment() {
    companion object {
        fun start(context:Context, fragment: Class<SimpleListFragment<*>>) {
            FragmentShellActivity.start(context, fragment)
        }
    }

    private lateinit var host:LinearLayout

    /**
     * 标题
     */
    abstract val title:String

    /**
     * 实现：写上所有显示的items
     */
    abstract val items:List<B>

    /**
     * 每一行的高度
     */
    open fun itemHeight():Int = LinearLayout.LayoutParams.WRAP_CONTENT

    /**
     * 每一行的top边距
     */
    open fun itemTopMargin():Int = 0

    override fun toolbarInfo(): ToolbarInfo? {
        return ToolbarInfo()
    }

    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        setTitle(title)

        return NestedScrollView(inflater.context).also { scrollView->
            scrollView.isFillViewport = true
            root = scrollView

            val ll = LinearLayout(inflater.context).also {
                host = it
                it.orientation = LinearLayout.VERTICAL
            }
            scrollView.addView(ll, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT))

            val itemTopMargin = itemTopMargin()

            val context = inflater.context

            val items = this.items
            for (item in items) {
                val v = createItem(context, item)
                host.addView(
                    v,
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, itemHeight()).also {
                        it.topMargin = itemTopMargin
                    }
                )
            }
        }
    }

    private fun createItem(context: Context, value: B): View {
        val vb = createItemView(context, value)
        vb.root.tag = value
        bindItemView(vb, value)
        return vb.root
    }

    abstract fun createItemView(context: Context, value: B) : ViewBinding
    abstract fun bindItemView(vb: ViewBinding, item:B)
}