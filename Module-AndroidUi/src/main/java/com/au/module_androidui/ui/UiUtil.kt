package com.au.module_androidui.ui

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.au.module_android.Globals
import com.au.module_android.Globals.activityList
import com.au.module_android.utils.currentStatusBarAndNavBarHeight
import com.au.module_androidui.ui.base.IFullWindow
import com.au.module_androidui.ui.base.ImmersiveMode
import java.lang.reflect.ParameterizedType

/**
 * 将class转为ParameterizedType，方便获取此类的类泛型
 */
private fun Class<*>?.getParameterizedType(): ParameterizedType? {
    if (this == null) {
        return null
    }
    val type = this.genericSuperclass
    return if (type == null || type !is ParameterizedType) {
        this.superclass.getParameterizedType()
    } else {
        type
    }
}

private fun <T> findViewBinding(javaClass:Class<*>, typeIndex:Int = 0) : Class<T>? {
    val parameterizedType = javaClass.getParameterizedType() ?: return null
    val actualTypeArguments = parameterizedType.actualTypeArguments
    val type = actualTypeArguments[typeIndex]
    if ((ViewBinding::class.java).isAssignableFrom(type as Class<*>)) {
        return type as Class<T>
    }
    return null
}

fun <T : ViewBinding> createViewBinding(self: Class<*>, inflater: LayoutInflater, container: ViewGroup?, attach: Boolean): T {
    var clz: Class<T>? = findViewBinding(self)
    //修正框架，允许往上寻找3层superClass的第一个泛型做为ViewBinding
    if (clz == null) {
        val superClass = self.superclass
        if (superClass != null) {
            clz = findViewBinding(superClass) ?: superClass.superclass?.let { findViewBinding(it) }
        }
    }
    if (clz == null) throw IllegalArgumentException("需要一个ViewBinding类型的泛型")
    return try {
        clz.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        ).invoke(null, inflater, container, attach) as T
    } catch (e : NoSuchMethodException) {
        createViewBindingMerged(clz, inflater, container)
    }
}

fun <T : ViewBinding> createViewBindingMerged(self: Class<*>, inflater: LayoutInflater, container: ViewGroup?): T {
    var clz: Class<T>? = findViewBinding(self)
    //修正框架，允许往上寻找3层superClass的第一个泛型做为ViewBinding
    if (clz == null) {
        val superClass = self.superclass
        if (superClass != null) {
            clz = findViewBinding(superClass) ?: superClass.superclass?.let { findViewBinding(it) }
        }
    }
    if (clz == null) throw IllegalArgumentException("需要一个ViewBinding类型的泛型")
    return clz.getMethod(
        "inflate",
        LayoutInflater::class.java,
        ViewGroup::class.java
    ).invoke(null, inflater, container) as T
}

/**
 * 暂时不继续往父类查找。
 */
fun <T : ViewBinding> createViewBindingT2(self: Class<*>, inflater: LayoutInflater, container:ViewGroup, isContentMergeXml:Boolean): T {
    val clz: Class<T> = findViewBinding(self, 1) ?: throw IllegalArgumentException("需要一个ViewBinding类型的泛型") //不再向上去找
    return if (isContentMergeXml) {
        clz.getMethod("inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java).invoke(null, inflater, container) as T
    } else {
        clz.getMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        ).invoke(null, inflater, null, false) as T
    }
}

/**
 * 如果是我们框架的代码，则可以用Fragment的来判断
 */
fun findCustomFragmentGetActivity(customFragment: Class<*>): Activity? {
    val found = Globals.activityList.find {
        it.javaClass == FragmentShellActivity::class.java && (it as FragmentShellActivity).fragmentClass == customFragment
    }
    return found
}

/**
 * 退出某个fragment承载的activity
 */
fun finishFragment(clz: Class<out Fragment>) {
    activityList.forEach {
        if (it is FragmentShellActivity && it.fragmentClass == clz) {
            it.finish()
        }
    }
}

fun IFullWindow.immersive(activity: Activity, root: View) {
    val pair = activity.currentStatusBarAndNavBarHeight()
    val statusBarsHeight = pair?.first ?: 0
    val bottomBarHeight = pair?.second ?: 0

    when (val immersiveMode = immersiveMode()) {
        is ImmersiveMode.PaddingBars -> {
            root.updatePadding(top = statusBarsHeight, bottom = bottomBarHeight)
        }
        is ImmersiveMode.PaddingStatusBar -> {
            root.updatePadding(top = statusBarsHeight)
        }
        is ImmersiveMode.PaddingNavigationBar -> {
            root.updatePadding(bottom = bottomBarHeight)
        }
        is ImmersiveMode.FullImmersive -> {
            immersiveMode.barsHeightCallback?.invoke(statusBarsHeight, bottomBarHeight)
        }
    }
}