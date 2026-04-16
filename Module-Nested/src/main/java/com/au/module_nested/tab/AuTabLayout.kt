package com.au.module_nested.tab

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.au.module_android.utils.asOrNull
import com.au.module_androidcolor.R
import com.au.module_androidui.fontutil.setFontFromAsset
import com.au.module_androidui.widget.CustomFontText
import com.au.module_androidui.widget.FontMode
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * @author au
 * @date :2023/11/9 13:57
 * @description:
 */
class AuTabLayout : TabLayout {
    data class TabStyle(
        @ColorRes val textColor: Int,
        @DrawableRes val backgroundRes: Int = 0,
        val textSize: Float? = null,
        val fontMode: FontMode? = null
    )

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    private val notSelectTabStyle = TabStyle(
        textColor = R.color.color_tab_text_no_select,
        textSize = 16f,
        fontMode = FontMode.NORMAL
    )

    private val selectTabStyle = TabStyle(
        textColor = R.color.color_tab_text_select,
        textSize = 16f,
        fontMode = FontMode.MID
    )

    var notSelectTabStyleBlock: (position: Int) -> TabStyle = {
        notSelectTabStyle
    }

    var selectTabStyleBlock: (position: Int) -> TabStyle = {
        selectTabStyle
    }

    private var hasInitSelectedListener = false

//    inline fun attachViewPager2(
//        viewPage2: ViewPager2,
//        autoRefresh: Boolean = true,
//        crossinline tabBlock: ((tab: Tab, position: Int) -> Unit) = { _, _ -> }
//    ) {
//        TabLayoutMediator(this, viewPage2, autoRefresh) { tab: Tab, position: Int ->
//            tabBlock.invoke(tab, position)
//        }.attach()
//    }

    /**
     * 使用CustomFontText来作为customview设置给TabLayout。
     * 由于默认情况，TabLayout的文字都是material效果。故而采用customView来解决文案的颜色和字体。简单一点。
     */
    fun initAttachToViewPage2AsCustomFontText(viewPage2: ViewPager2, pages:List<Pair<String, Class<out Fragment>>>) {
        TabLayoutMediator(this, viewPage2, true) { tab: Tab, position: Int ->
            tab.customView = createFont(viewPage2.context, pages[position].first)
            applyTabStyle(tab, position, viewPage2.currentItem == position)
        }.attach()

        initSelectedListener()
    }

    private fun initSelectedListener() {
        if (hasInitSelectedListener) return
        hasInitSelectedListener = true
        addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabSelected(tab: Tab?) {
                applyTabStyle(tab, tab?.position ?: return, true)
            }

            override fun onTabUnselected(tab: Tab?) {
                applyTabStyle(tab, tab?.position ?: return, false)
            }

            override fun onTabReselected(tab: Tab?) {
            }
        })
    }

    /**
     * 当外部动态修改样式配置后，可调用此方法刷新全部Tab样式。
     */
    fun refreshTabStyles() {
        for (index in 0 until tabCount) {
            getTabAt(index)?.let {
                applyTabStyle(it, index, it.isSelected)
            }
        }
    }

    fun newTextTab(text:String, initSelect:Boolean) : Tab {
        val tab = super.newTab()
        tab.customView = createFont(context, text)
        applyTabStyle(tab, tab.position, initSelect)
        return tab
    }

    // 统一创建使用自定义字体的 Tab 文案视图。
    private fun createFont(context: Context, text: String): CustomFontText {
        return CustomFontText(context).apply {
            gravity = Gravity.CENTER
            this.text = text
        }
    }

    // 统一处理文字和背景，使用按位置计算的选中/未选中样式。
    private fun applyTabStyle(tab: Tab?, position: Int, isSelected: Boolean) {
        val view = tab?.customView.asOrNull<TextView>() ?: return
        val style = if (isSelected) {
            selectTabStyleBlock(position)
        } else {
            notSelectTabStyleBlock(position)
        }
        view.setTextColor(ContextCompat.getColor(view.context, style.textColor))
        style.textSize?.let {
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, it)
        }
        style.fontMode?.let {
            if (view is CustomFontText) {
                view.fontMode = it
            } else {
                view.setFontFromAsset(view.context, it, false, "")
            }
        }
        if (style.backgroundRes != 0) {
            view.setBackgroundResource(style.backgroundRes)
        } else {
            view.background = null
        }
    }
}