package com.au.module_android.ui.navigation

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.EmptySuper
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.au.module_android.ui.createViewBinding
import com.au.module_android.ui.views.ViewToolbarFragment

/**
 * 如果是 startPage，不论如何都先显示他；再navigateTo 下个页面，以便还原。如果某个界面
 */
abstract class NavPageFragment<VB: ViewBinding> : ViewToolbarFragment(), INavigationPage {
    lateinit var binding:VB private set

    internal lateinit var page: FragmentNavigationPage

    override lateinit var viewModel : FragmentNavigationViewModel

    override fun isStartPage() = page.isStartPage

    override fun pageId(): String {
        return page.pageId
    }

    override fun loadAndObserverData() {
        TODO("Not yet implemented")
    }

    override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(requireActivity())[FragmentNavigationViewModel::class.java]

        if (isStartPage()) {

        }

        val vb = createViewBinding(javaClass, inflater, container, false) as VB
        binding = vb
        onBindingCreated(savedInstanceState)
        return vb.root
    }

    @EmptySuper
    open fun onBindingCreated(savedInstanceState: Bundle?) {}
}