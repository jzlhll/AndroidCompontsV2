package com.au.module_androidcolor

import android.content.res.ColorStateList
import androidx.appcompat.widget.AppCompatCheckBox

/**
 * 扩展函数： 设置CheckBox的Tint 用于修改选中和未选中的颜色
 */
fun AppCompatCheckBox.setTintAuto(checkedColor: Int, uncheckedColor: Int, disabledColor: Int = 0xFF888888.toInt()) {
    val buttonTint = ColorStateList(
        arrayOf(
            intArrayOf(-android.R.attr.state_enabled), // 禁用状态
            intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked), // 启用但未选中
            intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked) // 启用且选中
        ), intArrayOf(disabledColor, uncheckedColor, checkedColor)
    )
    this.buttonTintList = buttonTint
}