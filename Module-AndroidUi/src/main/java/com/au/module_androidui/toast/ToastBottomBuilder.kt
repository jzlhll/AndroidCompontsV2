package com.au.module_androidui.toast

import android.os.SystemClock
import com.au.module_androidui.toast.MyToastUtil.bottomAnimHelper

class ToastBottomBuilder : AbsToastBuilder<ToastBottomBuilder>() {
    override var mDuration:Long = 2000

    var mIconResId: Int? = null // icon res id
    var mIconOnRight = false
    private var queued = false
    private var actionText: String? = null
    private var onAction: (() -> Unit)? = null

    /** 设置提示条右侧的一次性操作。 */
    fun setAction(text: String, onClick: () -> Unit): ToastBottomBuilder {
        actionText = text
        onAction = onClick
        return this
    }

    /**
     * 按调用顺序排队展示，延迟和展示期间后续底部提示均需等待。
     */
    fun setQueued(queued: Boolean = true): ToastBottomBuilder {
        this.queued = queued
        return this
    }

    fun setIconResId(resId: Int): ToastBottomBuilder {
        mIconResId = resId
        return this
    }

    fun setIconOnRight(): ToastBottomBuilder {
        mIconOnRight = true
        return this
    }

    override fun toast() {
        val laterTs = mLaterTs
        val delayTs = if (laterTs > 0L) laterTs else 50L
        ToastBottomMainHandler.enqueue(
            readyAt = SystemClock.uptimeMillis() + delayTs,
            queued = queued,
        ) {
            decorViewProvider?.invoke()?.let { decorView ->
                bottomAnimHelper.toast(
                    decorView,
                    mDuration,
                    mSpanned,
                    mIconResId,
                    mIconOnRight,
                    mAlwaysShown,
                    actionText,
                    onAction,
                )?.root
            }
        }
    }
}
