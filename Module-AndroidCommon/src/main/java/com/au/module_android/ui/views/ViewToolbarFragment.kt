package com.au.module_android.ui.views

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.annotation.CallSuper
import com.au.module_android.ui.base.AbsFragment
import com.au.module_android.ui.base.IUi
import com.au.module_android.widget.YourToolbar

/**
 * @author au
 * Date: 2023/7/10
 * Description 基础Fragment的通用
 */
abstract class ViewToolbarFragment : AbsFragment(), IUi, IHasToolbar {
    lateinit var root: View

    private var _realRoot: RelativeLayout? = null
    private var _toolbar: YourToolbar? = null

    final override val realRoot: RelativeLayout?
        get() = _realRoot

    final override val toolbar: YourToolbar?
        get() = _toolbar

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val v = onUiCreateView(layoutInflater, null, savedInstanceState)
        root = v

        when (val info = toolbarInfo()) {
            is YourToolbarInfo -> {
                val vb = createToolbarLayout(layoutInflater.context, v)
                _realRoot = vb.root
                _toolbar = vb.toolbar

                if (info is YourToolbarInfo.Defaults) {
                    vb.toolbar.initAsNormal(info.title)
                } else if (info is YourToolbarInfo.Yours<*>) {
                    vb.toolbar.initAsYourBinding(info.layoutId, info.viewBindingInitializer)
                }
                return vb.root
            }

            else -> {
                return v
            }
        }
    }
}