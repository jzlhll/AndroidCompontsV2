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

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_android.utils.dp
import com.au.module_android.utils.hideImeNew
import com.au.module_android.utils.setMaxLength
import com.au.module_android.utils.showImeNew
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androiduiex.databinding.NameEditDialogBinding
import kotlin.math.min

/**
 * 编辑名称的居中弹窗。
 */
open class NameEditDialog : BindingDialog<NameEditDialogBinding>() {
    companion object {
        /**
         * 显示名称编辑弹窗。
         */
        fun show(
            manager: FragmentManager,
            name: String?,
            title: String,
            doneText: String,
            maxLength: Int = 30,
            cancelText: String? = null,
            cancelBlock: Function1<NameEditDialog, Unit>? = null,
            multiLine: Boolean = false,
            doneClick: Function2<NameEditDialog, String, Unit>?,
        ): NameEditDialog {
            val dialog = NameEditDialog()
            dialog.onShownBlock = {
                val input = name.orEmpty()
                val realInput = if (maxLength > 0 && input.length > maxLength) {
                    input.substring(0, min(input.length, maxLength))
                } else {
                    input
                }
                dialog.binding.titleTv.text = title
                if (cancelText != null) {
                    dialog.binding.cancelButton.text = cancelText
                }
                dialog.binding.doneButton.text = doneText
                dialog.applyInputMode(multiLine)
                if (maxLength > 0) {
                    dialog.binding.nameEdit.setMaxLength(maxLength)
                }
                dialog.binding.nameEdit.setText(realInput)
                dialog.binding.nameEdit.setSelection(realInput.length)
                dialog.updateCountText(maxLength)
                dialog.binding.nameEdit.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    }

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        dialog.updateCountText(maxLength)
                    }

                    override fun afterTextChanged(s: Editable?) {
                    }
                })
                dialog.binding.cancelButton.onClick {
                    dialog.dismissAllowingStateLoss()
                    cancelBlock?.invoke(dialog)
                }
                dialog.binding.doneButton.onClick {
                    doneClick?.invoke(dialog, dialog.binding.nameEdit.text?.toString().orEmpty())
                    dialog.dismissAllowingStateLoss()
                }
                if (!multiLine) {
                    dialog.binding.nameEdit.setOnEditorActionListener { _, actionId, _ ->
                        if (actionId == EditorInfo.IME_ACTION_DONE) {
                            doneClick?.invoke(dialog, dialog.binding.nameEdit.text?.toString().orEmpty())
                            dialog.dismissAllowingStateLoss()
                            true
                        } else {
                            false
                        }
                    }
                }
                dialog.binding.nameEdit.postDelayed({
                    dialog.window?.let { showImeNew(it, dialog.binding.nameEdit) }
                }, 100)
            }
            dialog.onDismissBlock = {
                dialog.window?.let { hideImeNew(it, dialog.binding.nameEdit) }
            }
            dialog.show(manager, "NameEditDialog")
            return dialog
        }
    }

    private fun applyInputMode(multiLine: Boolean) {
        if (!multiLine) return
        binding.inputHost.orientation = android.widget.LinearLayout.VERTICAL
        binding.inputHost.gravity = Gravity.TOP
        binding.inputHost.setPadding(16.dp, 10.dp, 16.dp, 8.dp)
        binding.inputHost.layoutParams = binding.inputHost.layoutParams.apply {
            height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        }
        binding.nameEdit.apply {
            setSingleLine(false)
            minLines = 1
            maxHeight = 110.dp
            gravity = Gravity.START or Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_NONE
            isVerticalScrollBarEnabled = true
            setHorizontallyScrolling(false)
            layoutParams = (layoutParams as android.widget.LinearLayout.LayoutParams).apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                weight = 0f
            }
        }
        binding.countTv.layoutParams = (binding.countTv.layoutParams as android.widget.LinearLayout.LayoutParams).apply {
            topMargin = 6.dp
            marginStart = 0
            gravity = Gravity.END
        }
    }

    private fun updateCountText(maxLength: Int) {
        val length = binding.nameEdit.text?.length ?: 0
        binding.countTv.text = if (maxLength > 0) {
            "$length/$maxLength"
        } else {
            length.toString()
        }
    }
}
