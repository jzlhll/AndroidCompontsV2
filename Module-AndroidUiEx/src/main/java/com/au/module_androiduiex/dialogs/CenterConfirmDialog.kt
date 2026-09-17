package com.au.module_androiduiex.dialogs

import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_androidcolor.R
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.CenterConfirmDialogBinding

/**
 * 居中确认弹窗。
 */
open class CenterConfirmDialog : BindingDialog<CenterConfirmDialogBinding>() {
    companion object {
        /**
         * 显示居中确认弹窗。
         */
        fun show(
            manager: FragmentManager,
            title: String,
            desc: String,
            confirmText: String,
            cancelText: String? = null,
            confirmTextRed: Boolean = false,
            confirmButtonOnLeft: Boolean = false,
            confirmClick: (CenterConfirmDialog) -> Unit,
        ): CenterConfirmDialog {
            val dialog = CenterConfirmDialog()
            dialog.onShownBlock = {
                dialog.binding.titleTv.text = title
                dialog.binding.descTv.text = desc
                val confirmBtn = if (confirmButtonOnLeft) {
                    dialog.binding.cancelBtn
                } else {
                    dialog.binding.confirmBtn
                }
                val cancelBtn = if (confirmButtonOnLeft) {
                    dialog.binding.confirmBtn
                } else {
                    dialog.binding.cancelBtn
                }
                confirmBtn.text = confirmText
                confirmBtn.setTextColor(
                    ContextCompat.getColor(
                        dialog.requireContext(),
                        if (confirmTextRed) R.color.i8o_color_red else R.color.color_text_normal,
                    ),
                )
                if (cancelText == null) {
                    cancelBtn.setText(com.au.module_androidui.R.string.cancel)
                } else {
                    cancelBtn.text = cancelText
                }
                confirmBtn.onClick { confirmClick(dialog) }
                cancelBtn.onClick { dialog.dismissAllowingStateLoss() }
            }
            dialog.show(manager, "CenterConfirmDialog")
            return dialog
        }
    }
}
