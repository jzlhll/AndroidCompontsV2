package com.au.module_androiduiex.dialogs

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentManager
import com.au.module_android.click.onClick
import com.au.module_android.utils.dp
import com.au.module_android.utils.visible
import com.au.module_androidcolor.R as AndroidColorR
import com.au.module_androidui.ui.bindings.BindingDialog
import com.au.module_androidui.widget.CustomFontText
import com.au.module_androidui.widget.FontMode
import com.au.module_androiduiex.databinding.CenterGotItListDialogBinding
import kotlin.math.min

/**
 * 居中显示列表的 Got It 提醒弹窗。
 */
open class CenterGotItListDialog : BindingDialog<CenterGotItListDialogBinding>() {
    companion object {
        /**
         * 显示带列表的 Got It 提醒弹窗。
         */
        @JvmOverloads
        fun show(
            manager: FragmentManager,
            title: String,
            desc: String,
            buttonText: String,
            items: List<String> = emptyList(),
            useNeutralAction: Boolean = false,
            buttonClick: Function1<CenterGotItListDialog, Unit>? = null,
        ): CenterGotItListDialog {
            val dialog = CenterGotItListDialog()
            dialog.onShownBlock = {
                val textColor = ContextCompat.getColor(
                    dialog.requireContext(),
                    AndroidColorR.color.i8o_color_text_normal,
                )
                dialog.binding.titleTv.text = title
                dialog.binding.descTv.text = desc
                dialog.binding.gotItBtn.text = buttonText
                if (items.isNotEmpty()) {
                    dialog.binding.itemListScroll.visible()
                    dialog.binding.itemListScroll.updateLayoutParams<LinearLayout.LayoutParams> {
                        height = min(items.size, 2) * 24.dp
                    }
                    items.forEach { item ->
                        dialog.binding.itemListContainer.addView(
                            CustomFontText(dialog.requireContext()).apply {
                                text = item
                                gravity = Gravity.CENTER
                                maxLines = 1
                                ellipsize = TextUtils.TruncateAt.END
                                setTextColor(textColor)
                                textSize = 14f
                                fontMode = FontMode.MID
                                layoutParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    24.dp,
                                )
                            }
                        )
                    }
                }
                if (useNeutralAction) {
                    dialog.binding.gotItBtn.apply {
                        setTextColor(textColor)
                        fontMode = FontMode.MID
                        background = GradientDrawable().apply {
                            setColor(
                                ContextCompat.getColor(
                                    dialog.requireContext(),
                                    AndroidColorR.color.color_normal_block,
                                )
                            )
                            cornerRadius = 16.dp.toFloat()
                        }
                    }
                }
                dialog.binding.gotItBtn.onClick {
                    if (buttonClick == null) {
                        dialog.dismissAllowingStateLoss()
                    } else {
                        buttonClick.invoke(dialog)
                    }
                }
            }
            dialog.show(manager, "CenterGotItListDialog")
            return dialog
        }
    }
}
