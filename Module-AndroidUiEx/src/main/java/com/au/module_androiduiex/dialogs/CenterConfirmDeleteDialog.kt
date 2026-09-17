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

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_android.utils.dp
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.CenterConfirmDeleteDialogBinding

/**
 * 居中删除确认弹窗。
 */
open class CenterConfirmDeleteDialog : BindingDialog<CenterConfirmDeleteDialogBinding>() {
    companion object {
        /**
         * 显示删除确认弹窗。
         */
        @JvmOverloads
        fun show(
            manager: FragmentManager,
            title: String,
            desc: String,
            deleteText: String,
            cancelText: String,
            deleteClick: Function1<CenterConfirmDeleteDialog, Unit>?,
            showFirstButton: Boolean = false,
            firstButtonText: String = "",
            firstButtonClick: Function1<CenterConfirmDeleteDialog, Unit>? = null,
        ): CenterConfirmDeleteDialog {
            val dialog = CenterConfirmDeleteDialog()
            dialog.onShownBlock = {
                dialog.binding.titleTv.text = title
                dialog.binding.descTv.text = desc
                dialog.binding.descTv.visibility = if (desc.isBlank()) View.GONE else View.VISIBLE
                dialog.binding.firstBtn.visibility = if (showFirstButton) View.VISIBLE else View.GONE
                dialog.binding.firstBtn.text = firstButtonText
                dialog.binding.deleteBtn.text = deleteText
                dialog.binding.cancelBtn.text = cancelText
                (dialog.binding.deleteBtn.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
                    params.topMargin = if (showFirstButton) 12.dp else 16.dp
                    dialog.binding.deleteBtn.layoutParams = params
                }
                dialog.binding.firstBtn.onClick {
                    firstButtonClick?.invoke(dialog)
                }
                dialog.binding.deleteBtn.onClick {
                    deleteClick?.invoke(dialog)
                }
                dialog.binding.cancelBtn.onClick {
                    dialog.dismissAllowingStateLoss()
                }
            }
            dialog.show(manager, "CenterConfirmDeleteDialog")
            return dialog
        }
    }
}
