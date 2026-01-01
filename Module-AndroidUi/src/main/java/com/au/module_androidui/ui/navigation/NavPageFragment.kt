package com.au.module_androidui.ui.navigation

import androidx.viewbinding.ViewBinding
import com.au.module_androidui.ui.FragmentNavigationActivity
import com.au.module_androidui.ui.bindings.BindingFragment

/**
 * 如果是 startPage，不论如何都先显示他；再navigateTo 下个页面，以便还原。如果某个界面
 */
abstract class NavPageFragment<VB: ViewBinding> : BindingFragment<VB>(), INavigationPage {

    internal lateinit var page: FragmentNavigationPage

    override lateinit var viewModel : FragmentNavigationViewModel

    override val customBackAction: (() -> Boolean)
        get() = {
            (requireActivity() as FragmentNavigationActivity).navigateBack()
            false //这里永远拦截，交给 Activity 处理
        }
}