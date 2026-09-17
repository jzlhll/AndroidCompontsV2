package com.au.module_androiduiex.dialogs

import androidx.activity.ComponentActivity
import com.au.module_android.click.onClick
import com.au.module_android.utils.findWindow
import com.au.module_android.utils.gone
import com.au.module_android.utils.hideImeNew
import com.au.module_android.utils.showImeNew
import com.au.module_android.utils.visible
import com.au.module_androidui.dialogs.AbsCenterFakeDialog
import com.au.module_androiduiex.databinding.CenterEditInputBinding

class CenterEditFakeDialog : AbsCenterFakeDialog<CenterEditInputBinding>() {
    /**
     * 设置监听
     */
    var onSure:((text:String) ->Unit) = {}
    var onCancel:()->Unit = {}

    override fun onShow(activity: ComponentActivity, binding: CenterEditInputBinding, desc: String?) {
        if (!desc.isNullOrEmpty()) {
            binding.desc.text = desc
            binding.desc.visible()
            binding.descEmpty.gone()
        } else {
            binding.desc.gone()
            binding.descEmpty.visible()
        }

        binding.edit.setText("")
        binding.cancelButton.onClick {
            onCancel()
            hide()
        }
        binding.sureButton.onClick {
            onSure(binding.edit.text.toString())
            hide()
        }

        binding.edit.requestFocus()
        showImeNew(activity.window, binding.edit)
    }

    override fun onHide(binding:CenterEditInputBinding) {
        binding.edit.clearFocus()
        binding.edit.findWindow()?.let {
            hideImeNew(it, binding.edit)
        }
    }

    override fun markupShow(): Int {
        return 2
    }

}