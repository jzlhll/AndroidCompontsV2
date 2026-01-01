package com.au.module_androidui.ui.bindings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.EmptySuper
import androidx.viewbinding.ViewBinding
import com.au.module_androidui.ui.createViewBinding
import com.au.module_androidui.ui.views.ViewToolbarFragment

/**
 * @author Allan
 * Date: 2023/7/10
 * Description 基础Fragment的通用
 */
abstract class BindingFragment<VB: ViewBinding> : ViewToolbarFragment() {
    lateinit var binding:VB private set

    final override fun onUiCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val vb = createViewBinding(javaClass, inflater, container, false) as VB
        binding = vb
        return vb.root
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = super.onCreateView(inflater, container, savedInstanceState)
        onBindingCreated(savedInstanceState)
        return v
    }

    @EmptySuper
    open fun onBindingCreated(savedInstanceState: Bundle?) {}
}