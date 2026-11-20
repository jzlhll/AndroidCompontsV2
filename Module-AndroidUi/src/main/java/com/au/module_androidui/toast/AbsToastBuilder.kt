package com.au.module_androidui.toast

import android.app.Activity
import android.text.Spanned
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.au.module_android.Globals
import com.au.module_android.utils.asOrNull
import com.au.module_androidui.ui.FragmentShellActivity
import com.au.module_androidui.ui.base.IBaseDialog
import com.au.module_androidui.ui.base.findDialog

abstract class AbsToastBuilder<T>{
    // 在主线程展示时解析承载目标，避免调用线程提前访问窗口或 Fragment。
    protected var decorViewProvider: (() -> ViewGroup?)? = null

    var mSpanned: Spanned? = null //message

    open var mDuration:Long = 2000 //时长
    var mAlwaysShown = false //一直显示

    var mLaterTs = 50L

    /**
     * 其一：从Activity中调用
     */
    fun setOnActivity(activity: Activity?) : T {
        decorViewProvider = { activity?.window?.decorView.asOrNull() }
        return this as T
    }
    /**
     * 其一：从Fragment中调用
     */
    fun setOnFragment(fragment: Fragment) : T {
        decorViewProvider = { fragment.activity?.window?.decorView.asOrNull() }
        return this as T
    }

    fun setOnFragmentDialog(contentFragment: Fragment) : T {
        decorViewProvider = {
            findDialog(contentFragment).asOrNull<IBaseDialog>()?.findToastViewGroup()
        }
        return this as T
    }

    fun setOnViewGroup(viewGroup: ViewGroup?) : T {
        decorViewProvider = { viewGroup }
        return this as T
    }

    /**
     * 其一：从最顶中调用
     */
    fun setOnTop() : T {
        decorViewProvider = { Globals.activityList.lastOrNull()?.window?.decorView.asOrNull() }
        return this as T
    }

    /**
     * 其一：delay执行
     */
    fun setOnTopLater(laterTs:Long = 1500) : T {
        mLaterTs = laterTs
        return setOnTop()
    }

    fun setOnActivityByFragClass(clz : Class<*>): T {
        decorViewProvider = {
            Globals.activityList.firstOrNull {
                it is FragmentShellActivity && it.fragmentClass == clz
            }?.window?.decorView.asOrNull()
        }
        return this as T
    }

    fun setSpanned(spanned: Spanned) : T {
        mSpanned = spanned
        return this as T
    }

    fun setDuration(duration: Long) : T {
        mDuration = duration
        return this as T
    }

    fun setAlwaysShown(shown:Boolean) : T {
        mAlwaysShown = shown
        return this as T
    }

    abstract fun toast()
}
