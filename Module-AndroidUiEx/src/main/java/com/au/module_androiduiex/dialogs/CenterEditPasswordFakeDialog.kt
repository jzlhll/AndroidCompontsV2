package com.au.module_androiduiex.dialogs

import android.text.method.PasswordTransformationMethod
import androidx.activity.ComponentActivity
import com.au.module_android.click.onClick
import com.au.module_android.utils.gone
import com.au.module_android.utils.showImeNew
import com.au.module_android.utils.visible
import com.au.module_androidui.dialogs.AbsCenterFakeDialog
import com.au.module_androiduiex.R
import com.au.module_androiduiex.databinding.CenterEditInputPasswordBinding

class CenterEditPasswordFakeDialog : AbsCenterFakeDialog<CenterEditInputPasswordBinding>() {
    /**
     * 设置监听
     */
    var onSure:((text:String) ->Unit) = {}

    private var mBinding: CenterEditInputPasswordBinding?=null

    private var isHide = true

    override fun onShow(activity: ComponentActivity, binding: CenterEditInputPasswordBinding, desc: String?) {
        mBinding = binding
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
            hide()
        }
        binding.sureButton.onClick {
            onSure(binding.edit.text.toString())
            hide()
        }
        binding.eye.onClick {
            isHide = !isHide
            changeEditHideMode(isHide)
            if (isHide) {
                binding.eye.setImageResource(R.drawable.ic_input_eye_hide)
            } else {
                binding.eye.setImageResource(R.drawable.ic_input_eye_open)
            }
        }

        binding.edit.requestFocus()
        showImeNew(activity.window, binding.edit)
    }

    override fun onHide(binding:CenterEditInputPasswordBinding) {
        binding.edit.clearFocus()
    }

    override fun markupShow(): Int {
        return 2
    }

    fun changeEditHideMode(isHide: Boolean) {
        mBinding?.let {
            it.edit.transformationMethod = if (isHide) PasswordTransformationMethod.getInstance() else null
            it.edit.setSelection(it.edit.text?.length ?: 0)
        }
    }
}