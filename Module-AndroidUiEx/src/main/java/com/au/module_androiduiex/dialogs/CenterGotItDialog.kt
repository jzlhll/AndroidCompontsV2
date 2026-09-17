/*
* Created by jiangzhonglun@imagecho.ai on 2026/06/23.
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
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.CenterGotItDialogBinding

/**
 * 居中 Got It 提醒弹窗。
 */
open class CenterGotItDialog : BindingDialog<CenterGotItDialogBinding>() {
    companion object {
        /**
         * 显示 Got It 提醒弹窗。
         */
        @JvmOverloads
        fun show(
            manager: FragmentManager,
            title: String,
            desc: String,
            buttonText: String,
            buttonClick: Function1<CenterGotItDialog, Unit>? = null,
        ): CenterGotItDialog {
            val dialog = CenterGotItDialog()
            dialog.onShownBlock = {
                dialog.binding.titleTv.text = title
                dialog.binding.descTv.text = desc
                dialog.binding.gotItBtn.text = buttonText
                dialog.binding.gotItBtn.onClick {
                    if (buttonClick == null) {
                        dialog.dismissAllowingStateLoss()
                    } else {
                        buttonClick.invoke(dialog)
                    }
                }
            }
            dialog.show(manager, "CenterGotItDialog")
            return dialog
        }
    }
}
