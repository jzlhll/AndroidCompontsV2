package com.au.module_androiduiex.dialogs

import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.ConfirmCenterAlertDialogRBinding

open class ConfirmCenterAlertRDialog : BindingDialog<ConfirmCenterAlertDialogRBinding>() {
    companion object {
        fun show(
            manager: FragmentManager,
            title: String?,
            content: String?,
            sureText: String?,
            cancelText:String? = null,
            sureClick: Function1<ConfirmCenterAlertRDialog, Unit>?,
        ): ConfirmCenterAlertRDialog {
            val dialog = ConfirmCenterAlertRDialog()
            dialog.onShownBlock = {
                dialog.binding.sureButton.text = sureText
                if (cancelText != null) {
                    dialog.binding.cancelButton.text = cancelText
                }

                dialog.binding.sureButton.onClick {
                    sureClick?.invoke(dialog)
                }

                dialog.binding.titleTv.text = title
                dialog.binding.contentTv.text = content
                dialog.binding.cancelButton.onClick {
                    dialog.dismissAllowingStateLoss()
                }
            }

            dialog.show(manager, "ConfirmCenterAlertDialog")
            return dialog
        }
    }
}
