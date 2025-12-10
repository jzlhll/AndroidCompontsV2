package com.allan.androidlearning.androidui

import android.os.Bundle
import android.view.View
import com.allan.androidlearning.R
import com.allan.androidlearning.databinding.FragmentAndroidUiBinding
import com.allan.classnameanno.EntryFrgName
import com.au.module_android.ui.ToolbarMenuManager
import com.au.module_nested.viewpager2.simplePagerAdapter
import com.au.module_android.ui.bindings.BindingFragment

/**
 * @author allan
 * @date :2024/8/19 15:15
 * @description:
 */
@EntryFrgName()
class AndroidUiFragment : BindingFragment<FragmentAndroidUiBinding>() {
    private var toolbarMenuManager: ToolbarMenuManager? = null

    private val pages = listOf(
        Pair("CustomView", AndroidUi4Fragment::class.java),
        Pair("Components", AndroidUi1Fragment::class.java),
        Pair("Components2", AndroidUi3Fragment::class.java),
        Pair("Action", AndroidUi2Fragment::class.java),
    )

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        toolbarMenuManager = ToolbarMenuManager(this, binding.toolbar, menuXml = R.menu.skip_menu) {
        }

        //不经过post 有毛用 binding.viewPager.overScrollNever()
        binding.viewPager.simplePagerAdapter(this, pages) { _, pair ->
            pair.second.getDeclaredConstructor().newInstance()
        }

        binding.tabLayout.initAttachToViewPage2AsCustomFontText(binding.viewPager, pages)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbarMenuManager?.showMenu()
    }
}