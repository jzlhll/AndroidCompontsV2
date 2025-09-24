package com.allan.mydroid.views.send

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.allan.mydroid.beansinner.ShareInBean
import com.allan.mydroid.databinding.DialogMyDroidSendlistBinding
import com.au.module_android.ui.base.findDialog
import com.au.module_android.ui.bindings.BindingFragment
import com.au.module_android.utils.launchOnThread

class SendListSelectorDialog : BindingFragment<DialogMyDroidSendlistBinding>() {
    interface ISelectItemClick {
        fun onItemClick(bean: ShareInBean)
    }

    private val common = object : SendListSelectorCommon(true) {
        override fun rcv() = binding.rcv
        override fun empty() = binding.empty

        override fun onItemClick(bean: ShareInBean?, mode:String) {
            bean ?: return
            if (mode != CLICK_MODE_ROOT) return
            var parent = parentFragment as? ISelectItemClick
            if (parent == null) {
                parent = parentFragment?.parentFragment as? ISelectItemClick
            }
            if (parent == null) {
                parent = parentFragment?.parentFragment?.parentFragment as? ISelectItemClick
            }
            parent?.onItemClick(bean)
            findDialog(this@SendListSelectorDialog)?.dismissAllowingStateLoss()
        }
    }

    override fun onBindingCreated(savedInstanceState: Bundle?) {
        common.onCreated()
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launchOnThread {
            common.reload()
        }
    }
}