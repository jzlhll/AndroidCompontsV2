package com.au.module_androidui.toast

import android.text.SpannableString
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.au.module_android.utils.HtmlPart
import com.au.module_android.utils.toSpanned

object MyToastUtil {
    internal val topAnimHelper = ToastAnimTopHelper()
    internal val bottomAnimHelper = ToastAnimBottomHelper()

    fun toastOnTop(message:String) {
        if (message.isEmpty()) {
            return
        }

        ToastTopBuilder().setOnTop()
            .setSpanned(SpannableString(message))
            .toast()
    }

    fun toastOnTopLater(message:String, laterTs: Long= 1500) {
        if (message.isEmpty()) {
            return
        }

        ToastTopBuilder().setOnTopLater(laterTs)
            .setSpanned(SpannableString(message))
            .toast()
    }

    /**
     * 在底部展示普通提示。
     */
    fun toastOnBottom(message: String, iconResId: Int? = null, viewGroup: ViewGroup? = null) {
        if (message.isEmpty()) {
            return
        }

        val builder = ToastBottomBuilder().setSpanned(SpannableString(message))
        if (viewGroup == null) builder.setOnTop() else builder.setOnViewGroup(viewGroup)
        if (iconResId != null) builder.setIconResId(iconResId)
        builder.toast()
    }

    /**
     * 在 Dialog 窗口内底部展示普通提示，避免被全屏 Dialog 遮挡。
     */
    fun toastOnDialogBottom(contentFragment: Fragment, message: String) {
        if (message.isEmpty()) {
            return
        }

        ToastBottomBuilder()
            .setOnFragmentDialog(contentFragment)
            .setSpanned(SpannableString(message))
            .toast()
    }

    /**
     * 延迟在底部展示普通提示。
     */
    fun toastOnBottomLater(message:String, laterTs: Long= 1500, queued: Boolean = false) {
        if (message.isEmpty()) {
            return
        }

        ToastBottomBuilder().setOnTopLater(laterTs)
            .setSpanned(SpannableString(message))
            .setQueued(queued)
            .toast()
    }

    /**
     * 延迟在底部展示带图标的提示。
     */
    fun toastOnBottomLater(
        message: String,
        iconResId: Int,
        duration: Long = 2000,
        laterTs: Long = 1500,
        queued: Boolean = false,
    ) {
        if (message.isEmpty()) {
            return
        }

        ToastBottomBuilder().setOnTopLater(laterTs)
            .setSpanned(SpannableString(message))
            .setIconResId(iconResId)
            .setDuration(duration)
            .setQueued(queued)
            .toast()
    }

    fun toastOnTopError(message: String, viewGroup: ViewGroup? = null) {
        if (message.isEmpty()) {
            return
        }

        val htmlPart = HtmlPart(message, "#fc1818")
        val builder = ToastTopBuilder().setSpanned(toSpanned(htmlPart))
        if (viewGroup == null) builder.setOnTop() else builder.setOnViewGroup(viewGroup)
        builder.toast()
    }

    fun toastOnTopErrorLater(message:String, laterTs: Long= 1500) {
        if (message.isEmpty()) {
            return
        }

        val htmlPart = HtmlPart(message, "#fc1818")
        ToastTopBuilder().setOnTopLater(laterTs)
            .setSpanned(toSpanned(htmlPart))
            .toast()
    }

}
