package com.au.module_androidui.toast

import com.au.module_android.Globals
import com.au.module_androidui.R
import com.au.module_androidui.toast.MyToastUtil.topAnimHelper

class ToastTopBuilder : AbsToastBuilder<ToastTopBuilder>() {
    override var mDuration:Long = 5000

    var mBtnTextStrId:Int? = null //button text
    var mBtnColorResId:Int? = null //button color
    var mBtnShow = false //是否显示按钮

    var mReplaceCloseIconResId:Int? = null //replace close icon res id

    var mReplaceCloseIconIsClose:Boolean = true //replace close icon is close toast

    fun setReplaceCloseIconResId(resId:Int) : ToastTopBuilder {
        mReplaceCloseIconResId = resId
        return this
    }

    fun setReplaceCloseIconIsClose(isClose:Boolean) : ToastTopBuilder {
        mReplaceCloseIconIsClose = isClose
        return this
    }

    fun setBtnTextStrId(strId:Int) : ToastTopBuilder {
        mBtnTextStrId = strId
        return this
    }

    fun setBtnColorResId(resId:Int) : ToastTopBuilder {
        mBtnColorResId = resId
        return this
    }

    fun setBtnShow(show:Boolean) : ToastTopBuilder {
        mBtnShow = show
        return this
    }


    override fun toast() {
        val run = Runnable {
            val decorView = decorViewProvider?.invoke() ?: return@Runnable
            topAnimHelper.toast(decorView, mDuration,
                mSpanned,
                mBtnShow,
                mBtnTextStrId ?: R.string.retry,
                mBtnColorResId ?: R.color.my_color_orange,
                mReplaceCloseIconIsClose,
                mReplaceCloseIconResId,
                mAlwaysShown)?.root}

        val laterTs = mLaterTs
        val delayTs = if (laterTs > 0L) laterTs else 25L
        Globals.mainHandler.postDelayed(run, delayTs)
    }
}
