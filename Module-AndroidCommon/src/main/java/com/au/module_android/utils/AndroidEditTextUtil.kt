package com.au.module_android.utils

import android.text.InputFilter
import android.view.View
import android.widget.EditText


/**
 * 设置最大长度
 */
fun EditText.setMaxLength(max: Int) {
    addFilters(InputFilter.LengthFilter(max))
}

/**
 * 所有字母大写
 */
fun EditText?.allCaps() {
    addFilters(InputFilter.AllCaps())
}

/**
 * 在之前的基础上，新增一个或者多个InputFilter
 */
fun EditText?.addFilters(vararg filter: InputFilter) {
    this ?: return
    if (filter.isNullOrEmpty()) {
        return
    }
    val old = this.filters
    val new = arrayOf<InputFilter>(*old, *filter)
    this.filters = new
}


/**
 * 在onCreate过程，添加代码。可以让它支持autoFill
 * @param autoFillHints 可以选如下：
 *     View.AUTOFILL_HINT_PASSWORD
 *     View.AUTOFILL_HINT_EMAIL_ADDRESS
 *     newPassword
 */
fun EditText.makeAutoFill(autoFillHints:String) {
    setAutofillHints(autoFillHints)
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    importantForAccessibility = View.IMPORTANT_FOR_AUTOFILL_YES
}

/**
 * 在onCreate过程，添加代码。可以让它不要支持autoFill
 */
fun EditText.makeNoAutoFill() {
    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
    importantForAccessibility = View.IMPORTANT_FOR_AUTOFILL_NO
}