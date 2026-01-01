package com.au.module_androidui.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import com.au.module_androidui.ui.base.AbsFragment
import com.au.module_androidui.ui.base.IUi

/**
 * @author Allan
 * Date: 2023/7/10
 * Description 基础Fragment的通用
 */
abstract class ViewFragment : AbsFragment(), IUi {
    lateinit var root: View

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return onUiCreateView(layoutInflater, null, savedInstanceState).also { root = it }
    }
}