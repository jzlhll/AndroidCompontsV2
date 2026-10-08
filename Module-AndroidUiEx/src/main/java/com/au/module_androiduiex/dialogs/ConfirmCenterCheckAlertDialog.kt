/*
* Created by jiangzhonglun@imagecho.ai on 2026/06/22.
*
* Copyright (C) 2026 [imagecho.ai]. All Rights Reserved.
*
* This software is proprietary and confidential. Unauthorized use, copying,
* modification, or distribution is prohibited without prior written consent.
*
* For inquiries, contact: [contacts@imagecho.ai]
*/

package com.au.module_androiduiex.dialogs

import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_android.utils.gone
import com.au.module_android.utils.visible
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.ConfirmCenterCheckAlertDialogBinding

/**
 * 支持中部勾选项的居中确认弹窗。
 */
open class ConfirmCenterCheckAlertDialog : BindingDialog<ConfirmCenterCheckAlertDialogBinding>() {
    companion object {
        /**
         * 展示带可选勾选项的居中确认弹窗。
         */
        fun show(
            manager: FragmentManager,
            title: String?,
            content: String?,
            sureText: String?,
            cancelText: String? = null,
            checkText: String? = null,
            showCheck: Boolean = true,
            defaultChecked: Boolean = false,
            sureClick: ((ConfirmCenterCheckAlertDialog, Boolean) -> Unit)?,
        ): ConfirmCenterCheckAlertDialog {
            val dialog = ConfirmCenterCheckAlertDialog()
            dialog.onShownBlock = {
                dialog.binding.titleTv.text = title
                dialog.binding.contentTv.text = content
                sureText?.let { dialog.binding.sureButton.text = it }
                cancelText?.let { dialog.binding.cancelButton.text = it }

                if (showCheck) {
                    dialog.binding.checkHost.visible()
                } else {
                    dialog.binding.dialogHost.minimumHeight = 0
                    dialog.binding.checkHost.gone()
                }
                dialog.binding.checkImg.isChecked = defaultChecked
                dialog.binding.checkTv.text = checkText

                dialog.binding.checkHost.onClick {
                    dialog.binding.checkImg.isChecked = !dialog.binding.checkImg.isChecked
                }
                dialog.binding.cancelButton.onClick {
                    dialog.dismissAllowingStateLoss()
                }
                dialog.binding.sureButton.onClick {
                    sureClick?.invoke(dialog, dialog.binding.checkImg.isChecked)
                    dialog.dismissAllowingStateLoss()
                }
            }

            dialog.show(manager, "ConfirmCenterCheckAlertDialog")
            return dialog
        }
    }
}
